package com.leaf.hyperdragshare.codex

import android.view.MotionEvent

/** Converts multi-touch evdev frames into one stable primary pointer stream. */
class EvdevTouchParser(private val listener: Listener) {
    fun interface Listener {
        fun onFrame(action: Int, rawX: Float, rawY: Float)
    }

    private val slotX = FloatArray(MAX_SLOTS)
    private val slotY = FloatArray(MAX_SLOTS)
    private val trackingId = IntArray(MAX_SLOTS)

    private var currentSlot = 0
    private var primarySlot = -1
    private var gestureActive = false
    private var buttonTouchSeen = false
    private var buttonTouchDown = false
    private var buttonDownEdge = false
    private var sawTrackingStart = false
    private var primaryLifted = false
    private var multiTouchDetected = false
    private var frameChanged = false
    private var ignoreUntilAllPointersUp = false
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    init {
        resetSlots()
    }

    fun consume(type: Int, code: Int, value: Int) {
        if (type == EV_ABS) {
            consumeAbsolute(code, value)
        } else if (type == EV_KEY && code == BTN_TOUCH) {
            buttonTouchSeen = true
            if (value != 0 && !buttonTouchDown) {
                buttonDownEdge = true
                clearCoordinates()
            }
            buttonTouchDown = value != 0
            frameChanged = true
        } else if (type == EV_SYN && code == SYN_REPORT) {
            emitFrame()
        }
    }

    fun cancel() {
        if (gestureActive && lastX.isFinite() && lastY.isFinite()) {
            listener.onFrame(MotionEvent.ACTION_CANCEL, lastX, lastY)
        }
        resetGesture()
        ignoreUntilAllPointersUp = false
        resetSlots()
    }

    private fun consumeAbsolute(code: Int, value: Int) {
        if (code == ABS_MT_SLOT) {
            currentSlot = Math.max(0, Math.min(MAX_SLOTS - 1, value))
            return
        }
        if (code == ABS_MT_TRACKING_ID) {
            if (value < 0) {
                if (currentSlot == primarySlot) {
                    primaryLifted = true
                }
                trackingId[currentSlot] = -1
            } else {
                if (gestureActive && currentSlot != primarySlot) {
                    multiTouchDetected = true
                }
                trackingId[currentSlot] = value
                slotX[currentSlot] = Float.NaN
                slotY[currentSlot] = Float.NaN
                sawTrackingStart = true
            }
            frameChanged = true
            return
        }
        if (code == ABS_MT_POSITION_X) {
            slotX[currentSlot] = value.toFloat()
            frameChanged = true
        } else if (code == ABS_MT_POSITION_Y) {
            slotY[currentSlot] = value.toFloat()
            frameChanged = true
        }
    }

    private fun emitFrame() {
        if (!frameChanged) {
            return
        }
        val trackedCount = trackedPointerCount()
        if (ignoreUntilAllPointersUp) {
            if (trackedCount == 0 && (!buttonTouchSeen || !buttonTouchDown)) {
                ignoreUntilAllPointersUp = false
                clearGestureSignals()
            }
            finishFrame()
            return
        }

        var candidate = firstTrackedSlotWithCoordinates()
        if (candidate < 0 && buttonTouchSeen && buttonTouchDown) {
            candidate = firstSlotWithCoordinates()
        }

        if (!gestureActive) {
            if (trackedCount > 1) {
                ignoreUntilAllPointersUp = true
            } else if (candidate >= 0 && (sawTrackingStart || buttonDownEdge)) {
                primarySlot = candidate
                gestureActive = true
                setLast(candidate)
                listener.onFrame(MotionEvent.ACTION_DOWN, lastX, lastY)
            }
            finishFrame()
            return
        }

        if (trackedCount > 1 || multiTouchDetected) {
            emitCancelAndIgnore()
            finishFrame()
            return
        }

        val primaryTracked = primarySlot >= 0 && trackingId[primarySlot] >= 0
        val buttonReleased = buttonTouchSeen && !buttonTouchDown
        if (!primaryTracked || primaryLifted || buttonReleased) {
            emitUp()
            if (trackedCount > 0 || (buttonTouchSeen && buttonTouchDown)) {
                ignoreUntilAllPointersUp = true
            }
            finishFrame()
            return
        }

        if (hasCoordinates(primarySlot)) {
            val nextX = slotX[primarySlot]
            val nextY = slotY[primarySlot]
            if (nextX != lastX || nextY != lastY) {
                lastX = nextX
                lastY = nextY
                listener.onFrame(MotionEvent.ACTION_MOVE, lastX, lastY)
            }
        }
        finishFrame()
    }

    private fun emitUp() {
        if (gestureActive && lastX.isFinite() && lastY.isFinite()) {
            listener.onFrame(MotionEvent.ACTION_UP, lastX, lastY)
        }
        resetGesture()
    }

    private fun emitCancelAndIgnore() {
        if (gestureActive && lastX.isFinite() && lastY.isFinite()) {
            listener.onFrame(MotionEvent.ACTION_CANCEL, lastX, lastY)
        }
        resetGesture()
        ignoreUntilAllPointersUp = true
    }

    private fun setLast(slot: Int) {
        lastX = slotX[slot]
        lastY = slotY[slot]
    }

    private fun trackedPointerCount(): Int {
        var count = 0
        for (id in trackingId) {
            if (id >= 0) {
                count++
            }
        }
        return count
    }

    private fun firstTrackedSlotWithCoordinates(): Int {
        for (index in 0 until MAX_SLOTS) {
            if (trackingId[index] >= 0 && hasCoordinates(index)) {
                return index
            }
        }
        return -1
    }

    private fun firstSlotWithCoordinates(): Int {
        for (index in 0 until MAX_SLOTS) {
            if (hasCoordinates(index)) {
                return index
            }
        }
        return -1
    }

    private fun hasCoordinates(slot: Int): Boolean =
        slot >= 0 && slot < MAX_SLOTS && slotX[slot].isFinite() && slotY[slot].isFinite()

    private fun finishFrame() {
        frameChanged = false
        primaryLifted = false
        multiTouchDetected = false
        buttonDownEdge = false
        if (!gestureActive && !ignoreUntilAllPointersUp) {
            sawTrackingStart = false
        }
    }

    private fun clearGestureSignals() {
        sawTrackingStart = false
        buttonDownEdge = false
        primaryLifted = false
        multiTouchDetected = false
    }

    private fun resetGesture() {
        gestureActive = false
        primarySlot = -1
        primaryLifted = false
        lastX = Float.NaN
        lastY = Float.NaN
    }

    private fun clearCoordinates() {
        for (index in 0 until MAX_SLOTS) {
            slotX[index] = Float.NaN
            slotY[index] = Float.NaN
        }
    }

    private fun resetSlots() {
        for (index in 0 until MAX_SLOTS) {
            trackingId[index] = -1
            slotX[index] = Float.NaN
            slotY[index] = Float.NaN
        }
        currentSlot = 0
        buttonTouchSeen = false
        buttonTouchDown = false
        frameChanged = false
        clearGestureSignals()
    }

    companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val SYN_REPORT = 0
        const val BTN_TOUCH = 330
        const val ABS_MT_SLOT = 47
        const val ABS_MT_POSITION_X = 53
        const val ABS_MT_POSITION_Y = 54
        const val ABS_MT_TRACKING_ID = 57

        private const val MAX_SLOTS = 16
    }
}
