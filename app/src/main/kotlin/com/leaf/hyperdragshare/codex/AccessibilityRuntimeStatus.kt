package com.leaf.hyperdragshare.codex

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Process-local status exposed to the settings UI. The service shares the app process. */
object AccessibilityRuntimeStatus {
    @Volatile
    private var connected = false

    @Volatile
    private var rootInputReady = false

    @JvmStatic
    fun setConnected(value: Boolean) {
        connected = value
        if (!value) {
            rootInputReady = false
        }
    }

    @JvmStatic
    fun setRootInputReady(value: Boolean) {
        rootInputReady = value && connected
    }

    @JvmStatic
    fun isConnected(): Boolean = connected

    @JvmStatic
    fun isRootInputReady(): Boolean = rootInputReady

    @JvmStatic
    fun isServiceEnabled(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        try {
            val manager = context.getSystemService(
                Context.ACCESSIBILITY_SERVICE,
            ) as AccessibilityManager?
            if (manager == null) {
                return false
            }
            val services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            for (service in services) {
                if (service?.resolveInfo?.serviceInfo == null) {
                    continue
                }
                val packageName = service.resolveInfo.serviceInfo.packageName
                val className = service.resolveInfo.serviceInfo.name
                if (context.packageName == packageName &&
                    (
                        DragShareAccessibilityService::class.java.name == className ||
                            className.endsWith(".DragShareAccessibilityService")
                        )
                ) {
                    return true
                }
            }
        } catch (_: Throwable) {
            // Settings remains usable on ROMs that restrict this lookup.
        }
        return false
    }
}
