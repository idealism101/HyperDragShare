package com.leaf.hyperdragshare.codex

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.max

/** Pure long-press state machine. Content capture is owned by its caller. */
class LongPressGestureDetector(
    timeoutMillis: Long,
    touchSlop: Float,
    private val callback: Callback?,
    clock: Clock?,
    scheduler: Scheduler?,
) {
    enum class State {
        IDLE,
        PENDING_LONG_PRESS,
        CAPTURING_CONTENT,
        DRAGGING,
        IGNORED_UNTIL_UP,
    }

    interface Callback {
        fun isLongPressAllowed(): Boolean

        fun onLongPress(gestureId: Long, stableX: Float, stableY: Float)

        fun onDrag(action: Int, x: Float, y: Float, eventTime: Long)

        fun onGestureCancelled(gestureId: Long)
    }

    fun interface Clock {
        fun now(): Long
    }

    interface Scheduler {
        fun postDelayed(runnable: Runnable, delayMillis: Long)

        fun removeCallbacks(runnable: Runnable)
    }

    @Suppress("unused")
    private val clock: Clock = clock ?: Clock { SystemClock.uptimeMillis() }
    private val scheduler: Scheduler = scheduler
        ?: HandlerScheduler(Handler(Looper.getMainLooper()))
    private val timeoutMillis: Long = max(1L, timeoutMillis)
    private val touchSlop: Float = max(0f, touchSlop)
    private val timeoutRunnable = Runnable { onTimeout() }

    private var state = State.IDLE
    private var gestureId: Long = 0
    private var downX = 0f
    private var downY = 0f
    private var latestX = 0f
    private var latestY = 0f

    constructor(
        timeoutMillis: Long,
        touchSlop: Float,
        callback: Callback?,
    ) : this(
        timeoutMillis,
        touchSlop,
        callback,
        Clock { SystemClock.uptimeMillis() },
        HandlerScheduler(Handler(Looper.getMainLooper())),
    )

    @Synchronized
    fun onPointerEvent(action: Int, x: Float, y: Float, eventTime: Long) {
        if (!x.isFinite() || !y.isFinite()) {
            return
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                cancelCurrentLocked()
                gestureId++
                downX = x
                downY = y
                latestX = x
                latestY = y
                if (callback != null && callback.isLongPressAllowed()) {
                    state = State.PENDING_LONG_PRESS
                    scheduler.postDelayed(timeoutRunnable, timeoutMillis)
                } else {
                    state = State.IGNORED_UNTIL_UP
                }
                return
            }
            MotionEvent.ACTION_MOVE -> {
                latestX = x
                latestY = y
                if (state == State.PENDING_LONG_PRESS &&
                    distanceSquared(x, y, downX, downY) > touchSlop * touchSlop
                ) {
                    scheduler.removeCallbacks(timeoutRunnable)
                    state = State.IGNORED_UNTIL_UP
                } else if (state == State.DRAGGING && callback != null) {
                    callback.onDrag(action, x, y, eventTime)
                }
                return
            }
            MotionEvent.ACTION_UP -> {
                latestX = x
                latestY = y
                if (state == State.DRAGGING && callback != null) {
                    callback.onDrag(action, x, y, eventTime)
                } else if (state == State.PENDING_LONG_PRESS || state == State.CAPTURING_CONTENT) {
                    notifyCancelledLocked()
                }
                scheduler.removeCallbacks(timeoutRunnable)
                state = State.IDLE
                return
            }
            MotionEvent.ACTION_CANCEL -> {
                latestX = x
                latestY = y
                if (state != State.IDLE) {
                    notifyCancelledLocked()
                }
                scheduler.removeCallbacks(timeoutRunnable)
                state = State.IDLE
                return
            }
            else -> return
        }
    }

    @Synchronized
    fun beginDragging(completedGestureId: Long): Boolean {
        if (state != State.CAPTURING_CONTENT || completedGestureId != gestureId) {
            return false
        }
        state = State.DRAGGING
        return true
    }

    @Synchronized
    fun captureFailed(completedGestureId: Long) {
        if (state == State.CAPTURING_CONTENT && completedGestureId == gestureId) {
            state = State.IGNORED_UNTIL_UP
        }
    }

    @Synchronized
    fun cancel() {
        if (state != State.IDLE) {
            notifyCancelledLocked()
        }
        scheduler.removeCallbacks(timeoutRunnable)
        state = State.IDLE
    }

    @Synchronized
    fun state(): State = state

    @Synchronized
    fun currentGestureId(): Long = gestureId

    @Synchronized
    fun isCapturing(expectedGestureId: Long): Boolean =
        state == State.CAPTURING_CONTENT && gestureId == expectedGestureId

    @Synchronized
    fun latestX(): Float = latestX

    @Synchronized
    fun latestY(): Float = latestY

    @Synchronized
    private fun onTimeout() {
        if (state != State.PENDING_LONG_PRESS) {
            return
        }
        if (callback == null || !callback.isLongPressAllowed()) {
            state = State.IGNORED_UNTIL_UP
            return
        }
        state = State.CAPTURING_CONTENT
        callback.onLongPress(gestureId, latestX, latestY)
    }

    private fun cancelCurrentLocked() {
        if (state != State.IDLE) {
            notifyCancelledLocked()
        }
        scheduler.removeCallbacks(timeoutRunnable)
        state = State.IDLE
    }

    private fun notifyCancelledLocked() {
        callback?.onGestureCancelled(gestureId)
    }

    private class HandlerScheduler(private val handler: Handler) : Scheduler {
        override fun postDelayed(runnable: Runnable, delayMillis: Long) {
            handler.postDelayed(runnable, delayMillis)
        }

        override fun removeCallbacks(runnable: Runnable) {
            handler.removeCallbacks(runnable)
        }
    }

    companion object {
        private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x1 - x2
            val dy = y1 - y2
            return dx * dx + dy * dy
        }
    }
}
