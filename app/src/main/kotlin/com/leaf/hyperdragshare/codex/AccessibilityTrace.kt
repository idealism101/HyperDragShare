package com.leaf.hyperdragshare.codex

import android.content.Context

/** Accessibility trace routed through the configured shared diagnostic logger. */
object AccessibilityTrace {
    fun reset(context: Context?) {
        DragShareLog.d("DragShare/Accessibility", "accessibility trace reset")
    }

    fun record(context: Context?, message: String?) {
        if (message != null) {
            DragShareLog.i("DragShare/Accessibility", message)
        }
    }
}
