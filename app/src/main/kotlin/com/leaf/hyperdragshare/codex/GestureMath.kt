package com.leaf.hyperdragshare.codex

import kotlin.math.max
import kotlin.math.min

/**
 * 手势/坐标换算工具。
 *
 * 原拖拽样式（跟手预览、边缘滚动、近手方向）已整体移除，这里只保留仍在用的部分：
 * evdev 原始坐标 → 屏幕坐标的旋转映射（`RootTouchSource` 用）。
 */
internal object GestureMath {
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

    fun clamp01(value: Float): Float = max(0f, min(1f, value))

    fun clamp(value: Int, minimum: Int, maximum: Int): Int {
        if (maximum < minimum) {
            return minimum
        }
        return max(minimum, min(maximum, value))
    }
}
