package com.leaf.hyperdragshare.codex

import android.graphics.Rect

/** Immutable data copied from AccessibilityNodeInfo before pure classification. */
class AccessibilityNodeSnapshot private constructor(builder: Builder) {
    val bounds: Rect = builder.bounds?.let { Rect(it) } ?: Rect()

    val packageName: String? = builder.packageName

    val className: String? = builder.className

    val viewId: String? = builder.viewId

    val text: String? = builder.text

    val contentDescription: String? = builder.contentDescription

    val visible: Boolean = builder.visible

    val editable: Boolean = builder.editable

    val password: Boolean = builder.password

    val clickable: Boolean = builder.clickable

    val longClickable: Boolean = builder.longClickable

    val important: Boolean = builder.important

    val leaf: Boolean = builder.leaf

    val insideWebView: Boolean = builder.insideWebView

    val depth: Int = builder.depth

    val windowLayer: Int = builder.windowLayer

    val traversalOrder: Int = builder.traversalOrder

    class Builder {
        internal var bounds: Rect? = null
        internal var packageName: String? = null
        internal var className: String? = null
        internal var viewId: String? = null
        internal var text: String? = null
        internal var contentDescription: String? = null
        internal var visible = true
        internal var editable = false
        internal var password = false
        internal var clickable = false
        internal var longClickable = false
        internal var important = true
        internal var leaf = true
        internal var insideWebView = false
        internal var depth = 0
        internal var windowLayer = 0
        internal var traversalOrder = 0

        fun bounds(value: Rect?): Builder { bounds = value; return this }
        fun packageName(value: String?): Builder { packageName = value; return this }
        fun className(value: String?): Builder { className = value; return this }
        fun viewId(value: String?): Builder { viewId = value; return this }
        fun text(value: String?): Builder { text = value; return this }
        fun contentDescription(value: String?): Builder { contentDescription = value; return this }
        fun visible(value: Boolean): Builder { visible = value; return this }
        fun editable(value: Boolean): Builder { editable = value; return this }
        fun password(value: Boolean): Builder { password = value; return this }
        fun clickable(value: Boolean): Builder { clickable = value; return this }
        fun longClickable(value: Boolean): Builder { longClickable = value; return this }
        fun important(value: Boolean): Builder { important = value; return this }
        fun leaf(value: Boolean): Builder { leaf = value; return this }
        fun insideWebView(value: Boolean): Builder { insideWebView = value; return this }
        fun depth(value: Int): Builder { depth = value; return this }
        fun windowLayer(value: Int): Builder { windowLayer = value; return this }
        fun traversalOrder(value: Int): Builder { traversalOrder = value; return this }
        fun build(): AccessibilityNodeSnapshot = AccessibilityNodeSnapshot(this)
    }
}
