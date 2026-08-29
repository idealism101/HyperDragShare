package com.leaf.hyperdragshare.codex

/** Applies the documented editable/text/image selection priority at a point. */
object AccessibilityCandidateSelector {
    @JvmStatic
    fun select(buckets: AccessibilityNodeClassifier.Buckets?, x: Float, y: Float): Selection? {
        if (buckets == null) {
            return null
        }
        var hit = firstContaining(buckets.webEditable, x, y)
        if (hit != null) return Selection(hit)
        hit = firstContaining(buckets.nativeEditable, x, y)
        if (hit != null) return Selection(hit)
        hit = firstContaining(buckets.nativeText, x, y)
        if (hit != null) return Selection(hit)

        val webText = firstContaining(buckets.webText, x, y)
        val webImage = firstContaining(buckets.webNonText, x, y)
        if (webText != null && webImage != null) {
            return Selection(if (webImage.isInside(webText)) webImage else webText)
        }
        if (webText != null) return Selection(webText)
        if (webImage != null) return Selection(webImage)

        hit = firstContaining(buckets.nativeNonText, x, y)
        return if (hit == null) null else Selection(hit)
    }

    private fun firstContaining(
        candidates: List<AccessibilityCandidate>?,
        x: Float,
        y: Float,
    ): AccessibilityCandidate? {
        if (candidates == null) {
            return null
        }
        for (candidate in candidates) {
            if (candidate.contains(x, y)) {
                return candidate
            }
        }
        return null
    }

    class Selection(@JvmField val candidate: AccessibilityCandidate?) {
        fun isImage(): Boolean =
            candidate != null && candidate.kind == AccessibilityCandidate.Kind.IMAGE_REGION
    }
}
