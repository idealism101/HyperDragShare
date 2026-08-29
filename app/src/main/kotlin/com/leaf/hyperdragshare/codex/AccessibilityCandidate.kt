package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import kotlin.math.max

/** Candidate selected from an immutable accessibility tree snapshot. */
class AccessibilityCandidate(
    @JvmField val kind: Kind,
    snapshot: AccessibilityNodeSnapshot,
    @JvmField val text: String?,
    @JvmField val strongImage: Boolean,
) {
    enum class Kind {
        TEXT,
        IMAGE_REGION,
    }

    @JvmField
    val bounds: Rect = Rect(snapshot.bounds)

    @JvmField
    val sourcePackage: String? = snapshot.packageName

    @JvmField
    val editable: Boolean = snapshot.editable

    @JvmField
    val insideWebView: Boolean = snapshot.insideWebView

    @JvmField
    val leaf: Boolean = snapshot.leaf

    @JvmField
    val depth: Int = snapshot.depth

    @JvmField
    val traversalOrder: Int = snapshot.traversalOrder

    fun contains(x: Float, y: Float): Boolean =
        x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom

    fun area(): Long = max(0L, bounds.width().toLong() * bounds.height().toLong())

    fun isInside(other: AccessibilityCandidate?): Boolean =
        other != null && other.bounds.contains(bounds)
}
