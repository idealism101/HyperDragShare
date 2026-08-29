package com.leaf.hyperdragshare.codex

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import java.util.function.Consumer
import kotlin.math.max

/**
 * A bounded, non-interactive native window for the modern preview and menu.
 * Window background blur is compositor-backed and respects the transparent
 * rounded window background, unlike blur-behind which affects all content
 * below an overlay window.
 */
internal class ModernOverlayWindow(
    context: Context?,
    windowManager: WindowManager?,
    overlayView: ModernOverlayComposeView?,
    layoutParams: WindowManager.LayoutParams?,
    blurRadiusPx: Int,
) {
    private val dialog: Dialog
    private val windowManager: WindowManager
    private val overlayView: ModernOverlayComposeView
    private val layoutParams: WindowManager.LayoutParams
    private val blurRadiusPx: Int
    private val blurEnabledListener: Consumer<Boolean>
    private var blurListenerRegistered = false
    private var crossWindowBlurEnabled = false
    private var nativeBackdropBlurEnabled = false
    private var disposed = false
    private var animationGeneration = 0

    init {
        if (context == null || windowManager == null || overlayView == null ||
            layoutParams == null
        ) {
            throw IllegalArgumentException("Modern overlay window requires its view and layout")
        }
        this.windowManager = windowManager
        this.overlayView = overlayView
        this.layoutParams = layoutParams
        this.blurRadiusPx = max(0, blurRadiusPx)

        val themedContext = ContextThemeWrapper(
            context,
            android.R.style.Theme_Translucent_NoTitleBar,
        )
        dialog = Dialog(themedContext)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(overlayView)

        val window = dialog.window
            ?: throw IllegalStateException("Modern overlay dialog has no Window")
        window.setBackgroundDrawable(transparentRoundedBackground(context))
        window.setDimAmount(0f)
        window.setWindowAnimations(0)
        window.attributes = layoutParams
        window.setBackgroundBlurRadius(this.blurRadiusPx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        blurEnabledListener = Consumer { enabled ->
            overlayView.post {
                if (!disposed) {
                    updateCrossWindowBlurEnabled(enabled == true)
                }
            }
        }
        refreshNativeBackdropBlurEnabled()
        registerBlurEnabledListener()
    }

    fun show() {
        if (!disposed && !dialog.isShowing) {
            prepareDecorForEnter()
            dialog.show()
        }
    }

    fun showAnimated() {
        if (disposed) {
            return
        }
        animationGeneration++
        show()
        overlayView.setContentVisibleForWindowAnimation(true)
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

    fun hideAnimated(durationMillis: Long, afterAnimation: Runnable?) {
        if (disposed) {
            return
        }
        val generation = ++animationGeneration
        val decorView = decorView()
        if (!dialog.isShowing || decorView == null) {
            finishHideAnimation(generation, afterAnimation)
            return
        }
        decorView.animate().cancel()
        decorView.animate()
            .alpha(0f)
            .scaleX(EXIT_TARGET_SCALE)
            .scaleY(EXIT_TARGET_SCALE)
            .setDuration(max(0L, durationMillis))
            .setInterpolator(EXIT_INTERPOLATOR)
            .withEndAction { finishHideAnimation(generation, afterAnimation) }
            .start()
    }

    fun isShowing(): Boolean = !disposed && dialog.isShowing

    fun isNativeBackdropBlurEnabled(): Boolean = nativeBackdropBlurEnabled

    fun updateLayout() {
        if (disposed || !dialog.isShowing) {
            return
        }
        val window = dialog.window
        val decorView = window?.decorView
        if (decorView != null) {
            windowManager.updateViewLayout(decorView, layoutParams)
        }
    }

    fun dispose() {
        if (disposed) {
            return
        }
        animationGeneration++
        disposed = true
        decorView()?.animate()?.cancel()
        if (blurListenerRegistered) {
            try {
                windowManager.removeCrossWindowBlurEnabledListener(blurEnabledListener)
            } catch (_: Throwable) {
                // The host window manager can already be shutting down.
            }
            blurListenerRegistered = false
        }
        try {
            dialog.dismiss()
        } catch (_: Throwable) {
            // The dialog may never have reached WindowManager.addView().
        }
        overlayView.disposeOverlay()
    }

    private fun registerBlurEnabledListener() {
        if (blurRadiusPx <= 0) {
            return
        }
        try {
            windowManager.addCrossWindowBlurEnabledListener(blurEnabledListener)
            blurListenerRegistered = true
        } catch (_: Throwable) {
            updateCrossWindowBlurEnabled(false)
        }
    }

    private fun refreshNativeBackdropBlurEnabled() {
        var enabled = false
        if (blurRadiusPx > 0) {
            try {
                enabled = windowManager.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
                // The transparent glass fallback below remains readable.
            }
        }
        updateCrossWindowBlurEnabled(enabled)
    }

    private fun updateCrossWindowBlurEnabled(enabled: Boolean) {
        crossWindowBlurEnabled = enabled
        updateOverlayBlurAvailability()
    }

    private fun updateOverlayBlurAvailability() {
        nativeBackdropBlurEnabled = shouldUseNativeBackdropBlur(
            blurRadiusPx,
            crossWindowBlurEnabled,
        )
        overlayView.updateNativeBackdropBlurAvailability(nativeBackdropBlurEnabled)
    }

    private fun prepareDecorForEnter() {
        val decorView = decorView() ?: return
        decorView.animate().cancel()
        decorView.alpha = 0f
        decorView.scaleX = ENTER_INITIAL_SCALE
        decorView.scaleY = ENTER_INITIAL_SCALE
    }

    private fun decorView(): View? = dialog.window?.decorView

    private fun finishHideAnimation(generation: Int, afterAnimation: Runnable?) {
        if (disposed || animationGeneration != generation) {
            return
        }
        overlayView.setContentVisibleForWindowAnimation(false)
        afterAnimation?.run()
    }

    companion object {
        private const val CARD_CORNER_RADIUS_DP = 16f
        private const val ENTER_DURATION_MS = 220L
        private const val ENTER_INITIAL_SCALE = 0.9f
        private const val EXIT_TARGET_SCALE = 0.96f
        private val ENTER_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val EXIT_INTERPOLATOR = PathInterpolator(0.4f, 0f, 1f, 1f)

        @JvmStatic
        fun transparentRoundedBackground(context: Context): GradientDrawable {
            val drawable = GradientDrawable()
            drawable.setColor(Color.TRANSPARENT)
            drawable.cornerRadius = localBackgroundCornerRadiusPx(context)
            return drawable
        }

        @JvmStatic
        fun localBackgroundCornerRadiusPx(context: Context): Float =
            CARD_CORNER_RADIUS_DP * context.resources.displayMetrics.density

        @JvmStatic
        fun shouldUseNativeBackdropBlur(radiusPx: Int, crossWindowBlurEnabled: Boolean): Boolean =
            radiusPx > 0 && crossWindowBlurEnabled
    }
}
