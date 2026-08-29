package com.leaf.hyperdragshare.codex

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object GestureMath {
    private const val EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER = 0.2f

    @JvmStatic
    fun shouldShowMenu(pointerY: Float, triggerTop: Int): Boolean = pointerY >= triggerTop

    @JvmStatic
    fun hasMovedTowardMenu(
        menuPosition: Int,
        startX: Float,
        startY: Float,
        pointerX: Float,
        pointerY: Float,
        minimumDistance: Float,
    ): Boolean {
        val distance = max(0f, minimumDistance)
        return when (menuPosition) {
            DragShareSettings.SIMPLE_MENU_POSITION_TOP -> startY - pointerY >= distance
            DragShareSettings.SIMPLE_MENU_POSITION_LEFT -> startX - pointerX >= distance
            DragShareSettings.SIMPLE_MENU_POSITION_RIGHT -> pointerX - startX >= distance
            // SIMPLE_MENU_POSITION_BOTTOM and every unknown position pull downwards.
            else -> pointerY - startY >= distance
        }
    }

    @JvmStatic
    fun edgeScrollDirection(pointerX: Float, viewportWidth: Int, edgeWidth: Int): Int {
        if (viewportWidth <= 0 || edgeWidth <= 0) {
            return 0
        }
        if (pointerX <= edgeWidth) {
            return -1
        }
        if (pointerX >= viewportWidth - edgeWidth) {
            return 1
        }
        return 0
    }

    @JvmStatic
    fun edgeScrollSpeedMultiplier(
        pointerCoordinate: Float,
        viewportSize: Int,
        edgeWidth: Int,
        fullSpeedInset: Int,
    ): Float {
        val direction = edgeScrollDirection(pointerCoordinate, viewportSize, edgeWidth)
        if (direction == 0) {
            return 0f
        }

        // Motion coordinates end at viewportSize - 1. Keep the physical outer edge at 1x.
        val maximumDistanceFromEdge = if (direction < 0) {
            edgeWidth.toFloat()
        } else {
            max(0, edgeWidth - 1).toFloat()
        }
        if (maximumDistanceFromEdge == 0f) {
            return 1f
        }
        val distanceFromEdge = if (direction < 0) {
            max(0f, pointerCoordinate)
        } else {
            max(0f, viewportSize - 1f - pointerCoordinate)
        }
        val clampedFullSpeedInset = min(
            maximumDistanceFromEdge,
            max(0, fullSpeedInset).toFloat(),
        )
        val accelerationDistance = maximumDistanceFromEdge - clampedFullSpeedInset
        if (accelerationDistance == 0f) {
            return 1f
        }
        val depth = clamp01(
            (maximumDistanceFromEdge - distanceFromEdge) / accelerationDistance,
        )
        return EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER +
            (1f - EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER) * depth * depth
    }

    @JvmStatic
    fun clamp(value: Int, minimum: Int, maximum: Int): Int {
        if (maximum < minimum) {
            return minimum
        }
        return max(minimum, min(maximum, value))
    }

    @JvmStatic
    fun previewLeft(pointerX: Float, previewWidth: Int, screenWidth: Int, margin: Int): Int = clamp(
        (pointerX - previewWidth / 2f).roundToInt(),
        margin,
        max(margin, screenWidth - previewWidth - margin),
    )

    @JvmStatic
    fun previewTop(
        pointerY: Float,
        previewHeight: Int,
        fingerOffset: Int,
        minimumTop: Int,
        maximumTop: Int,
    ): Int = clamp(
        (pointerY - previewHeight - fingerOffset).roundToInt(),
        minimumTop,
        max(minimumTop, maximumTop),
    )

    @JvmStatic
    fun dragPullProgress(pointerY: Float, startY: Float, endY: Float): Float {
        if (endY <= startY) {
            return if (pointerY >= endY) 1f else 0f
        }
        return clamp01((pointerY - startY) / (endY - startY))
    }

    @JvmStatic
    fun portalItemScale(pointerX: Float, itemCenterX: Float, itemWidth: Float): Float {
        val distanceInItems = abs(pointerX - itemCenterX) / max(1f, itemWidth)
        return max(1f, 1.23f - distanceInItems * 0.13f)
    }

    @JvmStatic
    fun nearHandMenuOnRight(tilt: Float): Boolean = tilt > 0f

    @JvmStatic
    fun mapRawPoint(
        rawX: Float,
        rawY: Float,
        rawMaxX: Int,
        rawMaxY: Int,
        screenWidth: Int,
        screenHeight: Int,
        rotation: Int,
    ): FloatArray {
        val normalizedX = clamp01(rawX / max(1, rawMaxX))
        val normalizedY = clamp01(rawY / max(1, rawMaxY))
        val width = max(1, screenWidth - 1).toFloat()
        val height = max(1, screenHeight - 1).toFloat()
        return when (rotation) {
            1 -> floatArrayOf(normalizedY * width, (1f - normalizedX) * height)
            2 -> floatArrayOf((1f - normalizedX) * width, (1f - normalizedY) * height)
            3 -> floatArrayOf((1f - normalizedY) * width, normalizedX * height)
            // Rotation 0 and any unexpected value keep the natural orientation.
            else -> floatArrayOf(normalizedX * width, normalizedY * height)
        }
    }

    @JvmStatic
    fun clamp01(value: Float): Float = max(0f, min(1f, value))
}
