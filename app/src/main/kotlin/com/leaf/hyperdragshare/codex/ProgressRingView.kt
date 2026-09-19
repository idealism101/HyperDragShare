package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * 长按期间显示的「玻璃磨砂」进度环：底色一条极淡蓝引导环，按住越久磨砂段越长，
 * progress 由 0 涨到 1 即填满。填满后由控制器直接弹出菜单。纯视觉、不可触摸。
 */
internal class ProgressRingView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val stroke = (density * 12f).coerceAtLeast(6f)

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#4285F4")
        alpha = 45 // ~0.18 引导轨道
        this.strokeWidth = stroke
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#29B6F6") // 天蓝色，填满时明显
        alpha = 235 // ~0.92 高不透明，填充段清晰可见
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        alpha = 130 // ~0.51 高光
        strokeWidth = stroke * 0.18f
        strokeCap = Paint.Cap.ROUND
    }

    private var progress = 0f

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - stroke / 2f - 2f
        canvas.drawCircle(cx, cy, r, guidePaint)
        val sweep = progress * 360f
        if (sweep > 0.5f) {
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(oval, -90f, sweep, false, fillPaint)
            canvas.drawArc(oval, -90f, sweep, false, sheenPaint)
        }
    }
}
