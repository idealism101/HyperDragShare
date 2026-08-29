package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.Build
import android.view.Display
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/**
 * Best-effort cancellation of the foreground app's current touch stream.
 *
 * The portal process normally observes the gesture without owning it. On
 * system builds that expose the hidden InputManager cancellation/monitor APIs
 * to the portal UID, the current pointer stream is cancelled or pilfered so
 * the original window cannot continue a scroll. The feature is optional
 * because these are hidden, permission-gated APIs.
 */
class BackgroundTouchBlocker @JvmOverloads constructor(
    context: Context?,
    methodInvoker: MethodInvoker? = null,
    rootTouchCanceller: RootTouchCanceller? = null,
) {
    private val context: Context? = context?.applicationContext
    private val methodInvoker: MethodInvoker = methodInvoker ?: ReflectiveMethodInvoker()
    private val rootTouchCanceller: RootTouchCanceller =
        rootTouchCanceller ?: RootServiceCallCanceller()
    private var inputMonitor: Any? = null
    private var started = false

    init {
        this.rootTouchCanceller.prepare()
    }

    @Synchronized
    fun start(): Boolean {
        if (started) {
            return true
        }
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            log("gesture monitor unavailable on this Android version")
            return false
        }

        var monitor: Any? = null
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager == null) {
                log("InputManager service is null")
                return false
            }

            // Android 14 exposes a simpler hidden operation that cancels the
            // current targets without requiring an input channel to be kept
            // alive. Prefer it when the ROM makes it available.
            try {
                methodInvoker.invoke(inputManager, "cancelCurrentTouch")
                started = true
                log("foreground touch stream cancelled")
                return true
            } catch (unavailable: Throwable) {
                log("cancelCurrentTouch unavailable; trying gesture monitor", unavailable)
            }

            val displayId = resolveDisplayId()

            monitor = methodInvoker.invoke(
                inputManager,
                "monitorGestureInput",
                MONITOR_NAME,
                displayId,
            )
            if (monitor == null) {
                log("monitorGestureInput returned null")
            } else {
                // This sends ACTION_CANCEL to the previous touch target and makes
                // the monitor the target for the remainder of the pointer stream.
                methodInvoker.invoke(monitor, "pilferPointers")
                inputMonitor = monitor
                started = true
                log("foreground touch stream pilfered")
                return true
            }
        } catch (error: Throwable) {
            val pending = monitor
            if (pending != null) {
                try {
                    methodInvoker.invoke(pending, "dispose")
                } catch (_: Throwable) {
                    // Keep the original failure as the diagnostic signal.
                }
            }
            log("gesture monitor unavailable; trying root cancellation", error)
        }

        try {
            if (rootTouchCanceller.cancelCurrentTouch()) {
                started = true
                log("foreground touch stream cancelled through root")
                return true
            }
            log("root cancelCurrentTouch command failed")
        } catch (error: Throwable) {
            log("unable to cancel foreground touch stream through root", error)
        }
        return false
    }

    private fun resolveDisplayId(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Display.DEFAULT_DISPLAY
        }
        try {
            val display = context?.display
            if (display != null) {
                return display.displayId
            }
        } catch (_: Throwable) {
            // A Service application context on recent Android versions is not visual.
            log("context has no display; using the default display for gesture monitor")
        }
        return Display.DEFAULT_DISPLAY
    }

    @Synchronized
    fun stop() {
        val monitor = inputMonitor
        inputMonitor = null
        started = false
        if (monitor == null) {
            return
        }
        try {
            methodInvoker.invoke(monitor, "dispose")
        } catch (error: Throwable) {
            log("unable to dispose gesture monitor", error)
        }
    }

    fun interface MethodInvoker {
        @Throws(Throwable::class)
        fun invoke(target: Any?, methodName: String, vararg args: Any?): Any?
    }

    fun interface RootTouchCanceller {
        @Throws(Throwable::class)
        fun cancelCurrentTouch(): Boolean

        fun prepare() {}
    }

    private class RootServiceCallCanceller : RootTouchCanceller {
        private val transactionCodeTask = FutureTask<Int> {
            val transactionCode =
                FrameworkBinderTransactionResolver.resolveCancelCurrentTouchTransactionCode()
            if (transactionCode <= 0) {
                throw IllegalStateException("Invalid dynamically resolved input transaction")
            }
            log("resolved root input transaction dynamically")
            transactionCode
        }
        private var transactionCodeTaskStarted = false

        @Synchronized
        override fun prepare() {
            if (transactionCodeTaskStarted) {
                return
            }
            transactionCodeTaskStarted = true
            val thread = Thread(transactionCodeTask, "drag-share-input-transaction")
            thread.isDaemon = true
            thread.start()
        }

        @Throws(Throwable::class)
        override fun cancelCurrentTouch(): Boolean {
            prepare()
            val transactionCode = awaitTransactionCode()
            if (transactionCode <= 0) {
                return false
            }
            var process: Process? = null
            try {
                val started = ProcessBuilder(
                    "su",
                    "-c",
                    rootServiceCallCommand(transactionCode),
                )
                    .redirectErrorStream(true)
                    .start()
                process = started
                if (!started.waitFor(ROOT_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    started.destroyForcibly()
                    return false
                }
                val result: String = started.inputStream.use { output ->
                    String(output.readAllBytes(), StandardCharsets.UTF_8)
                }
                return started.exitValue() == 0 && isSuccessfulServiceCallResult(result)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            } finally {
                val current = process
                if (current != null && current.isAlive) {
                    current.destroyForcibly()
                }
            }
        }

        @Throws(Throwable::class)
        private fun awaitTransactionCode(): Int {
            try {
                return transactionCodeTask.get(ROOT_TRANSACTION_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                return -1
            } catch (error: ExecutionException) {
                val cause = error.cause
                if (cause != null) {
                    throw cause
                }
                throw error
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            }
        }
    }

    private class ReflectiveMethodInvoker : MethodInvoker {
        @Throws(Throwable::class)
        override fun invoke(target: Any?, methodName: String, vararg args: Any?): Any? {
            if (target == null) {
                throw NullPointerException("target")
            }
            val method = findMethod(target.javaClass, methodName, args)
            method.isAccessible = true
            try {
                return method.invoke(target, *args)
            } catch (error: InvocationTargetException) {
                val cause = error.cause
                if (cause != null) {
                    throw cause
                }
                throw error
            }
        }

        companion object {
            @Throws(NoSuchMethodException::class)
            private fun findMethod(
                type: Class<*>,
                name: String,
                args: Array<out Any?>,
            ): Method {
                var current: Class<*>? = type
                while (current != null) {
                    for (method in current.declaredMethods) {
                        if (name == method.name && matches(method.parameterTypes, args)) {
                            return method
                        }
                    }
                    current = current.superclass
                }
                throw NoSuchMethodException(type.name + "." + name)
            }

            private fun matches(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
                if (parameterTypes.size != args.size) {
                    return false
                }
                for (index in parameterTypes.indices) {
                    val value = args[index]
                    val parameter = parameterTypes[index]
                    if (value == null) {
                        if (parameter.isPrimitive) {
                            return false
                        }
                        continue
                    }
                    val valueType: Class<*> = value.javaClass
                    if (parameter.isPrimitive) {
                        if (!primitiveWrapper(parameter).isAssignableFrom(valueType)) {
                            return false
                        }
                    } else if (!parameter.isAssignableFrom(valueType)) {
                        return false
                    }
                }
                return true
            }

            private fun primitiveWrapper(primitive: Class<*>): Class<*> {
                if (primitive == Boolean::class.javaPrimitiveType) {
                    return Boolean::class.javaObjectType
                }
                if (primitive == Byte::class.javaPrimitiveType) {
                    return Byte::class.javaObjectType
                }
                if (primitive == Char::class.javaPrimitiveType) {
                    return Char::class.javaObjectType
                }
                if (primitive == Short::class.javaPrimitiveType) {
                    return Short::class.javaObjectType
                }
                if (primitive == Int::class.javaPrimitiveType) {
                    return Int::class.javaObjectType
                }
                if (primitive == Long::class.javaPrimitiveType) {
                    return Long::class.javaObjectType
                }
                if (primitive == Float::class.javaPrimitiveType) {
                    return Float::class.javaObjectType
                }
                if (primitive == Double::class.javaPrimitiveType) {
                    return Double::class.javaObjectType
                }
                return Void::class.javaObjectType
            }
        }
    }

    companion object {
        private const val TAG = "DragShare/InputBlocker"
        private const val MONITOR_NAME = "DragShare background lock"
        private const val ROOT_COMMAND_TIMEOUT_MILLIS = 1_000L
        private const val ROOT_TRANSACTION_WAIT_MILLIS = 50L
        private val SUCCESSFUL_SERVICE_CALL: Pattern = Pattern.compile(
            "Result:\\s*Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000(?=\\s|\\)|$)",
        )

        @JvmStatic
        fun rootServiceCallCommand(transactionCode: Int): String {
            if (transactionCode <= 0) {
                throw IllegalArgumentException("transactionCode must be positive")
            }
            return "uid=\$(/system/bin/id -u); " +
                "[ \"\$uid\" = 0 ] || exit 1; " +
                "/system/bin/service call input " + transactionCode
        }

        @JvmStatic
        fun isSuccessfulServiceCallResult(result: String?): Boolean =
            result != null && SUCCESSFUL_SERVICE_CALL.matcher(result).find()

        private fun log(message: String) {
            DragShareLog.i(TAG, message)
        }

        private fun log(message: String, error: Throwable?) {
            DragShareLog.w(TAG, message, error)
        }
    }
}
