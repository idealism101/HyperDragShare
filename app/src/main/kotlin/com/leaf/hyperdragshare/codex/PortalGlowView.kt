package com.leaf.hyperdragshare.codex

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Full-screen, non-interactive bottom glow used by the portal-style overlay. */
internal class PortalGlowView(context: Context, private val dark: Boolean, bottomInset: Int) : View(context) {
    private val density: Float = context.resources.displayMetrics.density
    private val hazePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbonPath = Path()

    private var hazeShader: LinearGradient? = null
    private var horizonShader: LinearGradient? = null
    private var radialShader: RadialGradient? = null
    private var expansionAnimator: ValueAnimator? = null

    private var running = false
    private var animationStartUptime: Long = 0
    private var bottomInset: Int = max(0, bottomInset)
    private var targetPullProgress = 0f
    private var displayedPullProgress = 0f
    private var expansionProgress = 0f
    private var pointerFraction = 0.5f

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = false
        }
        ribbonPaint.style = Paint.Style.STROKE
        ribbonPaint.strokeCap = Paint.Cap.ROUND
    }

    fun start() {
        if (running) {
            return
        }
        running = true
        animationStartUptime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        val animator = expansionAnimator
        if (animator != null) {
            animator.cancel()
            expansionAnimator = null
        }
    }

    fun setPullProgress(progress: Float, pointerXFraction: Float) {
        targetPullProgress = GestureMath.clamp01(progress)
        pointerFraction = GestureMath.clamp01(pointerXFraction)
        if (!running) {
            invalidate()
        }
    }

    fun expandMenu() {
        if (expansionProgress >= 1f) {
            return
        }
        expansionAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(expansionProgress, 1f)
        expansionAnimator = animator
        animator.duration = 380L
        animator.interpolator = DecelerateInterpolator(1.7f)
        animator.addUpdateListener { updated ->
            expansionProgress = updated.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun collapseMenu() {
        if (expansionProgress <= 0f) {
            return
        }
        expansionAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(expansionProgress, 0f)
        expansionAnimator = animator
        animator.duration = 240L
        animator.interpolator = DecelerateInterpolator(1.4f)
        animator.addUpdateListener { updated ->
            expansionProgress = updated.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildShaders(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) {
            return
        }
        if (hazeShader == null || radialShader == null || horizonShader == null) {
            rebuildShaders(width, height)
        }

        val delta = targetPullProgress - displayedPullProgress
        if (abs(delta) < 0.002f) {
            displayedPullProgress = targetPullProgress
        } else {
            displayedPullProgress += delta * 0.18f
        }

        val pull = easeOut(displayedPullProgress)
        val strength = min(1f, 0.18f + pull * 0.68f + expansionProgress * 0.22f)
        val contentBottom = max(1f, (height - bottomInset).toFloat())
        val glowHeight = dp(92f) + dp(158f) * pull + dp(42f) * expansionProgress
        val horizontalShift = (pointerFraction - 0.5f) * width * 0.18f

        val save = canvas.save()
        canvas.clipRect(
            0f,
            max(0f, contentBottom - glowHeight),
            width.toFloat(),
            contentBottom,
        )

        hazePaint.shader = hazeShader
        hazePaint.alpha = Math.round(255f * strength)
        canvas.drawRect(
            0f,
            contentBottom - glowHeight,
            width.toFloat(),
            contentBottom,
            hazePaint,
        )

        val radialSave = canvas.save()
        canvas.translate(horizontalShift, 0f)
        radialPaint.shader = radialShader
        radialPaint.alpha = Math.round(255f * min(1f, strength * 1.12f))
        canvas.drawCircle(
            width / 2f,
            contentBottom + dp(24f),
            max(width * 0.72f, dp(280f)),
            radialPaint,
        )
        canvas.restoreToCount(radialSave)

        drawRibbons(canvas, contentBottom, pull, strength, horizontalShift)
        drawParticles(canvas, contentBottom, glowHeight, strength, horizontalShift)
        canvas.restoreToCount(save)

        horizonPaint.shader = horizonShader
        horizonPaint.alpha = Math.round(255f * min(1f, strength * 1.18f))
        canvas.drawRect(0f, contentBottom - dp(2.2f), width.toFloat(), contentBottom, horizonPaint)

        if (running) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun rebuildShaders(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        val contentBottom = max(1f, (height - bottomInset).toFloat())
        val blue = if (dark) 0xA84F78FF.toInt() else 0x985C8BFF.toInt()
        val cyan = if (dark) 0xB044DDF1.toInt() else 0xA249E4EE.toInt()
        val rose = if (dark) 0x8EC868FF.toInt() else 0x7EDB72D7
        hazeShader = LinearGradient(
            0f,
            contentBottom - dp(300f),
            0f,
            contentBottom,
            intArrayOf(Color.TRANSPARENT, 0x143F6DFF, blue, cyan),
            floatArrayOf(0f, 0.43f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        radialShader = RadialGradient(
            width / 2f,
            contentBottom + dp(24f),
            max(width * 0.72f, dp(280f)),
            intArrayOf(0xE4EAFDFF.toInt(), cyan, blue, rose, Color.TRANSPARENT),
            floatArrayOf(0f, 0.12f, 0.38f, 0.66f, 1f),
            Shader.TileMode.CLAMP,
        )
        horizonShader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(
                Color.TRANSPARENT,
                rose,
                0xFFE8FFFF.toInt(),
                cyan,
                blue,
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.16f, 0.43f, 0.62f, 0.84f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun drawRibbons(
        canvas: Canvas,
        contentBottom: Float,
        pull: Float,
        strength: Float,
        horizontalShift: Float,
    ) {
        val elapsed = max(0L, SystemClock.uptimeMillis() - animationStartUptime)
        val phase = (elapsed % 2400L) / 2400f
        val colors = intArrayOf(0xFF67E8F4.toInt(), 0xFF78A4FF.toInt(), 0xFFE178E8.toInt())
        for (index in colors.indices) {
            val lane = index - 1f
            val sway = sin((phase + index * 0.23f) * Math.PI * 2f).toFloat()
            val startX = width * (0.18f + index * 0.32f) + horizontalShift * 0.5f
            val peakY = contentBottom - dp(36f) - dp(104f) * pull - dp(13f) * sway
            ribbonPath.reset()
            ribbonPath.moveTo(startX - dp(150f), contentBottom + dp(4f))
            ribbonPath.cubicTo(
                startX - dp(82f), contentBottom - dp(18f),
                startX + lane * dp(52f), peakY,
                startX + dp(150f), contentBottom + dp(3f),
            )
            ribbonPaint.color = colors[index]
            ribbonPaint.strokeWidth = dp(1.2f + pull * 1.1f)
            ribbonPaint.alpha = Math.round(150f * strength)
            canvas.drawPath(ribbonPath, ribbonPaint)
        }
    }

    private fun drawParticles(
        canvas: Canvas,
        contentBottom: Float,
        glowHeight: Float,
        strength: Float,
        horizontalShift: Float,
    ) {
        val seconds = max(0L, SystemClock.uptimeMillis() - animationStartUptime) / 1000f
        particlePaint.color = 0xFFF1FFFF.toInt()
        for (index in 0 until PARTICLE_COUNT) {
            val cycle = (seconds * (0.18f + (index % 4) * 0.025f) + index * 0.137f) % 1f
            val seed = ((index * 47) % 101) / 100f
            var x = seed * width + horizontalShift * (0.2f + seed * 0.35f)
            x += sin(seconds * 1.7f + index).toFloat() * dp(6f)
            val y = contentBottom - cycle * glowHeight * (0.38f + seed * 0.54f)
            val fade = sin(cycle * Math.PI).toFloat()
            particlePaint.alpha = Math.round(180f * strength * max(0f, fade))
            canvas.drawCircle(x, y, dp(0.8f + (index % 3) * 0.35f), particlePaint)
        }
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val PARTICLE_COUNT = 18

        private fun easeOut(value: Float): Float {
            val inverse = 1f - GestureMath.clamp01(value)
            return 1f - inverse * inverse
        }
    }
}
