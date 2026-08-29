package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/** In-process transient message that works when the ROM suppresses background Toasts. */
class DragShareToast(private val context: Context?, windowPolicy: OverlayWindowPolicy?) {
    private val windowManager: WindowManager? = if (context == null) {
        null
    } else {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
    }
    private val windowPolicy: OverlayWindowPolicy = windowPolicy ?: OverlayWindowPolicy.portal()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var removeRunnable: Runnable? = null
    private var closed = false

    @JvmOverloads
    fun show(message: String?, duration: Int = android.widget.Toast.LENGTH_SHORT) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            showOnMain(message, duration)
        } else {
            mainHandler.post { showOnMain(message, duration) }
        }
    }

    fun close() {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            closeOnMain()
        } else {
            mainHandler.post { closeOnMain() }
        }
    }

    private fun showOnMain(message: String?, duration: Int) {
        if (closed || message == null || message.trim().isEmpty()) {
            return
        }
        removeOnMain()
        if (windowManager == null || context == null) {
            fallbackToast(message, duration)
            return
        }

        val next = TextView(context)
        next.text = message
        next.setTextColor(Color.WHITE)
        next.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        next.gravity = Gravity.CENTER
        next.maxLines = 2
        next.ellipsize = android.text.TextUtils.TruncateAt.END
        next.setPadding(dp(18), dp(10), dp(18), dp(10))
        next.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val background = GradientDrawable()
        background.setColor(0xE52A2D32.toInt())
        background.cornerRadius = dp(22).toFloat()
        next.background = background

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowPolicy.windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(112)
        params.title = "DragShare message"
        try {
            windowManager.addView(next, params)
            view = next
            val timeout = if (duration == android.widget.Toast.LENGTH_LONG) {
                LONG_DURATION_MILLIS
            } else {
                DEFAULT_DURATION_MILLIS
            }
            val remove = Runnable { removeOnMain() }
            removeRunnable = remove
            mainHandler.postDelayed(remove, timeout)
        } catch (error: Throwable) {
            DragShareLog.w("DragShare/Toast", "overlay message failed", error)
            fallbackToast(message, duration)
        }
    }

    private fun fallbackToast(message: String?, duration: Int) {
        try {
            android.widget.Toast.makeText(
                context?.applicationContext,
                message,
                duration,
            ).show()
        } catch (error: Throwable) {
            DragShareLog.w("DragShare/Toast", "system Toast fallback failed", error)
        }
    }

    private fun removeOnMain() {
        val remove = removeRunnable
        if (remove != null) {
            mainHandler.removeCallbacks(remove)
            removeRunnable = null
        }
        val current = view
        if (current != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(current)
            } catch (_: Throwable) {
                // The service or host may already be tearing down the window.
            }
        }
        view = null
    }

    private fun closeOnMain() {
        closed = true
        removeOnMain()
    }

    private fun dp(value: Int): Int {
        if (context == null) {
            return value
        }
        return max(
            1,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics,
            ).roundToInt(),
        )
    }

    companion object {
        private const val DEFAULT_DURATION_MILLIS = 2_000L
        private const val LONG_DURATION_MILLIS = 3_500L
    }
}
