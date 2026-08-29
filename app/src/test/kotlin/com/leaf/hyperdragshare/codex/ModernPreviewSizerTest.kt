package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModernPreviewSizerTest {
    @Test
    fun textPreviewGrowsWithContentAndNeverExceedsOneThirdOfScreenWidth() {
        val shortText = CapturedContent.text("短文字", "pkg", Rect())
        val longText = CapturedContent.text(repeat("现代拖拽预览", 40), "pkg", Rect())

        val shortSide = ModernPreviewSizer.squareSidePx(shortText, 1200, 3f)
        val longSide = ModernPreviewSizer.squareSidePx(longText, 1200, 3f)

        assertTrue(longSide > shortSide)
        assertTrue(shortSide <= 400)
        assertTrue(longSide <= 400)
    }

    @Test
    fun imagePreviewUsesImageDimensionsAndRespectsNarrowScreens() {
        val smallBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val largeBitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val smallImage = CapturedContent.image(smallBitmap, "pkg", Rect(), false)
        val largeImage = CapturedContent.image(largeBitmap, "pkg", Rect(), false)

        val smallSide = ModernPreviewSizer.squareSidePx(smallImage, 1080, 3f)
        val largeSide = ModernPreviewSizer.squareSidePx(largeImage, 1080, 3f)
        val narrowSide = ModernPreviewSizer.squareSidePx(largeImage, 300, 1f)

        assertTrue(largeSide > smallSide)
        assertTrue(largeSide <= 360)
        assertEquals(100, narrowSide)
    }

    private companion object {
        private fun repeat(value: String, count: Int): String {
            val builder = StringBuilder(value.length * count)
            for (index in 0 until count) {
                builder.append(value)
            }
            return builder.toString()
        }
    }
}
