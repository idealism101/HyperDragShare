package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Maps getBoundsInScreen coordinates onto a screenshot and safely clamps the result. */
object ScreenshotRectMapper {
    fun mapAndExpand(
        source: Rect?,
        displayWidth: Int,
        displayHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        expansionPixels: Int,
    ): Rect? {
        if (source == null || displayWidth <= 0 || displayHeight <= 0
            || bitmapWidth <= 0 || bitmapHeight <= 0
        ) {
            return null
        }
        val displayLandscape = displayWidth > displayHeight
        val bitmapLandscape = bitmapWidth > bitmapHeight
        if (displayLandscape != bitmapLandscape && displayWidth != displayHeight
            && bitmapWidth != bitmapHeight
        ) {
            return null
        }
        val scaleX = bitmapWidth / displayWidth.toFloat()
        val scaleY = bitmapHeight / displayHeight.toFloat()
        if (abs(scaleX - scaleY) > max(0.03f, min(scaleX, scaleY) * 0.03f)) {
            return null
        }
        val expanded = Rect(source)
        val edge = max(0, expansionPixels)
        expanded.inset(-edge, -edge)
        val result = Rect(
            floor(expanded.left * scaleX).toInt(),
            floor(expanded.top * scaleY).toInt(),
            ceil(expanded.right * scaleX).toInt(),
            ceil(expanded.bottom * scaleY).toInt(),
        )
        if (!result.intersect(0, 0, bitmapWidth, bitmapHeight)
            || result.width() < 1 || result.height() < 1
        ) {
            return null
        }
        return result
    }
}
