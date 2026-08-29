package com.leaf.hyperdragshare.codex

import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Geometry used by the left/right edge semicircle share menu. */
object CircleMenuGeometry {
    const val EDGE_NONE = 0
    const val EDGE_LEFT = 1
    const val EDGE_RIGHT = 2
    const val EDGE_TOP = 3
    const val EDGE_BOTTOM = 4

    fun nearestEdge(x: Float, y: Float, width: Int, height: Int, trigger: Float): Int {
        if (width <= 0 || height <= 0 || trigger <= 0 || !x.isFinite() || !y.isFinite()) {
            return EDGE_NONE
        }
        if (y < height * 0.1f || y > height * 0.9f) {
            return EDGE_NONE
        }
        val left = max(0f, x)
        val right = max(0f, width - x)
        val minimum = min(left, right)
        if (minimum > trigger) {
            return EDGE_NONE
        }
        return if (left <= right) EDGE_LEFT else EDGE_RIGHT
    }

    fun edgeProgress(x: Float, y: Float, width: Int, height: Int, softDistance: Float): Float {
        if (width <= 0 || height <= 0 || softDistance <= 0 || !x.isFinite() || !y.isFinite()) {
            return 0f
        }
        if (y < height * 0.1f || y > height * 0.9f) {
            return 0f
        }
        val distance = min(max(0f, x), max(0f, width - x))
        return clamp01(1f - distance / softDistance)
    }

    fun startAngle(edge: Int, itemCount: Int): Int {
        // These values intentionally use the same reference-angle convention
        // as CircleMenu*Layout.setStartAngle(). itemCenterValues converts it
        // to Canvas coordinates below.
        val count = max(1, min(5, itemCount))
        return when (edge) {
            EDGE_LEFT -> if (count == 1) 90 else if (count <= 3) 54 else 18
            EDGE_TOP -> if (count == 1) 180 else if (count <= 4) 144 else 108
            EDGE_RIGHT -> if (count == 1) 270 else if (count <= 4) 234 else 198
            EDGE_BOTTOM -> if (count == 1) 0 else if (count <= 3) 324 else 288
            else -> 288
        }
    }

    fun displayOrder(edge: Int, itemCount: Int): IntArray {
        val count = max(0, min(5, itemCount))
        val natural = IntArray(count)
        for (index in 0 until count) {
            natural[index] = index
        }
        return natural
    }

    fun itemCenter(
        edge: Int,
        originX: Float,
        originY: Float,
        containerSize: Float,
        radius: Float,
        index: Int,
        itemCount: Int,
    ): PointF {
        val values = itemCenterValues(
            edge, originX, originY, containerSize, radius, index, itemCount,
        )
        return PointF(values[0], values[1])
    }

    fun itemCenterValues(
        edge: Int,
        originX: Float,
        originY: Float,
        containerSize: Float,
        radius: Float,
        index: Int,
        itemCount: Int,
    ): FloatArray {
        // The reference layouts use a radius-sized depth and a diameter-sized
        // vertical span. Their circle center lies on the physical screen edge.
        val count = max(1, min(5, itemCount))
        val centeredOffset = (index - (count - 1) / 2f) * 36f
        val degrees = when (edge) {
            EDGE_LEFT -> centeredOffset
            EDGE_RIGHT -> 180f - centeredOffset
            else -> startAngle(edge, count) - 90f + index * 36f
        }
        val angle = Math.toRadians(degrees.toDouble()).toFloat()
        val depth = containerSize / 2f
        val centerX: Float
        val centerY: Float
        when (edge) {
            EDGE_RIGHT -> {
                centerX = originX + depth
                centerY = originY + containerSize / 2f
            }
            EDGE_TOP -> {
                centerX = originX + containerSize / 2f
                centerY = originY
            }
            EDGE_BOTTOM -> {
                centerX = originX + containerSize / 2f
                centerY = originY + depth
            }
            // EDGE_LEFT and every unknown edge keep the left-edge center.
            else -> {
                centerX = originX
                centerY = originY + containerSize / 2f
            }
        }
        return floatArrayOf(
            centerX + radius * cos(angle.toDouble()).toFloat(),
            centerY + radius * sin(angle.toDouble()).toFloat(),
        )
    }

    fun containerRect(
        edge: Int,
        pointer: Float,
        width: Int,
        height: Int,
        containerSize: Int,
        topInset: Int,
        bottomInset: Int,
    ): Rect {
        val bounds = containerBounds(
            edge, pointer, width, height, containerSize, topInset, bottomInset,
        )
        return Rect(bounds[0], bounds[1], bounds[2], bounds[3])
    }

    fun containerBounds(
        edge: Int,
        pointer: Float,
        width: Int,
        height: Int,
        containerSize: Int,
        topInset: Int,
        bottomInset: Int,
    ): IntArray {
        var left: Int
        var top: Int
        val depth = max(1, containerSize / 2)
        var right: Int
        var bottom: Int
        when (edge) {
            EDGE_RIGHT -> {
                left = width - depth
                top = (pointer - containerSize / 2f).roundToInt()
                right = width
                bottom = top + containerSize
            }
            EDGE_TOP -> {
                left = (pointer - containerSize / 2f).roundToInt()
                top = topInset
                right = left + containerSize
                bottom = top + depth
            }
            EDGE_BOTTOM -> {
                left = (pointer - containerSize / 2f).roundToInt()
                top = height - bottomInset - depth
                right = left + containerSize
                bottom = height - bottomInset
            }
            // EDGE_LEFT and every unknown edge keep the left-edge container.
            else -> {
                left = 0
                top = (pointer - containerSize / 2f).roundToInt()
                right = depth
                bottom = top + containerSize
            }
        }
        val minTop = max(0, topInset)
        val maxTop = max(minTop, height - bottomInset - containerSize)
        if (edge == EDGE_LEFT || edge == EDGE_RIGHT || edge == EDGE_NONE) {
            top = clamp(top, minTop, maxTop)
            bottom = top + containerSize
        } else {
            left = clamp(left, 0, max(0, width - containerSize))
            right = left + containerSize
        }
        return intArrayOf(left, top, right, bottom)
    }

    fun clamp01(value: Float): Float = max(0f, min(1f, value))

    private fun clamp(value: Int, minimum: Int, maximum: Int): Int =
        max(minimum, min(maximum, value))
}
