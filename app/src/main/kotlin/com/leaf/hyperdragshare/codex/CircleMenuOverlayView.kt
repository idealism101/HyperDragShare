package com.leaf.hyperdragshare.codex

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Left/right edge semicircle share menu based on the Oplus ROM circlemenuview.
 *
 * The window is intentionally passive. It is added with FLAG_NOT_TOUCHABLE and
 * the controller feeds it the root-evdev coordinates, just like the other
 * DragShare overlays.
 */
@SuppressLint("ViewConstructor")
class CircleMenuOverlayView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val topInset: Int,
    private val bottomInset: Int,
    dark: Boolean,
    private val accentColor: Int,
    private val textColor: Int,
    private val selectedColor: Int,
    private val selectedStrokeColor: Int,
    iconOpacityPercent: Int,
) : FrameLayout(context) {

    private val containerSize: Int
    private val itemSize: Int
    private val radius: Float
    private val panelColor: Int = if (dark) 0xE91E242B.toInt() else 0xE9F7FAFC.toInt()
    private val panelStrokeColor: Int = if (dark) 0x665E6D78 else 0x553E5662
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcColors: IntArray = intArrayOf(0x00FFFFFF, accentColor, 0x00FFFFFF)
    private val arcPositions = floatArrayOf(0f, 0.5f, 1f)
    private val targets: MutableList<ShareTarget> = ArrayList()
    private val allTargets: MutableList<ShareTarget> = ArrayList()
    private val itemViews: MutableList<View> = ArrayList()
    private val arcPath = Path()
    private var arcShader: Shader? = null

    private var edge = CircleMenuGeometry.EDGE_NONE
    private var pointerX = 0f
    private var pointerY = 0f
    private var edgeProgress = 0f
    private var expansionProgress = 0f
    private var selectedIndex = -1
    private var expanded = false
    private var expansionAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var pulse = 0f
    private var panelRect = Rect()
    private var emptyView: View? = null
    private var windowStart = 0
    private val contentAlpha: Float = max(0f, min(1f, iconOpacityPercent / 100f))

    init {
        val availableHeight = max(1, screenHeight - topInset - bottomInset)
        val maximumContainer = max(1, min(screenWidth, availableHeight))
        containerSize = min(dp(CONTAINER_SIZE_DP), maximumContainer)
        itemSize = min(dp(ITEM_SIZE_DP), max(1, containerSize / 4))
        radius = min(dp(RADIUS_DP).toFloat(), containerSize * 0.38f)
        scrimPaint.color = if (dark) 0x16000000 else 0x0C0B1820
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = false
        }
        visibility = View.INVISIBLE
    }

    fun setTargets(shareTargets: List<ShareTarget?>?) {
        allTargets.clear()
        if (shareTargets != null) {
            for (target in shareTargets) {
                if (target != null) {
                    allTargets.add(target)
                }
            }
        }
        windowStart = 0
        targets.clear()
        clearItemViews()
    }

    private fun rebuildVisibleItems() {
        targets.clear()
        val visibleCount = min(MAX_ITEMS, max(0, allTargets.size - windowStart))
        for (index in 0 until visibleCount) {
            targets.add(allTargets[windowStart + index])
        }
        clearItemViews()
        populateItemViews()
    }

    private fun clearItemViews() {
        for (item in itemViews) {
            item.animate().cancel()
            itemContent(item).animate().cancel()
        }
        removeAllViews()
        itemViews.clear()
        emptyView = null
    }

    private fun populateItemViews() {
        if (targets.isEmpty()) {
            val empty = TextView(context)
            empty.text = "没有可用的分享应用"
            empty.setTextColor(textColor)
            empty.textSize = 13f
            empty.gravity = Gravity.CENTER
            val params = FrameLayout.LayoutParams(dp(180), dp(48))
            params.leftMargin = 0
            params.topMargin = 0
            addView(empty, params)
            emptyView = empty
            return
        }
        for (target in targets) {
            val item = createItemView(target)
            addView(item, FrameLayout.LayoutParams(itemSize, itemSize))
            itemViews.add(item)
        }
        positionItems()
    }

    private fun createItemView(target: ShareTarget): View {
        val slot = FrameLayout(context)
        slot.clipChildren = false
        slot.clipToPadding = false
        slot.tag = target

        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.setPadding(dp(4), dp(4), dp(4), dp(2))
        item.background = itemBackground(target, false)

        val icon = ImageView(context)
        icon.setImageDrawable(iconFor(target))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.alpha = contentAlpha
        item.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))

        val label = TextView(context)
        label.text = target.label
        label.setTextColor(textColor)
        label.textSize = 11f
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.alpha = contentAlpha
        item.addView(
            label,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)),
        )
        slot.addView(
            item,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return slot
    }

    /** Scroll one item along the arc, matching the adapter's delayed edge scroll. */
    fun scrollWindow(direction: Int): Boolean {
        if (!expanded || allTargets.size <= MAX_ITEMS || direction == 0) {
            return false
        }
        val maximumStart = allTargets.size - MAX_ITEMS
        val nextStart = max(0, min(maximumStart, windowStart + direction))
        if (nextStart == windowStart) {
            return false
        }
        setSelectedTarget(null)
        animateScrollTo(nextStart, direction)
        return true
    }

    private fun animateScrollTo(nextStart: Int, direction: Int) {
        val previousViews = LinkedHashMap<ShareTarget, View>()
        var index = 0
        while (index < targets.size && index < itemViews.size) {
            previousViews[targets[index]] = itemViews[index]
            index++
        }
        windowStart = nextStart
        val nextTargets = ArrayList<ShareTarget>()
        val visibleCount = min(MAX_ITEMS, allTargets.size - windowStart)
        for (i in 0 until visibleCount) {
            nextTargets.add(allTargets[windowStart + i])
        }

        for (entry in previousViews.entries) {
            if (!nextTargets.contains(entry.key)) {
                val leaving = entry.value
                leaving.animate().cancel()
                leaving.animate()
                    .alpha(0f)
                    .scaleX(0.78f)
                    .scaleY(0.78f)
                    .setDuration(160L)
                    .withEndAction { removeView(leaving) }
                    .start()
            }
        }

        targets.clear()
        targets.addAll(nextTargets)
        itemViews.clear()
        for (i in targets.indices) {
            val target = targets[i]
            val existing = previousViews[target]
            val center = centerFor(i)
            val targetLeft = Math.round(center.x - itemSize / 2f)
            val targetTop = Math.round(center.y - itemSize / 2f)
            val item: View
            if (existing == null) {
                item = createItemView(target)
                val params = FrameLayout.LayoutParams(itemSize, itemSize)
                params.leftMargin = targetLeft
                params.topMargin = targetTop
                addView(item, params)
                item.translationX = 0f
                item.translationY = if (direction > 0) dp(52).toFloat() else -dp(52).toFloat()
                item.alpha = 0f
                item.scaleX = 0.78f
                item.scaleY = 0.78f
            } else {
                item = existing
                item.animate().cancel()
                val currentX = item.x
                val currentY = item.y
                val params = item.layoutParams as FrameLayout.LayoutParams
                params.width = itemSize
                params.height = itemSize
                params.leftMargin = targetLeft
                params.topMargin = targetTop
                item.layoutParams = params
                // Keep the item visually at its old arc point until the next
                // frame, then animate the relative offset to the new point.
                item.translationX = currentX - targetLeft
                item.translationY = currentY - targetTop
            }
            itemViews.add(item)
            item.animate().cancel()
            item.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(0.45f))
                .start()
        }
        updateArcShader()
        invalidate()
    }

    fun getTargets(): List<ShareTarget> = Collections.unmodifiableList(targets)

    fun getContainerSize(): Int = containerSize

    fun setEdgeProgress(edge: Int, progress: Float, x: Float, y: Float) {
        if (expanded) {
            return
        }
        this.edge = edge
        this.pointerX = x
        this.pointerY = y
        this.edgeProgress = CircleMenuGeometry.clamp01(progress)
        if (edge == CircleMenuGeometry.EDGE_NONE || this.edgeProgress <= 0f) {
            stopPulse()
        } else {
            visibility = View.VISIBLE
            startPulse()
        }
        invalidate()
    }

    fun expand(edge: Int, x: Float, y: Float) {
        if (edge != CircleMenuGeometry.EDGE_LEFT && edge != CircleMenuGeometry.EDGE_RIGHT) {
            return
        }
        this.edge = edge
        this.pointerX = x
        this.pointerY = y
        this.edgeProgress = 1f
        this.expanded = true
        this.selectedIndex = -1
        this.panelRect = CircleMenuGeometry.containerRect(
            edge,
            if (isVerticalEdge(edge)) y else x,
            screenWidth,
            screenHeight,
            containerSize,
            topInset,
            bottomInset,
        )
        rebuildVisibleItems()
        visibility = View.VISIBLE
        stopPulse()
        expansionAnimator?.cancel()
        expansionProgress = 0f
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 500L
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            expansionProgress = animation.animatedValue as Float
            invalidate()
        }
        expansionAnimator = animator
        animator.start()

        val spring = OvershootInterpolator(0.82f)
        for (index in itemViews.indices) {
            val item = itemViews[index]
            item.animate().cancel()
            item.alpha = 0f
            item.scaleX = 0.7f
            item.scaleY = 0.7f
            val travel = panelDepth() * 0.3f
            item.translationX = if (edge == CircleMenuGeometry.EDGE_LEFT) {
                -travel
            } else if (edge == CircleMenuGeometry.EDGE_RIGHT) {
                travel
            } else {
                0f
            }
            item.translationY = 0f
            item.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .translationX(0f).translationY(0f)
                .setStartDelay(min(120L, index * 28L))
                .setDuration(500L)
                .setInterpolator(spring)
                .start()
        }
    }

    fun updatePointer(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        invalidate()
    }

    fun hitTest(x: Float, y: Float): ShareTarget? {
        if (!expanded) {
            return null
        }
        for (index in targets.indices) {
            val center = centerFor(index)
            val scale = if (index == selectedIndex) 1.12f else 1f
            val half = itemSize * scale / 2f
            if (abs(x - center.x) <= half && abs(y - center.y) <= half) {
                return targets[index]
            }
        }
        return null
    }

    fun scrollDirectionForPointer(x: Float, y: Float): Int {
        if (!expanded || allTargets.size <= MAX_ITEMS || targets.isEmpty()) {
            return 0
        }
        val first = centerFor(0)
        val last = centerFor(targets.size - 1)
        val threshold = itemSize * 0.9f
        if (hypot((x - first.x).toDouble(), (y - first.y).toDouble()) <= threshold &&
            windowStart > 0
        ) {
            return -1
        }
        if (hypot((x - last.x).toDouble(), (y - last.y).toDouble()) <= threshold &&
            windowStart < allTargets.size - MAX_ITEMS
        ) {
            return 1
        }
        return 0
    }

    fun setSelectedTarget(target: ShareTarget?) {
        val next = if (target == null) -1 else targets.indexOf(target)
        if (next == selectedIndex) {
            return
        }
        selectedIndex = next
        for (index in itemViews.indices) {
            val slot = itemViews[index]
            val item = itemContent(slot)
            val selected = index == selectedIndex
            item.background = itemBackground(slot.tag as ShareTarget?, selected)
            item.animate().cancel()
            item.animate().scaleX(if (selected) 1.12f else 1f)
                .scaleY(if (selected) 1.12f else 1f)
                .setDuration(120L)
                .start()
        }
        invalidate()
    }

    fun getAvoidRect(): Rect {
        if (!expanded) {
            return Rect()
        }
        val padding = Math.round(containerSize * 0.16f) + dp(10)
        return Rect(
            max(0, panelRect.left - padding),
            max(topInset, panelRect.top - padding),
            min(screenWidth, panelRect.right + padding),
            min(screenHeight - bottomInset, panelRect.bottom + padding),
        )
    }

    fun containsExpandedRegion(x: Float, y: Float): Boolean {
        if (!expanded || !x.isFinite() || !y.isFinite()) {
            return false
        }
        val region = getAvoidRect()
        return x >= region.left && x <= region.right && y >= region.top && y <= region.bottom
    }

    fun collapse() {
        expansionAnimator?.cancel()
        expansionAnimator = null
        for (item in itemViews) {
            item.animate().cancel()
            itemContent(item).animate().cancel()
        }
        stopPulse()
        expanded = false
        selectedIndex = -1
        targets.clear()
        clearItemViews()
        edge = CircleMenuGeometry.EDGE_NONE
        edgeProgress = 0f
        visibility = View.INVISIBLE
    }

    fun isExpanded(): Boolean = expanded

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!expanded) {
            drawEdgeGlow(canvas)
            return
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val scale = 0.7f + 0.3f * expansionProgress
        val centerX = panelCenterX()
        val centerY = panelCenterY()
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        panelPaint.style = Paint.Style.FILL
        panelPaint.color = panelColor
        panelPaint.setShadowLayer(dp(18).toFloat(), 0f, dp(5).toFloat(), 0x66000000)
        canvas.drawCircle(centerX, centerY, panelDepth(), panelPaint)
        panelPaint.clearShadowLayer()
        panelPaint.style = Paint.Style.STROKE
        panelPaint.strokeWidth = dp(1).toFloat()
        panelPaint.color = panelStrokeColor
        canvas.drawCircle(centerX, centerY, panelDepth(), panelPaint)

        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeWidth = dp(ARC_STROKE_DP).toFloat()
        arcPaint.strokeCap = Paint.Cap.ROUND
        arcPaint.shader = arcShader
        arcPath.reset()
        val arcItemCount = max(1, targets.size)
        val sweep = max(36f, (arcItemCount - 1) * 36f)
        val start = if (edge == CircleMenuGeometry.EDGE_RIGHT) 180f - sweep / 2f else -sweep / 2f
        arcPath.addArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            start,
            sweep,
        )
        canvas.drawPath(arcPath, arcPaint)
        arcPaint.shader = null
        canvas.restore()
    }

    private fun drawEdgeGlow(canvas: Canvas) {
        if (edge == CircleMenuGeometry.EDGE_NONE || edgeProgress <= 0f) {
            return
        }
        val alpha = Math.round(155f * edgeProgress * (0.82f + 0.18f * pulse))
        val color = (accentColor and 0x00FFFFFF) or (alpha shl 24)
        val softColor = (accentColor and 0x00FFFFFF) or (Math.round(alpha * 0.42f) shl 24)
        if (edge != CircleMenuGeometry.EDGE_LEFT && edge != CircleMenuGeometry.EDGE_RIGHT) {
            return
        }
        val centerX = if (edge == CircleMenuGeometry.EDGE_LEFT) 0f else width.toFloat()
        val centerY = max(topInset.toFloat(), min((height - bottomInset).toFloat(), pointerY))
        val glowRadius = dp(156) * (0.55f + edgeProgress * 0.45f)
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            glowRadius,
            intArrayOf(color, softColor, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)
        glowPaint.shader = null
    }

    private fun positionItems() {
        if (panelRect.isEmpty) {
            panelRect = CircleMenuGeometry.containerRect(
                edge,
                if (isVerticalEdge(edge)) pointerY else pointerX,
                screenWidth,
                screenHeight,
                containerSize,
                topInset,
                bottomInset,
            )
        }
        updateArcShader()
        for (index in itemViews.indices) {
            val center = centerFor(index)
            var params = itemViews[index].layoutParams as FrameLayout.LayoutParams?
            if (params == null) {
                params = FrameLayout.LayoutParams(itemSize, itemSize)
            }
            params.width = itemSize
            params.height = itemSize
            params.leftMargin = Math.round(center.x - itemSize / 2f)
            params.topMargin = Math.round(center.y - itemSize / 2f)
            val item = itemViews[index]
            item.layoutParams = params
            item.translationX = 0f
            item.translationY = 0f
        }
        val empty = emptyView
        if (empty != null) {
            var params = empty.layoutParams as FrameLayout.LayoutParams?
            if (params == null) {
                params = FrameLayout.LayoutParams(dp(180), dp(48))
            }
            params.leftMargin = panelRect.centerX() - dp(90)
            params.topMargin = panelRect.centerY() - dp(24)
            empty.layoutParams = params
        }
    }

    private fun updateArcShader() {
        if (panelRect.isEmpty) {
            arcShader = null
            return
        }
        arcShader = SweepGradient(panelCenterX(), panelCenterY(), arcColors, arcPositions)
    }

    private fun centerFor(index: Int): PointF = CircleMenuGeometry.itemCenter(
        edge,
        panelRect.left.toFloat(),
        panelRect.top.toFloat(),
        containerSize.toFloat(),
        radius,
        index,
        max(1, targets.size),
    )

    private fun panelCenterX(): Float = if (edge == CircleMenuGeometry.EDGE_RIGHT) {
        panelRect.right.toFloat()
    } else {
        panelRect.left.toFloat()
    }

    private fun panelCenterY(): Float = panelRect.centerY().toFloat()

    private fun panelDepth(): Float = containerSize / 2f

    private fun isVerticalEdge(edge: Int): Boolean =
        edge == CircleMenuGeometry.EDGE_LEFT || edge == CircleMenuGeometry.EDGE_RIGHT

    @Suppress("UNUSED_PARAMETER")
    private fun itemBackground(target: ShareTarget?, selected: Boolean): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(if (selected) selectedColor else Color.TRANSPARENT)
        drawable.cornerRadius = itemSize / 2f
        if (selected) {
            drawable.setStroke(dp(1), selectedStrokeColor)
        }
        return drawable
    }

    private fun iconFor(target: ShareTarget): Drawable? =
        ShareTargetRepository.iconForDisplay(target)

    private fun itemContent(item: View): View {
        if (item is FrameLayout && item.childCount > 0) {
            return item.getChildAt(0)
        }
        return item
    }

    private fun startPulse() {
        val running = pulseAnimator
        if (running != null && running.isRunning) {
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 700L
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.addUpdateListener { animation ->
            pulse = animation.animatedValue as Float
            invalidate()
        }
        pulseAnimator = animator
        animator.start()
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulse = 0f
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    companion object {
        private const val MAX_ITEMS = 5
        const val CONTAINER_SIZE_DP = 300
        private const val ITEM_SIZE_DP = 68
        private const val RADIUS_DP = 112
        private const val ARC_STROKE_DP = 2
    }
}
