package com.leaf.hyperdragshare.codex

import android.graphics.Rect

/** Immutable data copied from AccessibilityNodeInfo before pure classification. */
internal class AccessibilityNodeSnapshot(
    bounds: Rect? = null,
    val packageName: String? = null,
    val className: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val visible: Boolean = true,
    val editable: Boolean = false,
    val password: Boolean = false,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val important: Boolean = true,
    val leaf: Boolean = true,
    val insideWebView: Boolean = false,
    val depth: Int = 0,
    val windowLayer: Int = 0,
    val traversalOrder: Int = 0,
) {
    /** Copied because the traversal hands the same Rect instance to every node it visits. */
    val bounds: Rect = bounds?.let { Rect(it) } ?: Rect()
}
