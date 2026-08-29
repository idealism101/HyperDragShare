package com.leaf.hyperdragshare.codex

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import kotlin.math.abs

/**
 * Created by denglinling on 16-11-11.
 */
class CustomScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {
    private var mOnScrollListener: OnScrollListener? = null
    private var mOnEdgeDragListener: OnEdgeDragListener? = null
    private var mDownX = 0f
    private var mLastY = 0f
    private var mDownY = 0f
    private var mEdgeOffset = 0f
    private var mEdgeDragging = false
    private var mEdgeDragEnabled = true

    @Suppress("unused")
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val mEdgeStartDistance: Float
    private val mTriggerDistance: Float
    private val mDampingDistance: Float

    init {
        val density = context.resources.displayMetrics.density
        mEdgeStartDistance = 2f * density
        mTriggerDistance = 88f * density
        mDampingDistance = 120f * density
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    fun setOnScrollListener(onScrollListener: OnScrollListener?) {
        mOnScrollListener = onScrollListener
    }

    fun setOnEdgeDragListener(onEdgeDragListener: OnEdgeDragListener?) {
        mOnEdgeDragListener = onEdgeDragListener
    }

    fun setEdgeDragEnabled(enabled: Boolean) {
        mEdgeDragEnabled = enabled
        if (!enabled) {
            resetEdgeDrag(false)
        }
    }

    interface OnEdgeDragListener {
        fun onEdgeDrag(offset: Float)

        fun onEdgeDragRelease(offset: Float, triggered: Boolean)
    }

    interface OnScrollListener {
        fun onScrollChanged()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!mEdgeDragEnabled) {
            return super.onInterceptTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = ev.x
                mDownY = ev.y
                mLastY = ev.y
                resetEdgeDrag(false)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mEdgeDragging) {
                    return true
                }
                val totalDx = ev.x - mDownX
                val totalDy = ev.y - mDownY
                if (abs(totalDy) >= mEdgeStartDistance &&
                    abs(totalDy) > abs(totalDx) &&
                    shouldStartEdgeDrag(totalDy)
                ) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetEdgeDrag(false)
            else -> {
                // Other pointer actions do not affect the edge drag.
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!mEdgeDragEnabled) {
            return super.onTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> mLastY = ev.y
            MotionEvent.ACTION_MOVE -> {
                val currentY = ev.y
                val dy = currentY - mLastY
                if (mEdgeDragging || shouldStartEdgeDrag(dy)) {
                    val nextOffset = mEdgeOffset + applyResistance(dy)
                    if (mEdgeDragging && crossesZero(mEdgeOffset, nextOffset)) {
                        mEdgeOffset = 0f
                        dispatchEdgeDrag()
                        mLastY = currentY
                        return true
                    }
                    mEdgeDragging = true
                    mEdgeOffset = nextOffset
                    dispatchEdgeDrag()
                    mLastY = currentY
                    return true
                }
                mLastY = currentY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mEdgeDragging || mEdgeOffset != 0f) {
                    val releaseOffset = mEdgeOffset
                    val triggered = abs(releaseOffset) >= mTriggerDistance
                    resetEdgeDrag(false)
                    mOnEdgeDragListener?.onEdgeDragRelease(releaseOffset, triggered)
                    return true
                }
            }
            else -> {
                // Other pointer actions do not affect the edge drag.
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        mOnScrollListener?.onScrollChanged()
    }

    private fun shouldStartEdgeDrag(dy: Float): Boolean {
        if (dy == 0f) {
            return false
        }
        if (dy > 0f) {
            return !canScrollVertically(-1)
        }
        return !canScrollVertically(1)
    }

    private fun applyResistance(delta: Float): Float =
        delta / (1f + abs(mEdgeOffset) / mDampingDistance)

    private fun crossesZero(oldOffset: Float, newOffset: Float): Boolean =
        (oldOffset > 0f && newOffset <= 0f) || (oldOffset < 0f && newOffset >= 0f)

    private fun dispatchEdgeDrag() {
        mOnEdgeDragListener?.onEdgeDrag(mEdgeOffset)
    }

    private fun resetEdgeDrag(notify: Boolean) {
        mEdgeDragging = false
        mEdgeOffset = 0f
        if (notify) {
            dispatchEdgeDrag()
        }
    }
}
