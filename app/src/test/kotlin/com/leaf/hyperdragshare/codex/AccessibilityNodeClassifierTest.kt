package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccessibilityNodeClassifierTest {
    @Test
    fun textAndImageFollowDocumentedPriority() {
        val text = snapshot(Rect(0, 0, 200, 100))
            .className("android.widget.TextView")
            .text("Hello")
            .build()
        val image = snapshot(Rect(20, 20, 120, 90))
            .className("android.widget.ImageView")
            .contentDescription("photo")
            .build()
        val buckets = AccessibilityNodeClassifier(1f, 1080, 2400)
            .classify(listOf(text, image))

        val selection = AccessibilityCandidateSelector.select(buckets, 30f, 30f)
        assertNotNull(selection)
        val candidate = selection?.candidate
        assertNotNull(candidate)
        assertEquals(AccessibilityCandidate.Kind.TEXT, candidate?.kind)
    }

    @Test
    fun strongImageUsesImageRegionEvenWithDescription() {
        val image = snapshot(Rect(0, 0, 100, 100))
            .className("android.widget.ImageView")
            .contentDescription("A scenic photo")
            .build()
        val selection = AccessibilityCandidateSelector.select(
            AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(listOf(image)),
            10f,
            10f,
        )

        assertNotNull(selection)
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection?.candidate?.kind)
    }

    @Test
    fun genericImageRoleWithoutTextIsAnImageRegion() {
        val image = snapshot(Rect(0, 0, 100, 100))
            .className("android.view.View")
            .contentDescription("image")
            .build()

        val selection = AccessibilityCandidateSelector.select(
            AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(listOf(image)),
            10f,
            10f,
        )

        assertNotNull(selection)
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection?.candidate?.kind)
    }

    @Test
    fun smallEmptyLeafAndPasswordAreRejected() {
        val tiny = snapshot(Rect(0, 0, 20, 20))
            .className("android.view.View")
            .build()
        val password = snapshot(Rect(30, 0, 200, 80))
            .className("android.widget.EditText")
            .text("secret")
            .password(true)
            .build()
        val selection = AccessibilityCandidateSelector.select(
            AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(listOf(tiny, password)),
            10f,
            10f,
        )

        assertNull(selection)
    }

    @Test
    fun sameTextParentIsReplacedBySpecificChild() {
        val parent = snapshot(Rect(0, 0, 200, 200))
            .className("android.widget.TextView")
            .text("same")
            .leaf(false)
            .depth(1)
            .traversalOrder(1)
            .build()
        val child = snapshot(Rect(20, 20, 100, 60))
            .className("android.widget.TextView")
            .text("same")
            .leaf(true)
            .depth(2)
            .traversalOrder(2)
            .build()
        val buckets = AccessibilityNodeClassifier(1f, 1080, 2400)
            .classify(listOf(parent, child))

        assertEquals(1, buckets.nativeText.size)
        assertEquals(Rect(20, 20, 100, 60), buckets.nativeText[0].bounds)
    }

    private companion object {
        private fun snapshot(bounds: Rect): AccessibilityNodeSnapshot.Builder =
            AccessibilityNodeSnapshot.Builder()
                .bounds(bounds)
                .visible(true)
                .leaf(true)
                .traversalOrder(1)
    }
}
