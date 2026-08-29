package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {
    @Test
    fun menuTriggersAndActivationMovementRespectDirection() {
        assertFalse(GestureMath.shouldShowMenu(799f, 800))
        assertTrue(GestureMath.shouldShowMenu(800f, 800))
        assertTrue(GestureMath.shouldShowMenu(1200f, 800))

        assertFalse(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 500f, 815f, 16f,
            ),
        )
        assertTrue(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 500f, 816f, 16f,
            ),
        )
        assertFalse(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 540f, 800f, 16f,
            ),
        )
        assertTrue(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_TOP,
                500f, 800f, 500f, 784f, 16f,
            ),
        )
        assertTrue(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_LEFT,
                500f, 800f, 484f, 800f, 16f,
            ),
        )
        assertTrue(
            GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_RIGHT,
                500f, 800f, 516f, 800f, 16f,
            ),
        )
    }

    @Test
    fun edgeZonesScrollInExpectedDirection() {
        assertEquals(-1, GestureMath.edgeScrollDirection(0f, 1080, 56))
        assertEquals(-1, GestureMath.edgeScrollDirection(56f, 1080, 56))
        assertEquals(0, GestureMath.edgeScrollDirection(540f, 1080, 56))
        assertEquals(1, GestureMath.edgeScrollDirection(1024f, 1080, 56))
        assertEquals(1, GestureMath.edgeScrollDirection(1080f, 1080, 56))
    }

    @Test
    fun edgeScrollSpeedStartsSoftAndReachesMaximumJustInsideThePhysicalEdge() {
        assertEquals(0f, GestureMath.edgeScrollSpeedMultiplier(540f, 1080, 56, 8), 0.001f)
        assertEquals(0.2f, GestureMath.edgeScrollSpeedMultiplier(56f, 1080, 56, 8), 0.001f)
        val leftBeforeFullSpeed = GestureMath.edgeScrollSpeedMultiplier(9f, 1080, 56, 8)
        assertTrue(leftBeforeFullSpeed > 0.2f)
        assertTrue(leftBeforeFullSpeed < 1f)
        assertEquals(1f, GestureMath.edgeScrollSpeedMultiplier(8f, 1080, 56, 8), 0.001f)
        assertEquals(1f, GestureMath.edgeScrollSpeedMultiplier(0f, 1080, 56, 8), 0.001f)

        val rightEntry = GestureMath.edgeScrollSpeedMultiplier(1024f, 1080, 56, 8)
        val rightBeforeFullSpeed = GestureMath.edgeScrollSpeedMultiplier(1070f, 1080, 56, 8)
        assertEquals(0.2f, rightEntry, 0.001f)
        assertTrue(rightBeforeFullSpeed > rightEntry)
        assertTrue(rightBeforeFullSpeed < 1f)
        assertEquals(1f, GestureMath.edgeScrollSpeedMultiplier(1071f, 1080, 56, 8), 0.001f)
        assertEquals(1f, GestureMath.edgeScrollSpeedMultiplier(1079f, 1080, 56, 8), 0.001f)
    }

    @Test
    fun clampKeepsCoordinatesWithinViewport() {
        assertEquals(8, GestureMath.clamp(-10, 8, 100))
        assertEquals(40, GestureMath.clamp(40, 8, 100))
        assertEquals(100, GestureMath.clamp(120, 8, 100))
    }

    @Test
    fun previewTracksPointerAndClampsAtScreenEdges() {
        assertEquals(8, GestureMath.previewLeft(20f, 100, 1080, 8))
        assertEquals(490, GestureMath.previewLeft(540f, 100, 1080, 8))
        assertEquals(972, GestureMath.previewLeft(1070f, 100, 1080, 8))

        assertEquals(40, GestureMath.previewTop(20f, 120, 20, 40, 1800))
        assertEquals(860, GestureMath.previewTop(1000f, 120, 20, 40, 1800))
        assertEquals(1800, GestureMath.previewTop(2100f, 120, 20, 40, 1800))
    }

    @Test
    fun portalGlowProgressTracksDownwardTravel() {
        assertEquals(0f, GestureMath.dragPullProgress(300f, 400f, 900f), 0.001f)
        assertEquals(0.5f, GestureMath.dragPullProgress(650f, 400f, 900f), 0.001f)
        assertEquals(1f, GestureMath.dragPullProgress(1000f, 400f, 900f), 0.001f)
    }

    @Test
    fun portalItemsScaleByDistanceFromPointer() {
        assertEquals(1.23f, GestureMath.portalItemScale(500f, 500f, 80f), 0.001f)
        assertEquals(1.10f, GestureMath.portalItemScale(580f, 500f, 80f), 0.001f)
        assertEquals(1f, GestureMath.portalItemScale(700f, 500f, 80f), 0.001f)
    }

    @Test
    fun nearHandMenuUsesTheLowerSideOfTheDevice() {
        assertTrue(GestureMath.nearHandMenuOnRight(0.2f))
        assertFalse(GestureMath.nearHandMenuOnRight(-0.2f))
    }

    @Test
    fun rawTouchRangeMapsToPhysicalPixels() {
        assertArrayEquals(
            floatArrayOf(1219f, 2655f),
            GestureMath.mapRawPoint(
                121999f, 265599f, 121999, 265599, 1220, 2656, 0,
            ),
            0.01f,
        )
        assertArrayEquals(
            floatArrayOf(610f, 1328f),
            GestureMath.mapRawPoint(
                61050f, 132850f, 121999, 265599, 1220, 2656, 0,
            ),
            1f,
        )
    }

    @Test
    fun rawTouchRangeAccountsForRotation() {
        assertArrayEquals(
            floatArrayOf(2655f, 0f),
            GestureMath.mapRawPoint(
                121999f, 265599f, 121999, 265599, 2656, 1220, 1,
            ),
            0.01f,
        )
        assertArrayEquals(
            floatArrayOf(0f, 1219f),
            GestureMath.mapRawPoint(
                121999f, 265599f, 121999, 265599, 2656, 1220, 3,
            ),
            0.01f,
        )
    }
}
