package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import java.util.TreeSet

class BoomActionHandler(boomPage: BoomChipPage, enableFakeSelectBar: Boolean) :
    CustomScrollView.OnScrollListener {
    private val mBoomPage: BoomChipPage = boomPage
    private val mToast: Toast

    private val mRowMoveUpOffset: Int
    private val mRowMoveDownOffset: Int
    private val mSelectRectMarginTop: Int
    private val mSelectRectTopOffset: Int
    private val mSelectBarYOffset: Int

    var mSelectedTopRow = -1
    var mSelectedBottomRow = -1
    lateinit var mSelectBar: RelativeLayout
    lateinit var mFakeSelectBar: RelativeLayout
    lateinit var mSelectRect: LinearLayout

    @JvmField
    val mSelectedId = TreeSet<Int>()
    private val mSelectBarRect = Rect()
    private val mEnableFakeSelectBar: Boolean = enableFakeSelectBar

    init {
        mToast = Toast.makeText(boomPage.mActivity, "", Toast.LENGTH_SHORT)

        val res = boomPage.mActivity.resources
        mRowMoveUpOffset = res.getDimensionPixelOffset(R.dimen.chip_row_move_up_offset)
        mRowMoveDownOffset = res.getDimensionPixelOffset(R.dimen.chip_row_move_down_offset)
        if (mEnableFakeSelectBar) {
            mSelectRectMarginTop = res.getDimensionPixelOffset(R.dimen.select_rect_margin_top)
            mSelectRectTopOffset = res.getDimensionPixelOffset(R.dimen.select_rect_top_offset)
            mSelectBarYOffset = 0
        } else {
            val expand = res.getDimensionPixelOffset(R.dimen.chip_row_padding_top) +
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    5f,
                    res.displayMetrics,
                ).toInt()
            mSelectRectMarginTop = -expand
            mSelectRectTopOffset = expand * 2
            mSelectBarYOffset = mRowMoveUpOffset
        }

        initViews(mBoomPage.mBoomTable)
        initFakeViews(mBoomPage.mBoomPage)
    }

    fun onSelect(savedState: TreeSet<Int>) {
        mSelectedId.clear()
        val wordCount = mBoomPage.mLayout.getWordCount()
        for (id in savedState) {
            if (id < wordCount) {
                mSelectedId.add(id)
            }
        }
        if (!mSelectedId.isEmpty()) {
            onSelectInternal(mSelectedId.first(), mSelectedId.last())
        }
    }

    fun onSelect(start: Int, end: Int) {
        for (i in start..end) {
            mSelectedId.add(i)
        }
        onSelectInternal(start, end)
    }

    private fun onSelectInternal(start: Int, end: Int) {
        val topRow = mBoomPage.mLayout.getRowForIndex(start)
        val bottomRow = mBoomPage.mLayout.getRowForIndex(end)

        if (mSelectedTopRow == -1) {
            mSelectedTopRow = topRow
            mSelectedBottomRow = bottomRow
            showSelBarAndBgRect(topRow)
        } else {
            if (topRow < mSelectedTopRow) {
                mSelectedTopRow = topRow
            }
            if (bottomRow > mSelectedBottomRow) {
                mSelectedBottomRow = bottomRow
            }
            positionSelBar(mSelectedTopRow)
            positionSelectRect(mSelectedTopRow)
        }

        moveChipRows()

        mSelectBar.post {
            if (hasSelection() && mSelectedTopRow != -1) {
                positionSelBar(mSelectedTopRow)
                positionSelectRect(mSelectedTopRow)
            }
        }
    }

    fun deSelect(stat: Int, end: Int) {
        for (i in stat..end) {
            mSelectedId.remove(i)
        }
        if (mSelectedId.size > 0) {
            val min = mBoomPage.mLayout.getRowForIndex(mSelectedId.first())
            val max = mBoomPage.mLayout.getRowForIndex(mSelectedId.last())
            if (min > mSelectedTopRow) {
                mSelectedTopRow = min
                positionSelBar(min)
                positionSelectRect(min)
            } else if (max < mSelectedBottomRow) {
                mSelectedBottomRow = max
                positionSelBar(min)
                positionSelectRect(min)
            }
        } else {
            hideSelectBarAndRect()
            mBoomPage.resetChips()
        }
        moveChipRows()
    }

    fun handleClick(): Boolean {
        if (mSelectedId.size > 0) {
            mSelectedId.clear()
            mBoomPage.resetChips()
            hideSelectBarAndRect()
            return true
        }
        return false
    }

    fun clearSelectionStateForRelayout() {
        mSelectedId.clear()
        mSelectedTopRow = -1
        mSelectedBottomRow = -1
        mSelectBar.visibility = View.INVISIBLE
        val params: ViewGroup.LayoutParams = mSelectRect.layoutParams
        params.height = 0
        mSelectRect.layoutParams = params
        mSelectRect.visibility = View.INVISIBLE
        if (mFakeSelectBar.visibility == View.VISIBLE) {
            mFakeSelectBar.visibility = View.INVISIBLE
        }
    }

    private fun isChineseWord(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
        ) {
            return true
        }
        return false
    }

    @Suppress("unused")
    private fun getContentType(text: String): Int {
        var type = 0
        for (i in 0 until text.length) {
            val ch = text[i]
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                type = type or 1
            } else if (isChineseWord(ch)) {
                type = type or 2
            } else {
                return 3
            }
        }
        return if (type == 0) 3 else type - 1
    }

    private fun copy(text: String) {
        mToast.setText(mBoomPage.mActivity.resources.getString(R.string.copy_tips))
        mToast.show()
        val clipboard = mBoomPage.mActivity
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    fun search(text: String, type: Int) {
        val activity = mBoomPage.mActivity
        if (activity is TextSegmentationActivity) {
            activity.openSearch(text, type)
        }
    }

    private fun share() {
        val shareText = getSelectedText()
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, shareText)
        val i = Intent.createChooser(send, null)
        i.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        mBoomPage.mActivity.startActivity(i)
    }

    private fun initViews(contentView: View) {
        mSelectRect = contentView.findViewById(R.id.boom_multi_selected_bg)
        mSelectBar = contentView.findViewById(R.id.multi_selected_bar)
        mSelectBar.viewTreeObserver.addOnGlobalLayoutListener { onScrollChanged() }
        val searchView: ImageView = mSelectBar.findViewById(R.id.all_search)
        val resegmentView: ImageView = mSelectBar.findViewById(R.id.all_cut)
        resegmentView.setOnClickListener { mBoomPage.splitSelectedWordsToChars() }
        searchView.setOnClickListener { search(getSelectedText(), SEARCH_WEB) }
        val dictView: ImageView = mSelectBar.findViewById(R.id.all_dict)
        dictView.setOnClickListener { search(getSelectedText(), SEARCH_DICTIONARY) }
        val shareView: ImageView = mSelectBar.findViewById(R.id.all_share)
        shareView.setOnClickListener { share() }
        val copyView: ImageView = mSelectBar.findViewById(R.id.all_copy)
        copyView.setOnClickListener { copy(getSelectedText()) }
    }

    private fun initFakeViews(contentView: View) {
        mFakeSelectBar = contentView.findViewById(R.id.fake_multi_selected_bar)
        val topResegmentView: ImageView = mFakeSelectBar.findViewById(R.id.all_cut)
        topResegmentView.setOnClickListener { mBoomPage.splitSelectedWordsToChars() }
        val topSearchView: ImageView = mFakeSelectBar.findViewById(R.id.all_search)
        topSearchView.setOnClickListener { search(getSelectedText(), SEARCH_WEB) }
        val topDictView: ImageView = mFakeSelectBar.findViewById(R.id.all_dict)
        topDictView.setOnClickListener { search(getSelectedText(), SEARCH_DICTIONARY) }
        val topShareView: ImageView = mFakeSelectBar.findViewById(R.id.all_share)
        topShareView.setOnClickListener { share() }
        val topCopyView: ImageView = mFakeSelectBar.findViewById(R.id.all_copy)
        topCopyView.setOnClickListener { copy(getSelectedText()) }
    }

    fun hasSelection(): Boolean = mSelectedId.size > 0

    fun isAllSelected(): Boolean =
        mSelectedId.size == mBoomPage.mLayout.getWordCount() && mSelectedId.size > 0

    fun getSelectedText(): String {
        val wordCount = mBoomPage.mLayout.getWordCount()
        if (mSelectedId.size == wordCount) {
            return mBoomPage.mLayout.getOriText()
        }
        val res = StringBuilder()
        var last = -1
        for (cur in mSelectedId) {
            if (last == -1) {
                res.append(mBoomPage.mLayout.getWord(cur))
            } else if (cur == last + 1) {
                val end = if (cur == wordCount - 1) {
                    mBoomPage.mLayout.getOriText().length
                } else {
                    mBoomPage.mLayout.getWordEnd(cur)
                }
                res.append(mBoomPage.mLayout.getOriText(mBoomPage.mLayout.getWordEnd(last), end))
            } else {
                res.append(mBoomPage.mLayout.getWord(cur))
            }
            last = cur
        }
        return res.toString()
    }

    private fun getSelectRectHeight(): Int =
        mBoomPage.getRowsHeight(mSelectedTopRow, mSelectedBottomRow) + mSelectRectTopOffset

    private fun getSelectRectY(row: Int): Int = mBoomPage.getRowTop(row) + mSelectRectMarginTop

    private fun getSelectBarY(row: Int): Int = mBoomPage.getRowTop(row) + mSelectBarYOffset

    private fun showSelBarAndBgRect(row: Int) {
        mSelectBar.visibility = View.VISIBLE
        mSelectRect.visibility = View.VISIBLE
        mSelectBar.translationY = getSelectBarY(row).toFloat()
        mSelectRect.translationY = getSelectRectY(row).toFloat()
        BoomAnimator.makeBarAndRectShowAnimation(mSelectBar, mSelectRect, getSelectRectHeight())
    }

    private fun hideSelectBarAndRect() {
        mSelectedTopRow = -1
        mSelectedBottomRow = -1
        BoomAnimator.makeBarAndRectHideAnimation(mSelectBar, mSelectRect)
        if (mFakeSelectBar.visibility == View.VISIBLE) {
            mFakeSelectBar.visibility = View.INVISIBLE
        }
    }

    private fun positionSelBar(row: Int) {
        BoomAnimator.makeMoveAnimation(
            mSelectBar,
            mSelectBar.translationY,
            getSelectBarY(row).toFloat(),
        )
    }

    private fun positionSelectRect(row: Int) {
        BoomAnimator.makeHeightAnimation(
            mSelectRect,
            getSelectRectHeight(),
            mSelectRect.translationY,
            getSelectRectY(row).toFloat(),
        )
    }

    private fun moveChipRows() {
        val rowCount = mBoomPage.mLayout.getRowCount()
        if (mSelectedTopRow == -1 || mSelectedBottomRow == -1) {
            for (i in 0 until rowCount) {
                mBoomPage.moveChipRow(i, 0f)
            }
        } else {
            for (i in 0 until rowCount) {
                val end: Float = if (i < mSelectedTopRow) {
                    mRowMoveUpOffset.toFloat()
                } else if (i > mSelectedBottomRow) {
                    mRowMoveDownOffset.toFloat()
                } else {
                    0f
                }
                mBoomPage.moveChipRow(i, end)
            }
        }
    }

    override fun onScrollChanged() {
        if (!hasSelection()) return
        val pinnedBarContainer = mFakeSelectBar.parent as View?
        val pinnedBarRect = Rect()
        if (pinnedBarContainer == null || !pinnedBarContainer.getGlobalVisibleRect(pinnedBarRect)) {
            return
        }
        val selectBarLocation = IntArray(2)
        mSelectBar.getLocationOnScreen(selectBarLocation)
        mSelectBarRect.set(
            selectBarLocation[0],
            selectBarLocation[1],
            selectBarLocation[0] + mSelectBar.width,
            selectBarLocation[1] + mSelectBar.height,
        )
        if (mSelectBarRect.top <= pinnedBarRect.top &&
            mFakeSelectBar.visibility != View.VISIBLE
        ) {
            val params = mFakeSelectBar.layoutParams as RelativeLayout.LayoutParams
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            mFakeSelectBar.layoutParams = params
            mSelectBar.visibility = View.INVISIBLE
            mFakeSelectBar.visibility = View.VISIBLE
        } else if (mSelectBarRect.top > pinnedBarRect.top) {
            mFakeSelectBar.visibility = View.INVISIBLE
            mSelectBar.visibility = View.VISIBLE
        }
    }

    companion object {
        const val SEARCH_WEB = 0
        const val SEARCH_DICTIONARY = 1
    }
}
