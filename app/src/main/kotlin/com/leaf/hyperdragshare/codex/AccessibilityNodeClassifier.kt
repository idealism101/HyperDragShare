package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import java.util.ArrayList
import java.util.Comparator
import java.util.HashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Classifies snapshots without retaining AccessibilityNodeInfo instances. */
class AccessibilityNodeClassifier(density: Float, screenWidth: Int, screenHeight: Int) {
    private val minimumImageDimensionPx: Int = max(1, (20f * max(0.1f, density)).roundToInt())
    private val screenWidth: Int = max(1, screenWidth)
    private val screenHeight: Int = max(1, screenHeight)

    fun classify(snapshots: List<AccessibilityNodeSnapshot>?): Buckets {
        val buckets = Buckets()
        if (snapshots == null) {
            return buckets
        }
        for (snapshot in snapshots) {
            if (!isUsable(snapshot)) {
                continue
            }
            val strongImage = isStrongImage(snapshot)
            val text = if (strongImage) null else preferredText(snapshot)
            if (text != null) {
                val candidate = AccessibilityCandidate(
                    AccessibilityCandidate.Kind.TEXT,
                    snapshot,
                    text,
                    false,
                )
                if (snapshot.insideWebView) {
                    (if (snapshot.editable) buckets.webEditable else buckets.webText)
                        .add(candidate)
                } else {
                    (if (snapshot.editable) buckets.nativeEditable else buckets.nativeText)
                        .add(candidate)
                }
                continue
            }
            if (strongImage || isLowConfidenceImage(snapshot)) {
                val candidate = AccessibilityCandidate(
                    AccessibilityCandidate.Kind.IMAGE_REGION,
                    snapshot,
                    null,
                    strongImage,
                )
                if (snapshot.insideWebView) {
                    buckets.webNonText.add(candidate)
                } else {
                    buckets.nativeNonText.add(candidate)
                }
            }
        }
        cleanText(buckets.nativeEditable)
        cleanText(buckets.nativeText)
        cleanText(buckets.webEditable)
        cleanText(buckets.webText)
        cleanImages(buckets.nativeNonText)
        cleanImages(buckets.webNonText)
        rejectTextCoveredHeuristicImages(
            buckets.nativeNonText,
            buckets.nativeText,
            buckets.nativeEditable,
        )
        rejectTextCoveredHeuristicImages(buckets.webNonText, buckets.webText, buckets.webEditable)
        return buckets
    }

    private fun isUsable(node: AccessibilityNodeSnapshot?): Boolean =
        node != null &&
            node.visible &&
            !node.password &&
            node.bounds.width() > 0 &&
            node.bounds.height() > 0 &&
            node.bounds.right > 0 &&
            node.bounds.bottom > 0 &&
            node.bounds.left < screenWidth &&
            node.bounds.top < screenHeight

    private fun isStrongImage(node: AccessibilityNodeSnapshot): Boolean {
        val className = lower(node.className)
        if ("android.widget.imageview" == className ||
            "android.widget.image" == className ||
            (
                className.endsWith("imageview") &&
                    preferredText(node) == null &&
                    !isContainer(node)
                )
        ) {
            return true
        }
        val description = lower(usableText(node.contentDescription))
        return usableText(node.text) == null &&
            (
                "图片" == description ||
                    "图像" == description ||
                    "image" == description ||
                    "photo" == description
                )
    }

    private fun isLowConfidenceImage(node: AccessibilityNodeSnapshot): Boolean {
        if (preferredText(node) != null || node.editable || node.password || isContainer(node) ||
            isExplicitControl(node) || !node.leaf || coversMostOfScreen(node.bounds)
        ) {
            return false
        }
        return node.bounds.width() > minimumImageDimensionPx ||
            node.bounds.height() > minimumImageDimensionPx
    }

    private fun coversMostOfScreen(bounds: Rect): Boolean =
        bounds.width() >= (screenWidth * 0.9f).roundToInt() &&
            bounds.height() >= (screenHeight * 0.9f).roundToInt()

    class Buckets {
        val nativeText: MutableList<AccessibilityCandidate> = ArrayList()

        val nativeEditable: MutableList<AccessibilityCandidate> = ArrayList()

        val nativeNonText: MutableList<AccessibilityCandidate> = ArrayList()

        val webText: MutableList<AccessibilityCandidate> = ArrayList()

        val webEditable: MutableList<AccessibilityCandidate> = ArrayList()

        val webNonText: MutableList<AccessibilityCandidate> = ArrayList()

        fun candidateCount(): Int =
            nativeText.size + nativeEditable.size + nativeNonText.size +
                webText.size + webEditable.size + webNonText.size
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 100_000

        private fun preferredText(node: AccessibilityNodeSnapshot): String? {
            val text = usableText(node.text)
            return text ?: usableText(node.contentDescription)
        }

        private fun usableText(value: String?): String? {
            if (value == null || value.trim().isEmpty()) {
                return null
            }
            return if (value.length > MAX_TEXT_LENGTH) {
                value.substring(0, MAX_TEXT_LENGTH)
            } else {
                value
            }
        }

        private fun isContainer(node: AccessibilityNodeSnapshot): Boolean {
            val className = lower(node.className)
            return "android.widget.framelayout" == className ||
                "android.widget.relativelayout" == className ||
                "android.widget.linearlayout" == className ||
                "android.view.viewgroup" == className ||
                className.endsWith("viewgroup")
        }

        private fun isExplicitControl(node: AccessibilityNodeSnapshot): Boolean {
            val className = lower(node.className)
            return className.contains("button") ||
                className.contains("switch") ||
                className.contains("checkbox") ||
                className.contains("radiobutton") ||
                className.contains("seekbar") ||
                className.contains("progressbar")
        }

        private fun cleanText(candidates: MutableList<AccessibilityCandidate>) {
            val result = ArrayList<AccessibilityCandidate>()
            for (candidate in candidates) {
                var discarded = false
                for (index in result.indices) {
                    val existing = result[index]
                    if (existing.bounds == candidate.bounds) {
                        discarded = true
                        break
                    }
                    if (sameText(existing, candidate)) {
                        if (existing.bounds.contains(candidate.bounds)) {
                            result[index] = candidate
                            discarded = true
                            break
                        }
                        if (candidate.bounds.contains(existing.bounds)) {
                            discarded = true
                            break
                        }
                    }
                }
                if (!discarded) {
                    result.add(candidate)
                }
            }
            candidates.clear()
            candidates.addAll(result)
            sortSpecificFirst(candidates)
        }

        private fun sameText(
            first: AccessibilityCandidate,
            second: AccessibilityCandidate,
        ): Boolean {
            val firstText = first.text
            val secondText = second.text
            return firstText != null && secondText != null &&
                firstText.equals(secondText, ignoreCase = true)
        }

        private fun cleanImages(candidates: MutableList<AccessibilityCandidate>) {
            val seen = HashSet<Rect>()
            val result = ArrayList<AccessibilityCandidate>()
            for (candidate in candidates) {
                if (seen.add(Rect(candidate.bounds))) {
                    result.add(candidate)
                }
            }
            candidates.clear()
            candidates.addAll(result)
            sortSpecificFirst(candidates)
        }

        private fun sortSpecificFirst(candidates: MutableList<AccessibilityCandidate>) {
            candidates.sortWith(
                object : Comparator<AccessibilityCandidate> {
                    override fun compare(
                        first: AccessibilityCandidate,
                        second: AccessibilityCandidate,
                    ): Int {
                        val leaf = second.leaf.compareTo(first.leaf)
                        if (leaf != 0) return leaf
                        val area = first.area().compareTo(second.area())
                        if (area != 0) return area
                        val depth = second.depth.compareTo(first.depth)
                        if (depth != 0) return depth
                        return first.traversalOrder.compareTo(second.traversalOrder)
                    }
                },
            )
        }

        private fun rejectTextCoveredHeuristicImages(
            images: MutableList<AccessibilityCandidate>,
            text: List<AccessibilityCandidate>,
            editable: List<AccessibilityCandidate>,
        ) {
            val allText = ArrayList(text)
            allText.addAll(editable)
            images.removeAll { image ->
                !image.strongImage && coveredByText(image.bounds, allText) > image.area() / 4L
            }
        }

        private fun coveredByText(target: Rect, text: List<AccessibilityCandidate>): Long {
            val intersections = ArrayList<Rect>()
            for (candidate in text) {
                val intersection = Rect(target)
                if (intersection.intersect(candidate.bounds)) {
                    intersections.add(intersection)
                }
            }
            if (intersections.isEmpty()) {
                return 0L
            }
            val xValues = ArrayList<Int>()
            for (rect in intersections) {
                xValues.add(rect.left)
                xValues.add(rect.right)
            }
            xValues.sort()
            var area = 0L
            for (index in 0 until xValues.size - 1) {
                val left = xValues[index]
                val right = xValues[index + 1]
                if (right <= left) continue
                val ranges = ArrayList<IntArray>()
                for (rect in intersections) {
                    if (rect.left < right && rect.right > left) {
                        ranges.add(intArrayOf(rect.top, rect.bottom))
                    }
                }
                ranges.sortWith(Comparator { a, b -> a[0].compareTo(b[0]) })
                var rangeStart = Integer.MIN_VALUE
                var rangeEnd = Integer.MIN_VALUE
                for (range in ranges) {
                    if (rangeStart == Integer.MIN_VALUE) {
                        rangeStart = range[0]
                        rangeEnd = range[1]
                    } else if (range[0] > rangeEnd) {
                        area += (right - left).toLong() * max(0, rangeEnd - rangeStart)
                        rangeStart = range[0]
                        rangeEnd = range[1]
                    } else {
                        rangeEnd = max(rangeEnd, range[1])
                    }
                }
                if (rangeStart != Integer.MIN_VALUE) {
                    area += (right - left).toLong() * max(0, rangeEnd - rangeStart)
                }
            }
            return area
        }

        private fun lower(value: String?): String =
            value?.lowercase(Locale.ROOT) ?: ""
    }
}
