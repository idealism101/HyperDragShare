package com.leaf.hyperdragshare.codex

/** Minimal logging adapter retained by the imported BigBang chip implementation. */
internal object LogUtils {
    fun d(tag: String?, message: String?) {
        DragShareLog.d(tag, message)
    }
}
