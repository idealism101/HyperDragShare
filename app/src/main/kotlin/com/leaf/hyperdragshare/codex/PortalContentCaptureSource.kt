package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF

/** The only adapter allowed to read Taplus' private fields and methods. */
internal object PortalContentCaptureSource {
    fun capture(taplusService: Any?): CapturedContent? {
        if (taplusService == null) {
            return null
        }
        val serviceClass: Class<*> = taplusService.javaClass
        try {
            val textMode = PortalReflect.getStaticObjectField(serviceClass, "sIsTextMode")
            if (textMode == true) {
                val value = PortalReflect.getStaticObjectField(serviceClass, "sContent") as String?
                val text = CapturedContent.text(value, null, null)
                if (text != null) {
                    return text
                }
            }
        } catch (_: Throwable) {
            // Taplus internals can vary across versions; try the bitmap path.
        }
        return try {
            val value = PortalReflect.callStaticMethod(serviceClass, "getBitmap") as Bitmap?
            CapturedContent.image(value, null, null, false)
        } catch (_: Throwable) {
            null
        }
    }

    fun initialPoint(taplusService: Any?, fallbackX: Float, fallbackY: Float): PointF? {
        if (fallbackX >= 0f && fallbackY >= 0f) {
            return PointF(fallbackX, fallbackY)
        }
        if (taplusService != null) {
            try {
                val point = PortalReflect.callMethod(
                    taplusService,
                    "getFirstTouchPoint",
                ) as PointF?
                if (point != null) {
                    return PointF(point.x, point.y)
                }
            } catch (_: Throwable) {
                // Try the static injector point below.
            }
            try {
                val point = PortalReflect.callStaticMethod(
                    taplusService.javaClass,
                    "getInjectorPoint",
                ) as Point?
                if (point != null) {
                    return PointF(point.x.toFloat(), point.y.toFloat())
                }
            } catch (_: Throwable) {
                // The controller provides the final display-centered fallback.
            }
        }
        return null
    }
}
