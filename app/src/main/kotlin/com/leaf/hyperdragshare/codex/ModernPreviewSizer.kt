package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure sizing policy for the modern square preview. */
internal object ModernPreviewSizer {
    private const val MIN_SIDE_DP = 76f
    private const val TEXT_BASE_SIDE_DP = 76f
    private const val IMAGE_BASE_SIDE_DP = 80f
    private const val MAX_TEXT_GROWTH_DP = 120f
    private const val MAX_IMAGE_GROWTH_DP = 140f

    fun squareSidePx(content: CapturedContent?, screenWidthPx: Int, density: Float): Int {
        val maxSidePx = max(1, screenWidthPx / 3)
        val safeDensity = max(0.1f, density)
        val minSidePx = min(maxSidePx.toFloat(), dp(MIN_SIDE_DP, safeDensity))
        val desiredSidePx = if (content != null && content.isImage()) {
            imageSidePx(content.bitmap, safeDensity)
        } else {
            textSidePx(content?.text, safeDensity)
        }
        return max(minSidePx, min(maxSidePx.toFloat(), desiredSidePx)).roundToInt()
    }

    private fun textSidePx(text: String?, density: Float): Float {
        val codePoints = text?.codePointCount(0, text.length) ?: 0
        val growthDp = min(
            MAX_TEXT_GROWTH_DP,
            sqrt(min(240, max(0, codePoints)).toDouble()).toFloat() * 5f,
        )
        return dp(TEXT_BASE_SIDE_DP + growthDp, density)
    }

    private fun imageSidePx(bitmap: Bitmap?, density: Float): Float {
        if (bitmap == null || bitmap.isRecycled) {
            return dp(IMAGE_BASE_SIDE_DP, density)
        }
        val geometricMeanPx = sqrt(
            max(1L, bitmap.width.toLong() * bitmap.height).toDouble(),
        ).toFloat()
        val growthDp = min(
            MAX_IMAGE_GROWTH_DP,
            geometricMeanPx * 0.18f / density,
        )
        return dp(IMAGE_BASE_SIDE_DP + growthDp, density)
    }

    private fun dp(value: Float, density: Float): Float = value * density
}
