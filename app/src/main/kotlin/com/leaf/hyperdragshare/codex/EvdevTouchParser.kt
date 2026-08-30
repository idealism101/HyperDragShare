package com.leaf.hyperdragshare.codex

import android.view.MotionEvent

/** Converts multi-touch evdev frames into one stable primary pointer stream. */
internal class EvdevTouchParser(private val listener: Listener) {
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
    private var adoptInProgress = false
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

    /**
     * Lets the next frame start a gesture from a touch that was already down when the device was
     * opened. Attaching mid gesture never sees the tracking start or the `BTN_TOUCH` edge that
     * normally begins one, so without this the whole gesture in progress would be dropped and the
     * pointer would only be followed again after the user lifted and pressed once more.
     */
    fun adoptInProgressGesture() {
        adoptInProgress = true
    }

    fun cancel() {
        if (gestureActive && lastX.isFinite() && lastY.isFinite()) {
            listener.onFrame(MotionEvent.ACTION_CANCEL, lastX, lastY)
        }
        resetGesture()
        ignoreUntilAllPointersUp = false
        adoptInProgress = false
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
        // A touch that was already down reports no tracking id and no button edge of its own, so
        // a bare coordinate pair is the only evidence it exists. A released button rules it out.
        val adopting = !gestureActive && adoptInProgress && candidate < 0 && trackedCount == 0 &&
            !(buttonTouchSeen && !buttonTouchDown)
        if (adopting) {
            candidate = firstSlotWithCoordinates()
        }

        if (!gestureActive) {
            if (trackedCount > 1) {
                ignoreUntilAllPointersUp = true
            } else if (candidate >= 0 && (sawTrackingStart || buttonDownEdge || adopting)) {
                if (candidate >= 0 && trackingId[candidate] < 0) {
                    // The real tracking id was reported before the device was opened, so a
                    // placeholder keeps the lift and second finger checks below working until the
                    // kernel reports -1 for this slot.
                    trackingId[candidate] = ADOPTED_TRACKING_ID
                }
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
        if (gestureActive) {
            // Once a gesture is being tracked the parser is in step with the kernel again.
            adoptInProgress = false
        }
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

        /** Stands in for the tracking id of an adopted touch, which was never reported. */
        private const val ADOPTED_TRACKING_ID = Int.MAX_VALUE
    }
}
