package com.leaf.hyperdragshare.codex

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/** App-icon tile for the built-in text-segmentation action. */
class TextSegmentationTargetIconDrawable(
    sourceGlyph: Drawable?,
    private val accentColor: Int,
) : Drawable() {
    private val glyph: Drawable? = if (sourceGlyph == null) {
        null
    } else {
        val state = sourceGlyph.constantState
        (state?.newDrawable() ?: sourceGlyph).mutate().also { it.setTint(Color.WHITE) }
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alpha = 255

    init {
        fallbackPaint.color = Color.WHITE
        fallbackPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val bounds = getBounds()
        if (bounds.isEmpty) {
            return
        }
        val size = min(bounds.width(), bounds.height()).toFloat()
        tilePaint.color = (accentColor and 0x00FFFFFF) or
            (Math.round(Color.alpha(accentColor) * alpha / 255f) shl 24)
        canvas.drawRoundRect(RectF(bounds), size * 0.24f, size * 0.24f, tilePaint)

        val inset = max(1, Math.round(size * 0.20f))
        val glyphBounds = Rect(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset,
        )
        val currentGlyph = glyph
        if (currentGlyph != null) {
            currentGlyph.alpha = alpha
            currentGlyph.bounds = glyphBounds
            currentGlyph.draw(canvas)
        } else {
            drawFallbackGlyph(canvas, glyphBounds, size)
        }
    }

    private fun drawFallbackGlyph(canvas: Canvas, bounds: Rect, tileSize: Float) {
        fallbackPaint.alpha = alpha
        val lineHeight = max(2f, tileSize * 0.11f)
        val radius = lineHeight / 2f
        val left = bounds.left + bounds.width() * 0.04f
        val right = bounds.right - bounds.width() * 0.04f
        for (index in 0 until 3) {
            val centerY = bounds.top + bounds.height() * (0.23f + index * 0.27f)
            canvas.drawRoundRect(
                RectF(left, centerY - radius, right, centerY + radius),
                radius,
                radius,
                fallbackPaint,
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = max(0, min(255, alpha))
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tilePaint.colorFilter = colorFilter
        fallbackPaint.colorFilter = colorFilter
        glyph?.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
