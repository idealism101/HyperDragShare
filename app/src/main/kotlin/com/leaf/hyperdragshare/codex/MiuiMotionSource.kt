package com.leaf.hyperdragshare.codex

import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class MiuiMotionSource(
    private val classLoader: ClassLoader,
    private val listener: Listener,
) {
    fun interface Listener {
        fun onMotionEvent(event: MotionEvent)
    }

    private var thread: HandlerThread? = null
    private var manager: Any? = null
    private var motionListener: Any? = null
    private var firstEventLogged = false

    @Synchronized
    fun start() {
        if (motionListener != null) {
            return
        }
        try {
            val managerClass = PortalReflect.requireClass(
                "miui.hardware.input.MiuiInputManager", classLoader)
            val listenerClass = PortalReflect.requireClass(
                "miui.hardware.input.MiuiInputManager\$MiuiMotionEventListener", classLoader)
            val inputManager = PortalReflect.callStaticMethod(managerClass, "getInstance") ?: return

            val inputThread = HandlerThread("drag-share-input")
            inputThread.start()
            val handler = Handler(inputThread.looper)

            val invocationHandler = InvocationHandler { proxy, method, args ->
                if (method.declaringClass === Any::class.java) {
                    return@InvocationHandler handleObjectMethod(proxy, method, args)
                }
                if ("onMotionEvent" == method.name
                    && args != null
                    && args.size == 1
                    && args[0] is MotionEvent
                ) {
                    val current = args[0] as MotionEvent
                    if (!firstEventLogged) {
                        firstEventLogged = true
                        DragShareLog.i(
                            TAG,
                            "first event action="
                                + MotionEvent.actionToString(current.actionMasked),
                        )
                    }
                    // Only the gesture boundaries are worth a line; MOVE arrives per frame.
                    if (current.actionMasked != MotionEvent.ACTION_MOVE) {
                        DragShareLog.d(
                            TAG,
                            "event action="
                                + MotionEvent.actionToString(current.actionMasked)
                                + " pointers=" + current.pointerCount,
                        )
                    }
                    listener.onMotionEvent(MotionEvent.obtain(current))
                }
                null
            }
            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf<Class<*>>(listenerClass),
                invocationHandler,
            )
            PortalReflect.callMethod(
                inputManager,
                "registerMiuiMotionEventListener",
                proxy,
                handler,
            )

            thread = inputThread
            manager = inputManager
            motionListener = proxy
            DragShareLog.i(TAG, "fallback listener registered")
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "fallback listener unavailable", error)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        val inputManager = manager
        val listenerObject = motionListener
        val inputThread = thread
        manager = null
        motionListener = null
        thread = null

        if (inputManager != null && listenerObject != null) {
            try {
                PortalReflect.callMethod(
                    inputManager,
                    "unregisterMiuiMotionEventListener",
                    listenerObject,
                )
            } catch (error: Throwable) {
                DragShareLog.w(TAG, "unable to unregister fallback listener", error)
            }
        }
        inputThread?.quitSafely()
    }

    private fun handleObjectMethod(proxy: Any?, method: Method, args: Array<Any?>?): Any? =
        when (method.name) {
            "equals" -> args != null && args.size == 1 && proxy === args[0]
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "DragShareMiuiMotionListener"
            else -> null
        }

    private companion object {
        private const val TAG = "DragShare/MiuiInput"
    }
}
