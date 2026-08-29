package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import kotlin.math.max

/** Candidate selected from an immutable accessibility tree snapshot. */
class AccessibilityCandidate(
    val kind: Kind,
    snapshot: AccessibilityNodeSnapshot,
    val text: String?,
    val strongImage: Boolean,
) {
    enum class Kind {
        TEXT,
        IMAGE_REGION,
    }

    val bounds: Rect = Rect(snapshot.bounds)

    val sourcePackage: String? = snapshot.packageName

    val editable: Boolean = snapshot.editable

    val insideWebView: Boolean = snapshot.insideWebView

    val leaf: Boolean = snapshot.leaf

    val depth: Int = snapshot.depth

    val traversalOrder: Int = snapshot.traversalOrder

    fun contains(x: Float, y: Float): Boolean =
        x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom

    fun area(): Long = max(0L, bounds.width().toLong() * bounds.height().toLong())

    fun isInside(other: AccessibilityCandidate?): Boolean =
        other != null && other.bounds.contains(bounds)
}
