package com.leaf.hyperdragshare.codex

import android.view.WindowManager

/** Window type is a runtime capability, not a property of a share payload. */
@ConsistentCopyVisibility
internal data class OverlayWindowPolicy private constructor(
    val windowType: Int,
    val sourceName: String,
) {
    companion object {
        @Suppress("DEPRECATION")
        fun portal(): OverlayWindowPolicy {
            // Taplus is granted INTERNAL_SYSTEM_WINDOW. On the verified HyperOS device, the
            // same OPAQUE alpha=1 probe remains solid only on this window type, not TYPE_PHONE.
            return OverlayWindowPolicy(
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                "portal",
            )
        }

        fun accessibility(): OverlayWindowPolicy = OverlayWindowPolicy(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            "accessibility",
        )
    }
}
