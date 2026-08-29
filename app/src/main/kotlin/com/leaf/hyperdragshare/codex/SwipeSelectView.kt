package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import com.leaf.hyperdragshare.codex.BoomChipPage.BoomChip
import smartisanos.util.SidebarUtils
import kotlin.math.max
import kotlin.math.min

class SwipeSelectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val AUTO_SCROLL_DELAY = 25L
    private var mSelStart = 0
    private var mSelEnd = 0
    private var mStartBound = 0
    private var mEndBound = 0
    private var mLastTouchIndex = 0
    private var mStartIndex = 0
    private var mIsSelected = false
    private lateinit var mBoomPage: BoomChipPage
    private var mDragStarted = false
    private var mDeferInitialSelectionVisual = false
    private var mVisualSelectionApplied = false
    private var mAutoScrollTopInset = 0
    private var mAutoScrollBottomInset = 0
    private var mAutoScrollVelocity = 0

    private var mDragText: String? = null
    private val mStartDrag: Runnable = object : Runnable {
        override fun run() {
            if (!TextUtils.isEmpty(mDragText) && SidebarUtils.isSidebarShowing(context)) {
                mDragStarted = true
                SidebarUtils.dragText(this@SwipeSelectView, context, mDragText)
            }
        }
    }

    private val mAutoScroll: Runnable = object : Runnable {
        override fun run() {
            if (mAutoScrollVelocity != 0) {
                mBoomPage.mScroller.scrollBy(0, mAutoScrollVelocity)
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY)
            }
        }
    }

    fun setBoomPage(boomPage: BoomChipPage) {
        mBoomPage = boomPage
        mAutoScrollVelocity = 0
        val res = resources
        mAutoScrollTopInset = res.getDimensionPixelSize(R.dimen.auto_scroll_top)
        mAutoScrollBottomInset = res.getDimensionPixelSize(R.dimen.auto_scroll_bottom)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                mDeferInitialSelectionVisual = isAtScrollEdge()
                mVisualSelectionApplied = false
                val touchedChip = findChip(x, y, false)
                if (touchedChip != null) {
                    initSelection(touchedChip.index)
                    mIsSelected = !touchedChip.word.isSelected
                    if (!mDeferInitialSelectionVisual) {
                        applyInitialSelectionVisual(touchedChip)
                    }
                } else {
                    initSelection(-1)
                }
                mAutoScrollVelocity = 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (mLastTouchIndex != -1) {
                    scrollIfNeeded(ev.rawY.toInt())
                    val touchedChip = findChip(x, y, mSelEnd > mSelStart)
                    if (touchedChip != null && touchedChip.index != mLastTouchIndex) {
                        mVisualSelectionApplied = true
                        requestDisallowInterceptTouchEvent(true)
                        removeCallbacks(mStartDrag)
                        mLastTouchIndex = touchedChip.index
                        mStartBound = min(mStartBound, touchedChip.index)
                        mEndBound = max(mEndBound, touchedChip.index)
                        mSelStart = min(mStartIndex, touchedChip.index)
                        mSelEnd = max(mStartIndex, touchedChip.index)
                        performSelect(mIsSelected)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(mStartDrag)
                requestDisallowInterceptTouchEvent(false)
                if (mSelStart != -1) {
                    if (mSelStart == mSelEnd) {
                        performSelect(mIsSelected)
                    }
                    if (mIsSelected) {
                        mBoomPage.mBoomActionHandler.onSelect(mSelStart, mSelEnd)
                        if (mStartBound < mSelStart) {
                            mBoomPage.mBoomActionHandler.deSelect(mStartBound, mSelStart - 1)
                        }
                        if (mSelEnd < mEndBound) {
                            mBoomPage.mBoomActionHandler.deSelect(mSelEnd + 1, mEndBound)
                        }
                    } else {
                        if (mStartBound < mSelStart) {
                            mBoomPage.mBoomActionHandler.onSelect(mStartBound, mSelStart - 1)
                        }
                        if (mSelEnd < mEndBound) {
                            mBoomPage.mBoomActionHandler.onSelect(mSelEnd + 1, mEndBound)
                        }
                        mBoomPage.mBoomActionHandler.deSelect(mSelStart, mSelEnd)
                    }
                }
                mAutoScrollVelocity = 0
                mDeferInitialSelectionVisual = false
                mVisualSelectionApplied = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(mStartDrag)
                requestDisallowInterceptTouchEvent(false)
                if (mVisualSelectionApplied && mSelStart != -1 && mSelStart == mSelEnd) {
                    if (!mDragStarted || !mBoomPage.mBoomActionHandler.hasSelection()) {
                        performSelect(!mIsSelected)
                    }
                }
                mDragStarted = false
                mAutoScrollVelocity = 0
                mDeferInitialSelectionVisual = false
                mVisualSelectionApplied = false
            }
            else -> {
                removeCallbacks(mStartDrag)
                mAutoScrollVelocity = 0
            }
        }
        return if (mSelStart != -1) true else super.onTouchEvent(ev)
    }

    private fun scrollIfNeeded(screenY: Int) {
        val scrollerLocation = IntArray(2)
        mBoomPage.mScroller.getLocationOnScreen(scrollerLocation)
        val scrollerTop = scrollerLocation[1]
        val scrollerBottom = scrollerTop + mBoomPage.mScroller.height
        val autoScrollTop = min(
            scrollerTop + mAutoScrollTopInset,
            scrollerTop + mBoomPage.mScroller.height / 2,
        )
        val autoScrollBottom = max(
            scrollerBottom - mAutoScrollBottomInset,
            scrollerTop + mBoomPage.mScroller.height / 2,
        )
        if (screenY < autoScrollTop) {
            if (mAutoScrollVelocity >= 0) {
                removeCallbacks(mAutoScroll)
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY)
            }
            mAutoScrollVelocity = (screenY - autoScrollTop) / 2
        } else if (screenY > autoScrollBottom) {
            if (mAutoScrollVelocity <= 0) {
                removeCallbacks(mAutoScroll)
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY)
            }
            mAutoScrollVelocity = (screenY - autoScrollBottom) / 2
        } else {
            mAutoScrollVelocity = 0
        }
    }

    private fun initSelection(value: Int) {
        mStartBound = value
        mEndBound = value
        mSelStart = value
        mSelEnd = value
        mStartIndex = value
        mLastTouchIndex = value
    }

    private fun applyInitialSelectionVisual(touchedChip: BoomChip) {
        mVisualSelectionApplied = true
        if (mIsSelected) {
            touchedChip.setSelected(true)
            if (!mBoomPage.mBoomActionHandler.hasSelection() &&
                SidebarUtils.isSidebarShowing(context)
            ) {
                mDragText = touchedChip.word.text.toString()
                postDelayed(mStartDrag, ViewConfiguration.getLongPressTimeout().toLong())
            }
        } else if (SidebarUtils.isSidebarShowing(context)) {
            mDragText = mBoomPage.mBoomActionHandler.getSelectedText()
            postDelayed(mStartDrag, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    private fun isAtScrollEdge(): Boolean =
        !mBoomPage.mScroller.canScrollVertically(-1) ||
            !mBoomPage.mScroller.canScrollVertically(1)

    private fun performSelect(isSelected: Boolean) {
        val startRow = mBoomPage.mLayout.getRowForIndex(mStartBound)
        val endRow = mBoomPage.mLayout.getRowForIndex(mEndBound)
        for (i in startRow..endRow) {
            val row = getChipRow(i) ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                val tag = child.tag
                if (tag is BoomChip) {
                    val index = tag.index
                    if (index < mStartBound) continue
                    if (index > mEndBound) return
                    if (index >= mSelStart && index <= mSelEnd) {
                        tag.setSelected(isSelected)
                    } else {
                        tag.setSelected(!isSelected)
                    }
                }
            }
        }
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val hitRect = Rect()
        view.getHitRect(hitRect)
        return hitRect.contains(x.toInt(), y.toInt())
    }

    private fun findChip(x: Float, y: Float, isSwiping: Boolean): BoomChip? {
        for (i in 0 until childCount) {
            val row = getChipRow(i) ?: continue
            if (isPointInsideView(x, y, row)) {
                val offsetX = (row.scrollX - row.left).toFloat()
                val offsetY = (row.scrollY - row.top).toFloat()
                val newX = x + offsetX
                val newY = y + offsetY - row.translationY
                for (j in row.childCount - 1 downTo 0) {
                    val child = row.getChildAt(j)
                    if (isSwiping && newX > child.x) {
                        val tag = child.tag
                        if (tag is BoomChip) {
                            return tag
                        }
                        return null
                    }
                    if (isPointInsideView(newX, newY, child)) {
                        val tag = child.tag
                        if (tag is BoomChip) {
                            return tag
                        }
                        return null
                    }
                }
                return null
            }
        }
        return null
    }

    private fun getChipRow(index: Int): LinearLayout? {
        val child = getChildAt(index)
        return if (child is LinearLayout) child else null
    }
}
