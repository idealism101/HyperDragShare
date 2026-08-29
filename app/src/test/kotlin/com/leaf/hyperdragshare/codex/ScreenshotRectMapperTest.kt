package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ScreenshotRectMapperTest {
    @Test
    fun expandsAndMapsSameSizeDisplay() {
        assertEquals(
            Rect(9, 19, 31, 41),
            ScreenshotRectMapper.mapAndExpand(
                Rect(10, 20, 30, 40), 100, 100, 100, 100, 1,
            ),
        )
    }

    @Test
    fun mapsUniformScaleAndClamps() {
        assertEquals(
            Rect(0, 0, 40, 40),
            ScreenshotRectMapper.mapAndExpand(
                Rect(-10, -10, 20, 20), 100, 100, 200, 200, 0,
            ),
        )
    }

    @Test
    fun rejectsSwappedOrOutsideDimensions() {
        assertNull(
            ScreenshotRectMapper.mapAndExpand(
                Rect(1, 1, 10, 10), 100, 200, 200, 100, 0,
            ),
        )
        assertNull(
            ScreenshotRectMapper.mapAndExpand(
                Rect(300, 300, 400, 400), 100, 100, 100, 100, 0,
            ),
        )
    }
}
