package com.leaf.hyperdragshare.codex

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModernOverlayWindowTest {
    @Test
    fun transparentWindowBackgroundUsesTheModernLocalCornerRadius() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            16f * context.resources.displayMetrics.density,
            ModernOverlayWindow.localBackgroundCornerRadiusPx(context),
            0.001f,
        )
    }

    @Test
    fun opaqueFallbackIsUsedWhenCrossWindowBlurIsUnavailable() {
        assertTrue(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, true))
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(0, true))
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, false))
    }

    @Test
    fun portalUsesTheVerifiedOpaqueWindowTypeForPublicBackdropBlur() {
        assertEquals(
            WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
            OverlayWindowPolicy.portal().windowType,
        )
        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            OverlayWindowPolicy.accessibility().windowType,
        )
    }
}
