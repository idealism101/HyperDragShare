package com.leaf.hyperdragshare.codex

import android.content.Context
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import java.util.TreeSet
import kotlin.math.max

class BoomWordsLayout(context: Context) {
    private val mMaxRowNumber: Int
    private val mBoomPageWidth: Int
    private val mWordMinWidth: Int
    private val mWordBaseWidth: Int
    private val mPuncMinWidth: Int
    private val mPuncBaseWidth: Int
    private val mWordPaint: TextPaint
    private val mPuncPaint: TextPaint

    private var mWords = RangeList<Word>()
    private val mRowStart = ArrayList<Int>()
    private val mRowCount = ArrayList<Int>()
    private val mRowIsGap = ArrayList<Boolean>()
    private var mHardBreaks = ArrayList<Int>()
    private var mIdToRow = IntArray(0)
    private var mTouchedIndex = 0
    private var mOriText = ""

    private class RangeList<E> : ArrayList<E>() {
        fun remove(fromIndex: Int, toIndex: Int) {
            if (fromIndex < toIndex) {
                removeRange(fromIndex, toIndex)
            }
        }
    }

    private class Word(val word: String, val start: Int, val punc: Boolean)

    init {
        val res = context.resources
        val displayWidth = res.displayMetrics.widthPixels
        mBoomPageWidth = displayWidth - res.getDimensionPixelSize(R.dimen.page_margin_left) -
            res.getDimensionPixelSize(R.dimen.page_margin_right)
        // mMaxRowNumber = displayWidth > 1080 ? 11 : 10;
        mMaxRowNumber = 1000
        mWordMinWidth = res.getDimensionPixelSize(R.dimen.word_min_width)
        mWordBaseWidth = res.getDimensionPixelSize(R.dimen.word_base_width)
        mPuncMinWidth = res.getDimensionPixelSize(R.dimen.punc_min_width)
        mPuncBaseWidth = res.getDimensionPixelSize(R.dimen.punc_base_width)
        mWordPaint = View.inflate(context, R.layout.boom_chip_layout, null)
            .findViewById<TextView>(R.id.word).paint
        mPuncPaint = View.inflate(context, R.layout.boom_punc_layout, null)
            .findViewById<TextView>(R.id.punc).paint
    }

    fun layoutWords(segment: IntArray, text: String, touchedIndex: Int): Boolean {
        var puncIndexStart = -1
        for (i in segment.indices) {
            if (segment[i] == -1) {
                puncIndexStart = i
                break
            }
        }
        if (puncIndexStart == -1) return false
        val newSeg = IntArray(puncIndexStart)
        var wordIndexStart = 0
        ++puncIndexStart
        var garbageOffset = 0
        var touchIndexOffset = 0
        val newText = StringBuilder()
        var i = 0
        while (i < newSeg.size) {
            val curWordStart = segment[i]
            val curPuncStart = if (puncIndexStart == segment.size) {
                text.length
            } else {
                segment[puncIndexStart]
            }
            if (curWordStart < curPuncStart) {
                if (curWordStart > wordIndexStart) {
                    val removedDiff = appendFilteredGap(newText, text, wordIndexStart, curWordStart)
                    if (touchedIndex > curWordStart) {
                        touchIndexOffset += removedDiff
                    }
                    garbageOffset += removedDiff
                } else if (curWordStart < wordIndexStart) {
                    DragShareLog.e(
                        TAG,
                        "rebuild segment failed curWordStart=" + curWordStart +
                            ", wordIndexStart=" + wordIndexStart,
                    )
                    return false
                }
                newSeg[i] = segment[i] - garbageOffset
                newSeg[i + 1] = segment[i + 1] - garbageOffset
                wordIndexStart = segment[i + 1] + 1
                newText.append(text.substring(segment[i], segment[i + 1] + 1))
            } else {
                if (curPuncStart > wordIndexStart) {
                    val removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart)
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff
                    }
                    garbageOffset += removedDiff
                } else if (curPuncStart < wordIndexStart) {
                    DragShareLog.e(
                        TAG,
                        "rebuild segment failed curPuncStart=" + curPuncStart +
                            ", wordIndexStart=" + wordIndexStart,
                    )
                    return false
                }
                wordIndexStart = segment[puncIndexStart + 1] + 1
                newText.append(
                    text.substring(segment[puncIndexStart], segment[puncIndexStart + 1] + 1),
                )
                puncIndexStart += 2
                i -= 2
            }
            i += 2
        }
        if (puncIndexStart < segment.size) {
            var index = puncIndexStart
            while (index < segment.size) {
                val curPuncStart = segment[index]
                if (curPuncStart > wordIndexStart) {
                    val removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart)
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff
                    }
                } else if (curPuncStart < wordIndexStart) {
                    DragShareLog.e(
                        TAG,
                        "add ending punctuation failed curPuncStart=" + curPuncStart +
                            ", wordIndexStart=" + wordIndexStart,
                    )
                    return false
                }
                wordIndexStart = segment[index + 1] + 1
                newText.append(text.substring(segment[index], segment[index + 1] + 1))
                index += 2
            }
        }
        return layoutWordsAfterFilter(newSeg, newText.toString(), touchedIndex - touchIndexOffset)
    }

    private fun appendFilteredGap(
        newText: StringBuilder,
        text: String,
        start: Int,
        end: Int,
    ): Int {
        var preserved = 0
        for (i in start until end) {
            val ch = text[i]
            if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) {
                newText.append(ch)
                ++preserved
            }
        }
        return (end - start) - preserved
    }

    private fun layoutWordsAfterFilter(segment: IntArray, text: String, touchedIndex: Int): Boolean {
        mOriText = text
        mWords.clear()
        mHardBreaks.clear()
        mTouchedIndex = -1
        var start = 0
        var end = 0
        var prev = 0
        var i = 0
        while (i < segment.size) {
            start = segment[i]
            end = segment[i + 1] + 1
            addGapIntoChips(prev, start)
            // Java's String.trim() semantics: strip anything at or below the space character.
            val trim = text.substring(start, end)
                .replace("\\p{Z}".toRegex(), " ")
                .trim { it <= ' ' }
            if (!TextUtils.isEmpty(trim)) {
                if (touchedIndex >= start && touchedIndex < end) {
                    mTouchedIndex = mWords.size
                }
                mWords.add(Word(trim, start, false))
            }
            prev = end
            i += 2
        }
        addGapIntoChips(prev, text.length)

        val wordCount = mWords.size
        if (wordCount > 0) {
            generateLayout()
            val rowCount = mRowCount.size
            if (rowCount > mMaxRowNumber) {
                if (mTouchedIndex == -1) {
                    start = 0
                    end = mRowStart[mMaxRowNumber]
                } else {
                    val row = getRowForIndex(mTouchedIndex)
                    if (row < mMaxRowNumber / 2) {
                        start = 0
                        end = getRowStart(mMaxRowNumber)
                    } else if (row >= rowCount - mMaxRowNumber / 2) {
                        start = getRowStart(rowCount - mMaxRowNumber)
                        end = wordCount
                    } else {
                        start = getRowStart(row - mMaxRowNumber / 2)
                        end = getRowStart(row + mMaxRowNumber / 2)
                    }
                }
                mWords.remove(end, wordCount)
                mWords.remove(0, start)
                generateLayout()
            }
            return true
        }
        return false
    }

    private fun addGapIntoChips(start: Int, end: Int) {
        for (i in start until end) {
            val punc = mOriText[i]
            if (punc == '\n') {
                addHardBreak()
            } else if (!Character.isWhitespace(punc) && !Character.isSpaceChar(punc)) {
                mWords.add(Word(punc.toString(), i, true))
            }
        }
    }

    private fun addHardBreak() {
        val breakIndex = mWords.size
        if (mHardBreaks.size > 0 && mHardBreaks[mHardBreaks.size - 1] == breakIndex) {
            return
        }
        mHardBreaks.add(breakIndex)
    }

    private fun measureChip(index: Int): Int {
        val word = mWords[index]
        return if (word.punc) {
            max(mPuncMinWidth, mPuncBaseWidth + mPuncPaint.measureText(word.word).toInt())
        } else {
            max(mWordMinWidth, mWordBaseWidth + mWordPaint.measureText(word.word).toInt())
        }
    }

    private fun generateLayout() {
        var count = 0
        var start = 0
        var remain = mBoomPageWidth
        mRowCount.clear()
        mRowStart.clear()
        mRowIsGap.clear()
        mIdToRow = IntArray(mWords.size)
        var i = 0
        while (i < mWords.size) {
            if (isHardBreakIndex(i)) {
                if (count > 0) {
                    addRow(start, count, false)
                }
                addRow(i, 0, true)
                start = i
                count = 0
                remain = mBoomPageWidth
            }
            val chipWidth = measureChip(i)
            if (chipWidth > remain) {
                if (count == 0) {
                    mIdToRow[i] = mRowCount.size
                    addRow(i, 1, false)
                    start = i + 1
                } else {
                    addRow(start, count, false)
                    start = i
                    count = 0
                    remain = mBoomPageWidth
                    --i
                }
            } else {
                ++count
                remain -= chipWidth
                mIdToRow[i] = mRowCount.size
            }
            ++i
        }
        if (count > 0) {
            addRow(start, count, false)
        }
    }

    private fun addRow(start: Int, count: Int, isGap: Boolean) {
        mRowStart.add(start)
        mRowCount.add(count)
        mRowIsGap.add(isGap)
    }

    private fun isHardBreakIndex(index: Int): Boolean = mHardBreaks.contains(index)

    fun getRowCount(): Int = mRowCount.size

    fun getRowStart(row: Int): Int = mRowStart[row]

    fun getColumnCount(row: Int): Int = mRowCount[row]

    fun isGapRow(row: Int): Boolean = mRowIsGap[row]

    fun getRowForIndex(index: Int): Int {
        if (index < 0 || index >= mIdToRow.size) {
            return 0
        }
        return mIdToRow[index]
    }

    fun isPunc(index: Int): Boolean = mWords[index].punc

    fun getWord(index: Int): String = mWords[index].word

    fun getWordEnd(index: Int): Int = mWords[index].word.length + mWords[index].start

    fun getWordStart(index: Int): Int = mWords[index].start

    fun getOriText(start: Int, end: Int): String = mOriText.substring(start, end)

    fun getOriText(): String = mOriText

    fun getWordCount(): Int = mWords.size

    fun getTouchedIndex(): Int = mTouchedIndex

    fun splitSelectedWordsToChars(selectedIds: TreeSet<Int>?): TreeSet<Int>? {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return null
        }
        val newWords = RangeList<Word>()
        val newHardBreaks = ArrayList<Int>()
        val newSelected = TreeSet<Int>()
        var changed = false
        for (i in 0 until mWords.size) {
            if (isHardBreakIndex(i)) {
                newHardBreaks.add(newWords.size)
            }
            val word = mWords[i]
            val selected = selectedIds.contains(i)
            if (!selected || word.punc || word.word.length <= 1) {
                newWords.add(word)
                if (selected) {
                    newSelected.add(newWords.size - 1)
                }
                continue
            }
            changed = true
            var offset = 0
            while (offset < word.word.length) {
                val codePoint = word.word.codePointAt(offset)
                val next = offset + Character.charCount(codePoint)
                newWords.add(Word(word.word.substring(offset, next), word.start + offset, false))
                newSelected.add(newWords.size - 1)
                offset = next
            }
        }
        if (!changed) {
            return null
        }
        mWords = newWords
        mHardBreaks = newHardBreaks
        generateLayout()
        return newSelected
    }

    companion object {
        private const val TAG = "BoomWordsLayout"
    }
}
