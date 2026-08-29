package com.leaf.hyperdragshare.codex

import android.graphics.Rect

/** Immutable data copied from AccessibilityNodeInfo before pure classification. */
class AccessibilityNodeSnapshot private constructor(builder: Builder) {
    @JvmField
    val bounds: Rect = builder.bounds?.let { Rect(it) } ?: Rect()

    @JvmField
    val packageName: String? = builder.packageName

    @JvmField
    val className: String? = builder.className

    @JvmField
    val viewId: String? = builder.viewId

    @JvmField
    val text: String? = builder.text

    @JvmField
    val contentDescription: String? = builder.contentDescription

    @JvmField
    val visible: Boolean = builder.visible

    @JvmField
    val editable: Boolean = builder.editable

    @JvmField
    val password: Boolean = builder.password

    @JvmField
    val clickable: Boolean = builder.clickable

    @JvmField
    val longClickable: Boolean = builder.longClickable

    @JvmField
    val important: Boolean = builder.important

    @JvmField
    val leaf: Boolean = builder.leaf

    @JvmField
    val insideWebView: Boolean = builder.insideWebView

    @JvmField
    val depth: Int = builder.depth

    @JvmField
    val windowLayer: Int = builder.windowLayer

    @JvmField
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
