package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.Rect

/** Immutable, source-neutral content handed to the common share UI. */
internal class CapturedContent private constructor(
    val kind: Kind,
    val text: String?,
    val bitmap: Bitmap?,
    val sourcePackage: String?,
    sourceBounds: Rect?,
    val bitmapOwnedByDragShare: Boolean,
) {
    enum class Kind {
        TEXT,
        IMAGE,
    }

    val sourceBounds: Rect? = if (sourceBounds == null) null else Rect(sourceBounds)

    fun isImage(): Boolean = kind == Kind.IMAGE

    fun mimeType(): String = if (isImage()) "image/png" else "text/plain"

    companion object {
        fun text(value: String?, sourcePackage: String?, sourceBounds: Rect?): CapturedContent? {
            if (value == null || value.trim().isEmpty()) {
                return null
            }
            return CapturedContent(Kind.TEXT, value, null, sourcePackage, sourceBounds, false)
        }

        fun image(
            value: Bitmap?,
            sourcePackage: String?,
            sourceBounds: Rect?,
            bitmapOwnedByDragShare: Boolean,
        ): CapturedContent? {
            if (value == null || value.isRecycled) {
                return null
            }
            return CapturedContent(
                Kind.IMAGE,
                null,
                value,
                sourcePackage,
                sourceBounds,
                bitmapOwnedByDragShare,
            )
        }
    }
}
