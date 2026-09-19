package com.leaf.hyperdragshare.codex

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import kotlin.math.max

/**
 * 磨砂菜单的「本地模糊」窗口。
 *
 * 用 Dialog 承载，配合 [Window.setBackgroundBlurRadius]（合成器级背景模糊）：
 * 模糊按窗口自身的透明圆角背景裁剪，**只糊背板范围**，菜单四周保持原样。
 * 这与 `FLAG_BLUR_BEHIND` 不同 —— 后者会模糊窗口下方全部内容，
 * 在 HyperOS 上表现为整屏模糊 + 压暗。同时 [Window.setDimAmount] 设 0，四周不压暗。
 *
 * 可交互：菜单内点击正常响应；点菜单外收起（Dialog 的 outside-touch）。
 */
internal class FrostedMenuWindow(
    context: Context?,
    windowManager: WindowManager?,
    contentView: View?,
    layoutParams: WindowManager.LayoutParams?,
    blurRadiusPx: Int,
    private val onOutsideDismiss: () -> Unit,
) {
    private val dialog: Dialog
    private val windowManager: WindowManager
    private var disposed = false
    private var nativeBlurEnabled = false

    init {
        if (context == null || windowManager == null || contentView == null ||
            layoutParams == null
        ) {
            throw IllegalArgumentException("Frosted menu window requires its view and layout")
        }
        this.windowManager = windowManager

        val themedContext = ContextThemeWrapper(
            context,
            android.R.style.Theme_Translucent_NoTitleBar,
        )
        dialog = Dialog(themedContext)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(contentView)

        val window = dialog.window
            ?: throw IllegalStateException("Frosted menu dialog has no Window")
        // 透明圆角窗口背景：既定义背板形状，也是背景模糊的裁剪形状。
        window.setBackgroundDrawable(plateClipBackground(context))
        window.setDimAmount(0f) // 四周不压暗
        window.setWindowAnimations(0)
        window.attributes = layoutParams
        // overlayParams 带 FLAG_NOT_TOUCHABLE（给纯展示窗口用的），这里必须去掉才能点。
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )

        val radius = max(0, blurRadiusPx)
        nativeBlurEnabled = radius > 0 && isCrossWindowBlurEnabled()
        window.setBackgroundBlurRadius(if (nativeBlurEnabled) radius else 0)

        // 按需求：触摸背板**不消失**。取消 setCanceledOnTouchOutside，
        // 收起只靠 decorView 的 ACTION_OUTSIDE（手指点在背板以外时才触发）。
        // 返回键不会误关：窗口是 FLAG_NOT_FOCUSABLE，收不到 key 事件。
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            DragShareLog.i(TAG, "dialog dismissed")
            onOutsideDismiss()
        }
        // 兜底：有些 ROM 上 Dialog 的"点外部关闭"不会触发，直接在 decorView 上接 ACTION_OUTSIDE。
        // 返回 false，不影响正常触摸分发（子项点击/滑动照旧）。
        window.decorView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                DragShareLog.i(TAG, "ACTION_OUTSIDE -> dismiss")
                onOutsideDismiss()
                true
            } else {
                false
            }
        }
    }

    fun isNativeBlurEnabled(): Boolean = nativeBlurEnabled

    fun isShowing(): Boolean = !disposed && dialog.isShowing

    fun show() {
        if (disposed || dialog.isShowing) {
            return
        }
        prepareDecorForEnter()
        try {
            dialog.show()
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "unable to show frosted menu dialog", error)
            return
        }
        DragShareLog.i(
            TAG,
            "frosted menu dialog shown blur=" + nativeBlurEnabled,
        )
        val decorView = decorView() ?: return
        decorView.animate().cancel()
        decorView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(ENTER_INTERPOLATOR)
            .start()
    }

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        DragShareLog.i(TAG, "dispose showing=" + dialog.isShowing)
        decorView()?.animate()?.cancel()
        try {
            dialog.dismiss()
        } catch (error: Throwable) {
            // 关不掉就会留下一个不可见的可触摸窗口挡住那块区域，必须记下来。
            DragShareLog.w(TAG, "dialog dismiss failed (ghost window risk)", error)
        }
    }

    private fun prepareDecorForEnter() {
        val decorView = decorView() ?: return
        decorView.animate().cancel()
        decorView.alpha = 0f
        decorView.scaleX = ENTER_INITIAL_SCALE
        decorView.scaleY = ENTER_INITIAL_SCALE
    }

    private fun decorView(): View? = dialog.window?.decorView

    private fun isCrossWindowBlurEnabled(): Boolean = try {
        windowManager.isCrossWindowBlurEnabled
    } catch (_: Throwable) {
        // Older/unusual ROMs may not report it; fall back to no blur.
        false
    }

    companion object {
        private const val TAG = "DragShare/FrostedMenu"
        private const val PLATE_CORNER_RADIUS_DP = 20f
        private const val ENTER_DURATION_MS = 180L
        private const val ENTER_INITIAL_SCALE = 0.96f
        private val ENTER_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)

        /** 透明圆角背景：只用于定义窗口/模糊的裁剪形状，颜色由内容视图自己画。 */
        fun plateClipBackground(context: Context): GradientDrawable =
            GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = PLATE_CORNER_RADIUS_DP * context.resources.displayMetrics.density
            }
    }
}
