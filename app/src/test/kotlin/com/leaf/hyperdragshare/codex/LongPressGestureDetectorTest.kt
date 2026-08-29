package com.leaf.hyperdragshare.codex

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LongPressGestureDetectorTest {
    @Test
    fun timeoutStartsCaptureAndKeepsLatestPointerForDrag() {
        val callback = FakeCallback()
        val scheduler = FakeScheduler()
        val detector = LongPressGestureDetector(
            500L, 10f, callback, { 0L }, scheduler,
        )

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 10f, 20f, 0L)
        scheduler.runPending()
        assertEquals(1, callback.longPressCount)
        assertTrue(detector.beginDragging(detector.currentGestureId()))
        detector.onPointerEvent(MotionEvent.ACTION_MOVE, 30f, 40f, 600L)
        detector.onPointerEvent(MotionEvent.ACTION_UP, 30f, 40f, 620L)

        assertEquals(2, callback.dragCount)
        assertEquals(LongPressGestureDetector.State.IDLE, detector.state())
    }

    @Test
    fun movementPastSlopCancelsBeforeTimeout() {
        val callback = FakeCallback()
        val scheduler = FakeScheduler()
        val detector = LongPressGestureDetector(
            500L, 10f, callback, { 0L }, scheduler,
        )

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 0f, 0f, 0L)
        detector.onPointerEvent(MotionEvent.ACTION_MOVE, 11f, 0f, 10L)
        scheduler.runPending()

        assertEquals(0, callback.longPressCount)
        assertEquals(LongPressGestureDetector.State.IGNORED_UNTIL_UP, detector.state())
    }

    @Test
    fun captureCompletionAfterUpCannotStartDragging() {
        val callback = FakeCallback()
        val scheduler = FakeScheduler()
        val detector = LongPressGestureDetector(
            500L, 10f, callback, { 0L }, scheduler,
        )

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 0f, 0f, 0L)
        scheduler.runPending()
        val gesture = detector.currentGestureId()
        detector.onPointerEvent(MotionEvent.ACTION_UP, 0f, 0f, 600L)

        assertFalse(detector.beginDragging(gesture))
        assertTrue(callback.cancelledCount > 0)
    }

    private class FakeCallback : LongPressGestureDetector.Callback {
        var longPressCount = 0
        var dragCount = 0
        var cancelledCount = 0

        override fun isLongPressAllowed(): Boolean = true

        override fun onLongPress(gestureId: Long, stableX: Float, stableY: Float) {
            longPressCount++
        }

        override fun onDrag(action: Int, x: Float, y: Float, eventTime: Long) {
            dragCount++
        }

        override fun onGestureCancelled(gestureId: Long) {
            cancelledCount++
        }
    }

    private class FakeScheduler : LongPressGestureDetector.Scheduler {
        var pending: Runnable? = null

        override fun postDelayed(runnable: Runnable, delayMillis: Long) {
            pending = runnable
        }

        override fun removeCallbacks(runnable: Runnable) {
            if (pending === runnable) {
                pending = null
            }
        }

        fun runPending() {
            val runnable = pending
            pending = null
            runnable?.run()
        }
    }
}
