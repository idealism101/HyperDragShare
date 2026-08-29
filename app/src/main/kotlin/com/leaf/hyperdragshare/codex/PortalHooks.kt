package com.leaf.hyperdragshare.codex

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Installs the Taplus hooks with libxposed API 102. The interceptor model has no before/after
 * callbacks: everything a hook did before the host method now runs before [Chain.proceed], and
 * suppressing the host call means returning without proceeding at all.
 */
internal object PortalHooks {
    private const val TAG = "DragShare/Taplus"
    private const val SERVICE_CLASS =
        "com.miui.contentextension.services.TextContentExtensionService"
    private const val CALLBACK_CLASS = "$SERVICE_CLASS\$1"
    private const val BASE_FLOAT_CLASS =
        "com.miui.contentextension.text.floatview.BaseFloatView"
    private const val MOTION_KEY = "MotionEvent"
    private const val CONTROL_KEY = "observe_control_event"

    private val DEFERRED_HOST_LOCK = Any()
    private val DEFERRED_HOST_CALLS = ArrayDeque<DeferredHostCall>()
    private val REPLAYING_HOST_CALL = ThreadLocal<Boolean>()

    private val HOOKED_CALLBACK_CLASSES: MutableSet<Class<*>> =
        Collections.newSetFromMap(ConcurrentHashMap())

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var controller: DragShareController? = null

    @Volatile
    private var motionSource: MiuiMotionSource? = null

    @Volatile
    private var rootTouchSource: RootTouchSource? = null

    @Volatile
    private var rootAuthorityLogged = false

    @Volatile
    private var hostCancelIgnoredLogged = false

    @Volatile
    private var controlIgnoredLogged = false

    @Volatile
    private var activationReported = false

    @Volatile
    private var portalContext: Context? = null

    @Volatile
    private var portalClassLoader: ClassLoader? = null

    @Volatile
    private var settingsObserver: ContentObserver? = null

    @Volatile
    private var frameworkInfo = "unavailable"

    /** The framework handle the hooks and the deferred replay are installed through. */
    @Volatile
    private var xposed: XposedInterface? = null

    fun install(module: XposedInterface, classLoader: ClassLoader) {
        try {
            xposed = module
            portalClassLoader = classLoader
            frameworkInfo = readFrameworkInfo(module)
            val serviceClass = PortalReflect.requireClass(SERVICE_CLASS, classLoader)
            reportActivationOnApplicationCreate(classLoader)
            suppressOriginalFloatWindows(classLoader)
            hookServiceLifecycle(serviceClass, classLoader)
            hookShareStart(serviceClass)
            hookKnownCallbackClass(classLoader)
            log("Taplus 4.2.1 hooks installed")
        } catch (error: Throwable) {
            log("unable to install Taplus hooks", error)
        }
    }

    private fun intercept(target: Executable, hooker: Hooker) {
        val module = xposed ?: throw IllegalStateException("libxposed framework is not attached")
        module.hook(target).intercept(hooker)
    }

    /**
     * Reports the injected build as soon as any portal process has a Context. The settings UI
     * must be able to see the current version even when the text service was never created,
     * for example right after the portal was cold started only to answer this handshake.
     */
    private fun reportActivationOnApplicationCreate(classLoader: ClassLoader) {
        val target = PortalReflect.requireMethod(
            PortalReflect.requireClass("android.app.Instrumentation", classLoader),
            "callApplicationOnCreate",
            Application::class.java,
        )
        intercept(target, Hooker { chain ->
            val result = chain.proceed()
            if (!activationReported) {
                try {
                    val application = chain.args[0] as Application
                    val context = application.applicationContext ?: application
                    // The provider call may have to start the module process, which
                    // must not delay the portal's own startup.
                    Thread(
                        {
                            activationReported = reportPortalActivation(context) || activationReported
                        },
                        "DragShare-PortalActivation",
                    ).start()
                } catch (error: Throwable) {
                    log("unable to report activation on application create", error)
                }
            }
            result
        })
    }

    private fun suppressOriginalFloatWindows(classLoader: ClassLoader) {
        val target = PortalReflect.requireMethod(
            PortalReflect.requireClass(BASE_FLOAT_CLASS, classLoader),
            "addToWindow",
        )
        intercept(target, Hooker { chain ->
            val suppress = try {
                val current = controller
                current != null && current.shouldSuppressPortalHostFloatWindow()
            } catch (error: Throwable) {
                log("unable to decide host float window suppression", error)
                false
            }
            if (suppress) null else chain.proceed()
        })
    }

    private fun hookServiceLifecycle(serviceClass: Class<*>, classLoader: ClassLoader) {
        intercept(PortalReflect.requireMethod(serviceClass, "onCreate"), Hooker { chain ->
            val result = chain.proceed()
            try {
                val context = (chain.thisObject as Context).applicationContext
                portalContext = context
                portalClassLoader = classLoader
                DragShareLog.configure(DragShareSettings.readFromProvider(context))
                DragShareDiagnostics.captureRuntimeOnce(
                    context,
                    "portal service created",
                    frameworkInfo,
                )
                activationReported = reportPortalActivation(context)
                registerSettingsObserver(context)
                applyPortalRuntime()
                hookCallbackFromService(chain.thisObject)
            } catch (error: Throwable) {
                log("unable to prepare portal runtime", error)
            }
            result
        })

        intercept(PortalReflect.requireMethod(serviceClass, "onDestroy"), Hooker { chain ->
            try {
                unregisterSettingsObserver()
                stopPortalRuntime(false)
                portalContext = null
            } catch (error: Throwable) {
                log("unable to stop portal runtime", error)
            }
            chain.proceed()
        })

        intercept(
            PortalReflect.requireMethod(
                serviceClass,
                "onStartCommand",
                Intent::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ),
            Hooker { chain ->
                val result = chain.proceed()
                try {
                    val context = (chain.thisObject as Context).applicationContext
                    activationReported = reportPortalActivation(context) || activationReported
                } catch (error: Throwable) {
                    log("unable to report activation on start command", error)
                }
                result
            },
        )

        intercept(PortalReflect.requireMethod(serviceClass, "cancelTask"), Hooker { chain ->
            var suppressed = false
            if (!isReplayingHostCall()) {
                try {
                    val current = controller
                    if (current != null) {
                        if (deferHostCallIfRootDragActive(chain, "cancelTask")) {
                            // Let the root ACTION_UP replay the original method after the
                            // physical gesture has ended. Running it now poisons Taplus'
                            // task state and prevents the next long press in the same app.
                            suppressed = true
                            if (!hostCancelIgnoredLogged) {
                                hostCancelIgnoredLogged = true
                                log("ignoring host cancel while root drag is active")
                            }
                        } else {
                            current.onHostTaskCancelled()
                        }
                    }
                } catch (error: Throwable) {
                    log("unable to handle host cancel", error)
                }
            }
            if (suppressed) null else chain.proceed()
        })
    }

    private fun hookShareStart(serviceClass: Class<*>) {
        val showHook = Hooker { chain ->
            try {
                startFromPickTask(chain)
            } catch (error: Throwable) {
                log("unable to start drag share", error)
            }
            chain.proceed()
        }
        intercept(PortalReflect.requireMethod(serviceClass, "startPickTextTask"), showHook)
        intercept(PortalReflect.requireMethod(serviceClass, "startPickImageTask"), showHook)
    }

    private fun startFromPickTask(chain: Chain) {
        val service = chain.thisObject ?: return
        if (!activationReported) {
            val context = (service as Context).applicationContext
            activationReported = reportPortalActivation(context)
        }
        val context = (service as Context).applicationContext
        val settings = DragShareSettings.readFromProvider(context)
        if (!settings.isPortalCaptureMode) {
            return
        }
        applyPortalRuntime()
        val current = controller ?: return
        val content = PortalContentCaptureSource.capture(service) ?: return
        val point = PortalContentCaptureSource.initialPoint(
            service,
            current.latestPointerX(),
            current.latestPointerY(),
        )
        current.reservePortalHostFloatWindowSuppression()
        current.show(
            content,
            if (point == null) -1f else point.x,
            if (point == null) -1f else point.y,
            settings,
        )
    }

    private fun hookKnownCallbackClass(classLoader: ClassLoader) {
        val callbackClass = PortalReflect.findClassOrNull(CALLBACK_CLASS, classLoader)
        if (callbackClass != null) {
            hookCallbackClass(callbackClass)
        }
    }

    private fun hookCallbackFromService(service: Any) {
        try {
            val callback = PortalReflect.getObjectField(service, "mCallback")
            if (callback != null) {
                hookCallbackClass(callback.javaClass)
            }
        } catch (error: Throwable) {
            log("unable to discover Taplus callback", error)
        }
    }

    private fun hookCallbackClass(callbackClass: Class<*>) {
        if (!HOOKED_CALLBACK_CLASSES.add(callbackClass)) {
            return
        }

        try {
            var target: Method? = null
            for (method in callbackClass.declaredMethods) {
                if ("onContentReceived" == method.name && method.parameterTypes.size == 1) {
                    target = method
                    break
                }
            }
            if (target == null) {
                throw NoSuchMethodException(callbackClass.name + ".onContentReceived")
            }
            target.isAccessible = true
            intercept(target, Hooker { chain ->
                if (handleCallback(chain)) null else chain.proceed()
            })
            log("hooked callback " + callbackClass.name)
        } catch (error: Throwable) {
            HOOKED_CALLBACK_CLASSES.remove(callbackClass)
            log("unable to hook Taplus callback", error)
        }
    }

    /** Returns true when the host callback has to be swallowed instead of reaching Taplus. */
    @Suppress("UNCHECKED_CAST")
    private fun handleCallback(chain: Chain): Boolean {
        try {
            if (isReplayingHostCall()) {
                return false
            }
            val payload = chain.args.firstOrNull() ?: return false
            val properties = PortalReflect.callMethod(payload, "getPropertyMap")
                as? Map<String, Any?> ?: return false

            val motion = properties[MOTION_KEY]
            DragShareLog.d(
                TAG,
                "portal callback keys=" + properties.keys
                    + " motion=" + (motion is MotionEvent)
                    + " control=" + properties[CONTROL_KEY].toString(),
            )
            if (motion is MotionEvent) {
                dispatchMotion(MotionEvent.obtain(motion))
                // A motion-only result is not valid Taplus pick content.
                return true
            }

            val control = properties[CONTROL_KEY]
            if ("258" == control) {
                // Taplus normally interprets a move as cancellation.
                return true
            }
            if ("257" == control) {
                val current = controller
                if (deferHostCallIfRootDragActive(chain, "control-257")) {
                    if (!controlIgnoredLogged) {
                        controlIgnoredLogged = true
                        log("ignoring host finish; waiting for root ACTION_UP")
                    }
                    return true
                }
                current?.finishFromControlEvent()
            }
        } catch (error: Throwable) {
            log("callback handling failed", error)
        }
        return false
    }

    private fun dispatchMotion(event: MotionEvent) {
        DragShareLog.d(
            TAG,
            "MIUI motion action=" + MotionEvent.actionToString(event.actionMasked)
                + " point=" + event.rawX.roundToInt() + "," + event.rawY.roundToInt(),
        )
        if (hasReadyRootSource()) {
            event.recycle()
            if (!rootAuthorityLogged) {
                rootAuthorityLogged = true
                log("root input is authoritative; ignoring MIUI motion events")
            }
            return
        }
        val current = controller
        if (current == null) {
            event.recycle()
            return
        }
        val action = event.actionMasked
        current.acceptMotionEvent(
            event,
            if (action == MotionEvent.ACTION_UP) Runnable { replayDeferredHostCalls() } else null,
        )
    }

    private fun hasReadyRootSource(): Boolean {
        val source = rootTouchSource
        return source != null && source.isReady
    }

    private fun reportPortalActivation(context: Context): Boolean {
        val reported = ModuleActivation.reportInjected(context)
        if (reported) {
            ModuleActivation.probePortalRootAccessAsync(context)
        }
        return reported
    }

    @Synchronized
    private fun applyPortalRuntime() {
        val context = portalContext
        val classLoader = portalClassLoader
        if (context == null || classLoader == null) {
            return
        }
        val settings = DragShareSettings.readFromProvider(context)
        DragShareLog.configure(settings)
        DragShareLog.d(
            TAG,
            "portal runtime settings captureMode=" + settings.contentCaptureMode
                + " logLevel=" + settings.logLevel
                + " logDestination=" + settings.logDestination,
        )
        if (!settings.isPortalCaptureMode) {
            stopPortalRuntime(true)
            return
        }
        if (controller != null) {
            return
        }
        rootAuthorityLogged = false
        hostCancelIgnoredLogged = false
        controlIgnoredLogged = false
        clearDeferredHostCalls()
        controller = DragShareController(context, OverlayWindowPolicy.portal())
        val source = MiuiMotionSource(
            classLoader,
            MiuiMotionSource.Listener { event -> dispatchMotion(event) },
        )
        motionSource = source
        source.start()
        val rootSource = RootTouchSource(context) { action, x, y, eventTime ->
            dispatchRootPointer(action, x, y, eventTime)
        }
        rootTouchSource = rootSource
        rootSource.start()
        log("portal runtime started")
    }

    @Synchronized
    private fun stopPortalRuntime(replayDeferredCalls: Boolean) {
        if (replayDeferredCalls) {
            replayDeferredHostCalls()
        }
        val source = motionSource
        motionSource = null
        source?.stop()
        val rootSource = rootTouchSource
        rootTouchSource = null
        rootSource?.stop()
        val current = controller
        controller = null
        current?.destroy()
        clearDeferredHostCalls()
    }

    private fun registerSettingsObserver(context: Context) {
        unregisterSettingsObserver()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                applyPortalRuntime()
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                DragShareSettings.settingsUri(),
                false,
                observer,
            )
            settingsObserver = observer
        } catch (error: Throwable) {
            log("unable to observe settings changes", error)
        }
    }

    private fun unregisterSettingsObserver() {
        val observer = settingsObserver
        settingsObserver = null
        val context = portalContext
        if (observer == null || context == null) {
            return
        }
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Throwable) {
            // The process may be leaving while the resolver is already gone.
        }
    }

    private fun dispatchRootPointer(action: Int, x: Float, y: Float, eventTime: Long) {
        DragShareLog.d(
            TAG,
            "root pointer action=" + MotionEvent.actionToString(action)
                + " point=" + x.roundToInt() + "," + y.roundToInt()
                + " time=" + eventTime,
        )
        val current = controller
        current?.acceptPointerEvent(
            action,
            x,
            y,
            eventTime,
            "root",
            if (action == MotionEvent.ACTION_UP) Runnable { replayDeferredHostCalls() } else null,
        )
    }

    private fun deferHostCallIfRootDragActive(chain: Chain, kind: String): Boolean {
        synchronized(DEFERRED_HOST_LOCK) {
            val current = controller
            if (current == null || !current.isActive || !hasReadyRootSource()) {
                return false
            }
            for (existing in DEFERRED_HOST_CALLS) {
                if (kind == existing.kind) {
                    return true
                }
            }
            DEFERRED_HOST_CALLS.addLast(
                DeferredHostCall(
                    kind,
                    chain.executable,
                    chain.thisObject,
                    chain.args.toTypedArray(),
                ),
            )
        }
        log("deferred host call=$kind until root ACTION_UP")
        return true
    }

    private fun replayDeferredHostCalls() {
        val calls: List<DeferredHostCall>
        synchronized(DEFERRED_HOST_LOCK) {
            if (DEFERRED_HOST_CALLS.isEmpty()) {
                return
            }
            calls = ArrayList(DEFERRED_HOST_CALLS)
            DEFERRED_HOST_CALLS.clear()
        }

        REPLAYING_HOST_CALL.set(java.lang.Boolean.TRUE)
        try {
            for (call in calls) {
                try {
                    invokeHostOriginal(call.method, call.receiver, call.args)
                    log("replayed host call=" + call.kind)
                } catch (error: Throwable) {
                    log("unable to replay host call=" + call.kind, error)
                }
            }
        } finally {
            REPLAYING_HOST_CALL.remove()
        }
    }

    /** Runs a host method with every hook skipped; the API 102 form of invokeOriginalMethod. */
    private fun invokeHostOriginal(target: Executable, receiver: Any?, args: Array<Any?>) {
        val module = xposed ?: throw IllegalStateException("libxposed framework is not attached")
        val method = target as? Method
            ?: throw IllegalArgumentException("cannot replay $target")
        module.getInvoker(method)
            .setType(XposedInterface.Invoker.Type.ORIGIN)
            .invoke(receiver, *args)
    }

    private fun clearDeferredHostCalls() {
        synchronized(DEFERRED_HOST_LOCK) {
            DEFERRED_HOST_CALLS.clear()
        }
    }

    private fun isReplayingHostCall(): Boolean = REPLAYING_HOST_CALL.get() == true

    private fun readFrameworkInfo(module: XposedInterface): String = try {
        module.frameworkName + " " + module.frameworkVersion +
            " (" + module.frameworkVersionCode + ") api=" + module.apiVersion
    } catch (error: Throwable) {
        "unavailable:" + error.javaClass.simpleName
    }

    private class DeferredHostCall(
        val kind: String,
        val method: Executable,
        val receiver: Any?,
        val args: Array<Any?>,
    )

    private fun log(message: String) {
        DragShareLog.i(TAG, message)
    }

    private fun log(message: String, error: Throwable) {
        DragShareLog.w(TAG, message, error)
    }
}
