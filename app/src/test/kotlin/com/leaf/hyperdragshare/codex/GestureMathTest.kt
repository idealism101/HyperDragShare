package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `GestureMath` 只保留 evdev 原始坐标 → 屏幕坐标的旋转映射（拖拽样式相关的菜单判定、
 * 边缘滚动、近手方向等函数已随样式删除一并移除）。
 *
 * 统一输入：raw=(250,100)/max=(1000,500) → norm=(0.25,0.2)；屏幕 1001×501 → width=1000,height=500。
 */
class GestureMathTest {
    @Test
    fun rotationZeroKeepsNaturalOrientation() {
        val mapped = mapWith(rotation = 0)

        // (normX*width, normY*height)
        assertArrayEquals(floatArrayOf(250f, 100f), mapped, 0.001f)
    }

    @Test
    fun rotationOneSwapsAxes() {
        val mapped = mapWith(rotation = 1)

        // (normY*width, (1-normX)*height)
        assertArrayEquals(floatArrayOf(200f, 375f), mapped, 0.001f)
    }

    @Test
    fun rotationTwoMirrorsBothAxes() {
        val mapped = mapWith(rotation = 2)

        // ((1-normX)*width, (1-normY)*height)
        assertArrayEquals(floatArrayOf(750f, 400f), mapped, 0.001f)
    }

    @Test
    fun rotationThreeMirrorsAndSwapsAxes() {
        val mapped = mapWith(rotation = 3)

        // ((1-normY)*width, normX*height)
        assertArrayEquals(floatArrayOf(800f, 125f), mapped, 0.001f)
    }

    private fun mapWith(rotation: Int): FloatArray = GestureMath.mapRawPoint(
        rawX = 250f,
        rawY = 100f,
        rawMaxX = 1000,
        rawMaxY = 500,
        screenWidth = 1001,
        screenHeight = 501,
        rotation = rotation,
    )

    @Test
    fun clamp01AndClampBoundValues() {
        assertEquals(0f, GestureMath.clamp01(-0.5f), 0.0001f)
        assertEquals(0.5f, GestureMath.clamp01(0.5f), 0.0001f)
        assertEquals(1f, GestureMath.clamp01(1.5f), 0.0001f)

        assertEquals(10, GestureMath.clamp(5, 10, 20))
        assertEquals(20, GestureMath.clamp(25, 10, 20))
        assertEquals(15, GestureMath.clamp(15, 10, 20))
    }
}
