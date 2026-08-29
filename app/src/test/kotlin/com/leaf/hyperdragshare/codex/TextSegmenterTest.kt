package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSegmenterTest {
    @Test
    fun nativeRangesUseTheBigBangWordAndPunctuationFormat() {
        assertArrayEquals(
            intArrayOf(0, 1, 3, 4, -1, 2, 2),
            TextSegmenter.buildSegments("你好，世界", intArrayOf(0, 2, 3, 5)),
        )
    }

    @Test
    fun whitespaceIsKeptBetweenWordsButNotMadeSelectable() {
        assertArrayEquals(
            intArrayOf(0, 4, 6, 7, -1),
            TextSegmenter.buildSegments("hello 世界", intArrayOf(0, 5, 6, 8)),
        )
    }

    @Test
    fun emptyInputDoesNotProduceAnInvalidBigBangSegment() {
        assertNull(TextSegmenter.buildSegments("", intArrayOf(0, 1)))
    }
}
