package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleMenuGeometryTest {
    @Test
    fun onlyLeftAndRightEdgesCanTrigger() {
        assertEquals(
            CircleMenuGeometry.EDGE_LEFT,
            CircleMenuGeometry.nearestEdge(20f, 500f, 1080, 2000, 76f),
        )
        assertEquals(
            CircleMenuGeometry.EDGE_RIGHT,
            CircleMenuGeometry.nearestEdge(1060f, 500f, 1080, 2000, 76f),
        )
        assertEquals(
            CircleMenuGeometry.EDGE_NONE,
            CircleMenuGeometry.nearestEdge(500f, 20f, 1080, 2000, 76f),
        )
        assertEquals(
            CircleMenuGeometry.EDGE_NONE,
            CircleMenuGeometry.nearestEdge(500f, 1980f, 1080, 2000, 76f),
        )
        assertEquals(
            CircleMenuGeometry.EDGE_NONE,
            CircleMenuGeometry.nearestEdge(20f, 20f, 1080, 2000, 76f),
        )
        assertEquals(
            CircleMenuGeometry.EDGE_NONE,
            CircleMenuGeometry.nearestEdge(500f, 1000f, 1080, 2000, 76f),
        )
    }

    @Test
    fun startAnglesMatchFourReferenceLayouts() {
        assertEquals(18, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 5))
        assertEquals(108, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_TOP, 5))
        assertEquals(198, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_RIGHT, 5))
        assertEquals(288, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 5))
        assertEquals(324, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 2))
        assertEquals(90, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 1))
        assertEquals(0, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 1))
    }

    @Test
    fun sideContainersAreSemicirclesClampedInsideInsets() {
        val left = CircleMenuGeometry.containerBounds(
            CircleMenuGeometry.EDGE_LEFT,
            20f,
            1080,
            2000,
            600,
            40,
            80,
        )
        assertEquals(0, left[0])
        assertEquals(40, left[1])
        assertEquals(300, left[2])
        assertEquals(640, left[3])

        val right = CircleMenuGeometry.containerBounds(
            CircleMenuGeometry.EDGE_RIGHT,
            1900f,
            1080,
            2000,
            600,
            40,
            80,
        )
        assertEquals(780, right[0])
        assertEquals(1320, right[1])
        assertEquals(1080, right[2])
        assertEquals(1920, right[3])
    }

    @Test
    fun itemCentersAdvanceAroundArc() {
        val first = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_LEFT, 0f, 700f, 600f, 220f, 0, 5,
        )
        val second = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_LEFT, 0f, 700f, 600f, 220f, 1, 5,
        )
        assertTrue(first[0] != second[0] || first[1] != second[1])
        assertTrue(first[0] >= 0 && first[0] <= 300)
        assertTrue(first[1] >= 700 && first[1] <= 1300)
    }

    @Test
    fun centerItemUsesScreenEdgeAsCircleCenter() {
        val leftCenter = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_LEFT, 0f, 700f, 600f, 220f, 2, 5,
        )
        assertEquals(220f, leftCenter[0], 0.01f)
        assertEquals(1000f, leftCenter[1], 0.01f)

        val rightCenter = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_RIGHT, 780f, 700f, 600f, 220f, 2, 5,
        )
        assertEquals(860f, rightCenter[0], 0.01f)
        assertEquals(1000f, rightCenter[1], 0.01f)
    }

    @Test
    fun displayOrderIsNaturalFromTopToBottom() {
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3, 4),
            CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_LEFT, 5),
        )
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3, 4),
            CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_RIGHT, 5),
        )
        assertArrayEquals(
            intArrayOf(0, 1, 2),
            CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_LEFT, 3),
        )

        val leftFirst = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_LEFT, 0f, 700f, 600f, 220f, 0, 5,
        )
        val leftLast = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_LEFT, 0f, 700f, 600f, 220f, 4, 5,
        )
        val rightFirst = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_RIGHT, 780f, 700f, 600f, 220f, 0, 5,
        )
        val rightLast = CircleMenuGeometry.itemCenterValues(
            CircleMenuGeometry.EDGE_RIGHT, 780f, 700f, 600f, 220f, 4, 5,
        )
        assertTrue(leftFirst[1] < leftLast[1])
        assertTrue(rightFirst[1] < rightLast[1])
    }
}
