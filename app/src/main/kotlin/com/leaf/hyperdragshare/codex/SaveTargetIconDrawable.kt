package com.leaf.hyperdragshare.codex

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/** App-icon tile for the built-in save action. */
class SaveTargetIconDrawable(sourceGlyph: Drawable?, private val accentColor: Int) : Drawable() {
    private val glyph: Drawable? = if (sourceGlyph == null) {
        null
    } else {
        val state = sourceGlyph.constantState
        (state?.newDrawable() ?: sourceGlyph).mutate().also { it.setTint(Color.WHITE) }
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPath = Path()
    private var alpha = 255

    init {
        fallbackPaint.color = Color.WHITE
        fallbackPaint.style = Paint.Style.STROKE
        fallbackPaint.strokeCap = Paint.Cap.ROUND
        fallbackPaint.strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        val bounds = getBounds()
        if (bounds.isEmpty) {
            return
        }
        val size = min(bounds.width(), bounds.height()).toFloat()
        val radius = size * 0.24f
        tilePaint.color = (accentColor and 0x00FFFFFF) or
            (Math.round(Color.alpha(accentColor) * alpha / 255f) shl 24)
        canvas.drawRoundRect(RectF(bounds), radius, radius, tilePaint)

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
        val centerX = bounds.exactCenterX()
        val top = bounds.top + bounds.height() * 0.08f
        val tip = bounds.top + bounds.height() * 0.62f
        val wing = bounds.width() * 0.22f
        fallbackPaint.alpha = alpha
        fallbackPaint.strokeWidth = max(2f, tileSize * 0.065f)
        fallbackPath.reset()
        fallbackPath.moveTo(centerX, top)
        fallbackPath.lineTo(centerX, tip)
        fallbackPath.moveTo(centerX - wing, tip - wing)
        fallbackPath.lineTo(centerX, tip)
        fallbackPath.lineTo(centerX + wing, tip - wing)
        canvas.drawPath(fallbackPath, fallbackPaint)
        val tray = RectF(
            bounds.left + bounds.width() * 0.06f,
            bounds.top + bounds.height() * 0.42f,
            bounds.right - bounds.width() * 0.06f,
            bounds.bottom - bounds.height() * 0.06f,
        )
        canvas.drawRoundRect(tray, tileSize * 0.08f, tileSize * 0.08f, fallbackPaint)
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
