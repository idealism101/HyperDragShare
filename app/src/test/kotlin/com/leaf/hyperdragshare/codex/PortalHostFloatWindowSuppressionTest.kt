package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PortalHostFloatWindowSuppressionTest {
    @Test
    fun pendingPortalPreviewSuppressesHostFloatWindowBeforeItBecomesActive() {
        val controller = DragShareController(
            RuntimeEnvironment.getApplication(),
            OverlayWindowPolicy.portal(),
        )

        assertFalse(controller.shouldSuppressPortalHostFloatWindow())

        controller.reservePortalHostFloatWindowSuppression()

        assertTrue(controller.shouldSuppressPortalHostFloatWindow())

        controller.destroy()

        assertFalse(controller.shouldSuppressPortalHostFloatWindow())
    }
}
