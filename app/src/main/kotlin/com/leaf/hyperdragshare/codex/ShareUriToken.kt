package com.leaf.hyperdragshare.codex

import java.util.UUID

internal object ShareUriToken {
    const val PNG_SUFFIX = ".png"
    const val JPEG_SUFFIX = ".jpg"

    fun fileName(token: String?, suffix: String? = PNG_SUFFIX): String {
        if (parse(token) == null) {
            throw IllegalArgumentException("Invalid share token")
        }
        if (PNG_SUFFIX != suffix && JPEG_SUFFIX != suffix) {
            throw IllegalArgumentException("Invalid image suffix")
        }
        return token + suffix
    }

    fun parse(pathSegment: String?): String? {
        if (pathSegment == null) {
            return null
        }
        var token = pathSegment
        if (pathSegment.endsWith(PNG_SUFFIX)) {
            token = pathSegment.substring(0, pathSegment.length - PNG_SUFFIX.length)
        } else if (pathSegment.endsWith(JPEG_SUFFIX)) {
            token = pathSegment.substring(0, pathSegment.length - JPEG_SUFFIX.length)
        }
        return try {
            UUID.fromString(token)
            token
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun suffix(pathSegment: String?): String? {
        if (parse(pathSegment) == null) {
            return null
        }
        if (pathSegment != null && pathSegment.endsWith(PNG_SUFFIX)) {
            return PNG_SUFFIX
        }
        // Versions before the explicit .jpg URI suffix used a bare UUID for JPEG files.
        return JPEG_SUFFIX
    }
}
