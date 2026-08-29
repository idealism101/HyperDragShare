package com.leaf.hyperdragshare.codex

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.LinearLayout
import android.widget.TextView
import java.io.Serializable
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BoomChipPage(activity: Activity, contentView: View, enableLegacyMask: Boolean) {

    val mLayout: BoomWordsLayout
    val mActivity: Activity = activity
    val mBoomTable: View
    val mMask: View
    val mScroller: CustomScrollView
    val mCancel: View
    val mBoomPage: View = contentView
    val mBoomActionHandler: BoomActionHandler
    val mDismissClickListener: View.OnClickListener

    private val mBoomConent: SwipeSelectView
    private val mEnableLegacyMask: Boolean = enableLegacyMask
    private val mAdjacentTopHint: TextView
    private val mAdjacentBottomHint: TextView
    private val mScrollerBaseInset: Int
    private val mTableBasePaddingTop: Int
    private val mTableBasePaddingBottom: Int

    var mSavedData: Serializable? = null
    private var mOnAdjacentRequestListener: OnAdjacentRequestListener? = null
    private var mAdjacentLoading = false
    private var mAdjacentOffset = 0f

    private var mTouchedX = 0
    private var mTouchedY = 0

    var mDoBoomAnimation: OnGlobalLayoutListener = object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            mBoomConent.viewTreeObserver.removeOnGlobalLayoutListener(mDoBoomAnimation)
            if (mEnableLegacyMask && mScroller.canScrollVertically(1)) {
                mMask.visibility = View.VISIBLE
            }
            if (restoreSelectedState()) {
                if (DBG) {
                    LogUtils.d(TAG, "Skip boom animation when restoring")
                }
                return
            }
            if (mTouchedX == -1 || mTouchedY == -1) {
                DragShareLog.e(TAG, "bad touch position passed")
                return
            }
            val pageX = getChipParentX()
            val pageY = getChipParentY()
            if (DBG) {
                LogUtils.d(TAG, "init Chip and do boom animation")
            }
            val animationRows = min(mLayout.getRowCount(), 12)
            for (i in 0 until animationRows) {
                val row = getChipRow(i) ?: continue
                val rowX = row.x
                val rowY = row.y
                for (j in 0 until row.childCount) {
                    val child = row.getChildAt(j)
                    child.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    val newX = mTouchedX - pageX - rowX - child.measuredWidth / 2
                    val newY = mTouchedY - pageY - rowY - child.measuredHeight / 2
                    val x = child.x
                    val y = child.y
                    child.translationX = newX - x
                    child.translationY = newY - y
                    BoomAnimator.makeBoomAnimation(child)
                }
            }
        }

        private fun getChipParentX(): Float {
            val location = IntArray(2)
            mBoomConent.getLocationOnScreen(location)
            return location[0].toFloat()
        }

        private fun getChipParentY(): Float {
            val location = IntArray(2)
            mBoomConent.getLocationOnScreen(location)
            return location[1].toFloat()
        }
    }

    init {
        mBoomTable = contentView.findViewById(R.id.boom_table)
        mBoomConent = contentView.findViewById<SwipeSelectView>(R.id.boom_content)
        mMask = contentView.findViewById(R.id.boom_mask)
        mCancel = contentView.findViewById(R.id.mask_cancel)
        mScroller = contentView.findViewById<CustomScrollView>(R.id.boom_scroller)
        mAdjacentTopHint = contentView.findViewById<TextView>(R.id.boom_adjacent_top_hint)
        mAdjacentBottomHint = contentView.findViewById<TextView>(R.id.boom_adjacent_bottom_hint)
        mScrollerBaseInset = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            28f,
            mActivity.resources.displayMetrics,
        ).toInt()
        if (!mEnableLegacyMask) {
            mMask.visibility = View.GONE
            removeLegacyChromeSpacing()
        }
        mLayout = BoomWordsLayout(mActivity)
        mBoomConent.setBoomPage(this)
        mDismissClickListener = View.OnClickListener {
            if (!handleClick()) {
                val hostActivity = mActivity
                if (hostActivity is TextSegmentationActivity) {
                    hostActivity.requestAnimatedDismissFromLegacy()
                } else {
                    hostActivity.finish()
                }
            }
        }
        mBoomPage.setOnClickListener(mDismissClickListener)
        mBoomTable.setOnClickListener(mDismissClickListener)
        mScroller.setOnClickListener(mDismissClickListener)
        mBoomConent.setOnClickListener(mDismissClickListener)
        mCancel.setOnClickListener(mDismissClickListener)
        mBoomActionHandler = BoomActionHandler(this, mEnableLegacyMask)
        mScroller.setOnScrollListener(mBoomActionHandler)
        mScroller.setOnEdgeDragListener(object : CustomScrollView.OnEdgeDragListener {
            override fun onEdgeDrag(offset: Float) {
                updateAdjacentPull(offset)
            }

            override fun onEdgeDragRelease(offset: Float, triggered: Boolean) {
                releaseAdjacentPull(offset, triggered)
            }
        })
        mTableBasePaddingTop = mBoomTable.paddingTop
        mTableBasePaddingBottom = mBoomTable.paddingBottom
    }

    interface OnAdjacentRequestListener {
        fun onAdjacentRequest(direction: String)
    }

    private fun removeLegacyChromeSpacing() {
        val selectBarHeadroom = abs(
            mActivity.resources.getDimensionPixelOffset(R.dimen.chip_row_move_up_offset),
        )
        val scrollerParams = mScroller.layoutParams as ViewGroup.MarginLayoutParams
        scrollerParams.topMargin = 0
        scrollerParams.bottomMargin = 0
        mScroller.layoutParams = scrollerParams
        mScroller.clipToPadding = false
        mScroller.setPadding(
            mScroller.paddingLeft,
            mScrollerBaseInset,
            mScroller.paddingRight,
            mScrollerBaseInset,
        )

        val tableParams = mBoomTable.layoutParams as ViewGroup.MarginLayoutParams
        tableParams.topMargin = 0
        tableParams.bottomMargin = 0
        mBoomTable.layoutParams = tableParams
        mBoomTable.setPadding(
            mBoomTable.paddingLeft,
            selectBarHeadroom,
            mBoomTable.paddingRight,
            mBoomTable.paddingBottom,
        )

        mBoomConent.setPadding(
            mBoomConent.paddingLeft,
            0,
            mBoomConent.paddingRight,
            0,
        )
    }

    fun initWords(
        segment: IntArray,
        text: String,
        touchedIndex: Int,
        touchedX: Int,
        touchedY: Int,
    ): Boolean {
        if (mLayout.layoutWords(segment, text, touchedIndex)) {
            mTouchedX = touchedX
            mTouchedY = touchedY
            initChips(true)
            return true
        }
        return false
    }

    /**
     * Clear all chip views and selection state so that a subsequent
     * [initWords] call can fully reinitialise the page (e.g. when
     * the Activity receives a new Intent via `onNewIntent`).
     */
    fun prepareForReinit() {
        mBoomActionHandler.clearSelectionStateForRelayout()
        mBoomConent.removeAllViews()
    }

    fun resetChips() {
        for (i in 0 until mLayout.getRowCount()) {
            val row = getChipRow(i) ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                val tag = child.tag
                if (tag is BoomChip) {
                    tag.setSelected(false)
                }
            }
            BoomAnimator.makeMoveAnimation(row, row.translationY, 0f)
        }
    }

    fun moveChipRow(row: Int, to: Float) {
        val child = mBoomConent.getChildAt(row)
        BoomAnimator.makeMoveAnimation(child, child.translationY, to)
    }

    fun getRowTop(row: Int): Int {
        val child = mBoomConent.getChildAt(row)
        return if (child == null) 0 else child.top
    }

    fun getRowsHeight(topRow: Int, bottomRow: Int): Int {
        val top = mBoomConent.getChildAt(topRow)
        val bottom = mBoomConent.getChildAt(bottomRow)
        if (top == null || bottom == null) {
            return 0
        }
        return bottom.bottom - top.top
    }

    fun handleClick(): Boolean = mBoomActionHandler.handleClick()

    fun captureSelectedState(): Serializable? {
        if (mBoomActionHandler.hasSelection()) {
            val wordSet = mBoomActionHandler.mSelectedId
            val ranges = Array(wordSet.size) { IntArray(2) }
            var idx = 0
            for (wordIdx in wordSet) {
                ranges[idx][0] = mLayout.getWordStart(wordIdx)
                ranges[idx][1] = mLayout.getWordEnd(wordIdx)
                idx++
            }
            return ranges as Serializable
        }
        return null
    }

    fun restoreSelectedState(savedState: Serializable?) {
        mSavedData = savedState
    }

    val originalText: String
        get() = mLayout.getOriText()

    fun selectAll() {
        val wordCount = mLayout.getWordCount()
        if (wordCount <= 0) {
            return
        }
        if (mBoomActionHandler.isAllSelected()) {
            handleClick()
            return
        }
        for (i in 0 until mLayout.getRowCount()) {
            val row = getChipRow(i) ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                val tag = child.tag
                if (tag is BoomChip) {
                    tag.setSelected(true)
                }
            }
        }
        mBoomActionHandler.onSelect(0, wordCount - 1)
    }

    /**
     * Auto-select the word that was touched / identified by the initial layout.
     * This is used when a third-party caller provides a character index via
     * `EXTRA_SELECTED_CHAR_INDEX` — after layout the touched word index
     * is known, and this method selects it.
     */
    fun selectTouchedWord() {
        val touchedIndex = mLayout.getTouchedIndex()
        if (touchedIndex < 0 || touchedIndex >= mLayout.getWordCount()) {
            return
        }
        for (i in 0 until mLayout.getRowCount()) {
            val row = getChipRow(i) ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                val tag = child.tag
                if (tag is BoomChip && tag.index == touchedIndex) {
                    tag.setSelected(true)
                }
            }
        }
        mBoomActionHandler.onSelect(touchedIndex, touchedIndex)
        // Scroll the touched word to the centre of the viewport
        mScroller.post {
            val row = mLayout.getRowForIndex(touchedIndex)
            val rowView = mBoomConent.getChildAt(row)
            if (rowView != null) {
                val rowCentre = rowView.top + rowView.height / 2
                val viewportCentre = mScroller.height / 2
                val targetScrollY = rowCentre - viewportCentre
                mScroller.scrollTo(0, max(0, targetScrollY))
            }
        }
    }

    fun splitSelectedWordsToChars(): Boolean {
        if (!mBoomActionHandler.hasSelection()) {
            return false
        }
        val newSelection = mLayout.splitSelectedWordsToChars(
            TreeSet(mBoomActionHandler.mSelectedId),
        )
        if (newSelection == null || newSelection.isEmpty()) {
            return false
        }
        mBoomActionHandler.clearSelectionStateForRelayout()
        mSavedData = newSelection
        mBoomConent.removeAllViews()
        initChips(false)
        mBoomConent.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    mBoomConent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    updateScrollerInsetsForContent()
                    restoreSelectedState()
                }
            },
        )
        return true
    }

    fun setOnAdjacentRequestListener(listener: OnAdjacentRequestListener?) {
        mOnAdjacentRequestListener = listener
    }

    @JvmOverloads
    fun replaceWords(
        segment: IntArray,
        text: String,
        targetWordIndex: Int,
        charOffset: Int = 0,
    ): Boolean {
        // Save selection as char ranges before it gets cleared
        var savedSelection: Serializable? = null
        if (mBoomActionHandler.hasSelection()) {
            savedSelection = captureSelectedState()
        }
        mBoomActionHandler.clearSelectionStateForRelayout()
        mSavedData = null
        mBoomConent.removeAllViews()
        if (!mLayout.layoutWords(segment, text, -1)) {
            finishAdjacentPull()
            return false
        }
        initChips(false)
        // Restore selection with adjusted char ranges
        if (savedSelection is Array<*> && savedSelection.isArrayOf<IntArray>()) {
            @Suppress("UNCHECKED_CAST")
            val ranges = savedSelection as Array<IntArray>
            if (charOffset != 0) {
                for (range in ranges) {
                    range[0] += charOffset
                    range[1] += charOffset
                }
            }
            mSavedData = ranges as Serializable
            mBoomConent.viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        mBoomConent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        updateScrollerInsetsForContent()
                        restoreSelectedState()
                    }
                },
            )
        }
        scrollToWord(targetWordIndex)
        finishAdjacentPull()
        return true
    }

    private fun scrollToWord(wordIndex: Int) {
        if (wordIndex < 0 || wordIndex >= mLayout.getWordCount()) {
            mScroller.scrollTo(0, 0)
            return
        }
        mScroller.post {
            mScroller.scrollTo(0, getRowTop(mLayout.getRowForIndex(wordIndex)))
        }
    }

    fun finishAdjacentPull() {
        mAdjacentLoading = false
        mScroller.setEdgeDragEnabled(true)
        animateContentOffset(0f)
        hideAdjacentHint(mAdjacentTopHint)
        hideAdjacentHint(mAdjacentBottomHint)
    }

    private fun initChips(animate: Boolean) {
        for (i in 0 until mLayout.getRowCount()) {
            val start = mLayout.getRowStart(i)
            val count = mLayout.getColumnCount(i)
            if (mLayout.isGapRow(i)) {
                val rowHeight = mActivity.resources.getDimensionPixelOffset(R.dimen.chip_row_height)
                val gapHeight = Math.round(
                    rowHeight * BigBangSettings.get(mActivity).gapRowHeightPercent / 100f,
                )
                val spacer = View(mActivity)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    gapHeight,
                )
                mBoomConent.addView(spacer)
                continue
            }
            val row = LinearLayout(mActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            for (j in 0 until count) {
                val isPunc = mLayout.isPunc(start + j)
                val chipView = mActivity.layoutInflater.inflate(
                    if (isPunc) R.layout.boom_punc_layout else R.layout.boom_chip_layout,
                    null,
                )
                val chip = BoomChip(start + j, chipView)
                chipView.tag = chip
                row.addView(chipView)
            }
            mBoomConent.addView(row)
        }
        mBoomConent.requestLayout()
        mScroller.post { updateScrollerInsetsForContent() }
        if (animate) {
            mBoomConent.viewTreeObserver.addOnGlobalLayoutListener(mDoBoomAnimation)
        }
    }

    private fun restoreSelectedState(): Boolean {
        val savedData = mSavedData
        if (savedData is Array<*> && savedData.isArrayOf<IntArray>()) {
            // Char-range based selection (stable across rotation)
            @Suppress("UNCHECKED_CAST")
            val ranges = savedData as Array<IntArray>
            val newWordSet = TreeSet<Int>()
            for (range in ranges) {
                val charStart = range[0]
                val charEnd = range[1]
                for (i in 0 until mLayout.getWordCount()) {
                    val wordStart = mLayout.getWordStart(i)
                    val wordEnd = mLayout.getWordEnd(i)
                    if (wordStart < charEnd && wordEnd > charStart) {
                        newWordSet.add(i)
                    }
                }
            }
            if (!newWordSet.isEmpty()) {
                for (i in 0 until mLayout.getRowCount()) {
                    val row = getChipRow(i) ?: continue
                    for (j in 0 until row.childCount) {
                        val child = row.getChildAt(j)
                        val tag = child.tag
                        if (tag is BoomChip && newWordSet.contains(tag.index)) {
                            tag.setSelected(true)
                        }
                    }
                }
                mBoomActionHandler.onSelect(newWordSet)
                return true
            }
        } else if (savedData is TreeSet<*>) {
            // Legacy: word-index based (used by splitSelectedWordsToChars)
            @Suppress("UNCHECKED_CAST")
            val set = savedData as TreeSet<Int>
            if (set.size > 0) {
                for (i in 0 until mLayout.getRowCount()) {
                    val row = getChipRow(i) ?: continue
                    for (j in 0 until row.childCount) {
                        val child = row.getChildAt(j)
                        val tag = child.tag
                        if (tag is BoomChip && set.contains(tag.index)) {
                            tag.setSelected(true)
                        }
                    }
                }
                mBoomActionHandler.onSelect(set)
                return true
            }
        }
        return false
    }

    private fun getChipRow(index: Int): LinearLayout? {
        val child = mBoomConent.getChildAt(index)
        return if (child is LinearLayout) child else null
    }

    private fun updateAdjacentPull(offset: Float) {
        if (mAdjacentLoading) {
            return
        }
        mAdjacentOffset = offset
        applyContentOffset(offset)
        if (offset > 0f) {
            showAdjacentHint(mAdjacentTopHint, "before", offset)
            hideAdjacentHint(mAdjacentBottomHint)
        } else if (offset < 0f) {
            showAdjacentHint(mAdjacentBottomHint, "after", -offset)
            hideAdjacentHint(mAdjacentTopHint)
        } else {
            hideAdjacentHint(mAdjacentTopHint)
            hideAdjacentHint(mAdjacentBottomHint)
        }
    }

    private fun releaseAdjacentPull(offset: Float, triggered: Boolean) {
        if (mAdjacentLoading) {
            return
        }
        val direction = if (offset > 0f) "before" else if (offset < 0f) "after" else null
        val previewText = if (direction == null) {
            null
        } else {
            TextSessionCoordinator.INSTANCE.peekAdjacentText(direction)
        }
        if (!triggered || direction == null || previewText == null) {
            finishAdjacentPull()
            return
        }
        mAdjacentLoading = true
        mScroller.setEdgeDragEnabled(false)
        animateContentOffset(clampHoldOffset(offset))
        mOnAdjacentRequestListener?.onAdjacentRequest(direction)
    }

    private fun showAdjacentHint(view: TextView, direction: String, distance: Float) {
        val preview = TextSessionCoordinator.INSTANCE.peekAdjacentText(direction)
        if (preview == null) {
            hideAdjacentHint(view)
            return
        }
        view.visibility = View.VISIBLE
        view.text = getHintTitle(direction)
        val alpha = min(1f, distance / getTriggerDistance())
        view.alpha = alpha
    }

    private fun hideAdjacentHint(view: TextView) {
        view.alpha = 0f
        view.visibility = View.GONE
    }

    private fun getHintTitle(direction: String): String = if ("before" == direction) {
        mActivity.getString(R.string.bigbang_pull_previous)
    } else {
        mActivity.getString(R.string.bigbang_pull_next)
    }

    private fun getTriggerDistance(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        88f,
        mActivity.resources.displayMetrics,
    )

    private fun clampHoldOffset(offset: Float): Float {
        val hold = getTriggerDistance()
        return if (offset > 0f) hold else -hold
    }

    private fun applyContentOffset(offset: Float) {
        mScroller.translationY = 0f
        mBoomTable.translationY = offset
    }

    private fun animateContentOffset(offset: Float) {
        mAdjacentOffset = offset
        mScroller.animate().cancel()
        mScroller.translationY = 0f
        mBoomTable.animate()
            .translationY(offset)
            .setDuration(180L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (mAdjacentOffset == 0f && !mAdjacentLoading) {
                        hideAdjacentHint(mAdjacentTopHint)
                        hideAdjacentHint(mAdjacentBottomHint)
                    }
                }
            })
            .start()
    }

    private fun updateScrollerInsetsForContent() {
        val viewportHeight = mScroller.height
        val contentHeight = mBoomConent.height
        if (viewportHeight <= 0 || contentHeight <= 0) {
            return
        }
        val symmetricBaseInset = max(mTableBasePaddingTop, mTableBasePaddingBottom)
        val availableHeight = viewportHeight - (mScrollerBaseInset * 2) - (symmetricBaseInset * 2)
        val extraInset = max(0, (availableHeight - contentHeight) / 2)
        val targetTableTop = symmetricBaseInset + extraInset
        val targetTableBottom = symmetricBaseInset + extraInset
        if (mBoomTable.paddingTop == targetTableTop &&
            mBoomTable.paddingBottom == targetTableBottom
        ) {
            return
        }
        mBoomTable.setPadding(
            mBoomTable.paddingLeft,
            targetTableTop,
            mBoomTable.paddingRight,
            targetTableBottom,
        )
    }

    inner class BoomChip(id: Int, chipView: View) {
        val index: Int = id
        val word: TextView
        val punc: Boolean

        init {
            punc = mLayout.isPunc(id)
            word = if (punc) {
                chipView.findViewById(R.id.punc)
            } else {
                chipView.findViewById(R.id.word)
            }
            word.text = mLayout.getWord(id)
        }

        fun setSelected(selected: Boolean) {
            word.setShadowLayer(if (selected) 1.0f else 0f, 0f, -3.0f, 0x1f000000)
            word.isSelected = selected
        }
    }

    companion object {
        private const val TAG = "BoomChipPage"
        private val DBG = TextSegmentationActivity.DBG
    }
}
