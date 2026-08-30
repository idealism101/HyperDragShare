package com.leaf.hyperdragshare.codex

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EvdevTouchParserTest {
    @Test
    fun emitsStableDownMoveAndUp() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }

        pointerDown(parser, 0, 42, 10, 20)
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, 12)
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1)
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)

        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
            ),
            actions,
        )
    }

    @Test
    fun secondPointerCancelsInsteadOfFinishingShare() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }

        pointerDown(parser, 0, 42, 10, 20)
        pointerDown(parser, 1, 43, 30, 40)

        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_CANCEL,
            ),
            actions,
        )
    }

    @Test
    fun adoptsATouchThatWasAlreadyDownWhenTheDeviceWasOpened() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }
        parser.adoptInProgressGesture()

        // Opening the device mid gesture only ever sees position updates: the tracking id and
        // the button edge of that touch were reported before the device existed for us.
        moveTo(parser, 10, 20)
        moveTo(parser, 12, 24)
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1)
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)

        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
            ),
            actions,
        )
    }

    @Test
    fun aTouchInProgressIsIgnoredWhenAdoptionWasNotArmed() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }

        moveTo(parser, 10, 20)
        moveTo(parser, 12, 24)

        assertEquals(emptyList<Int>(), actions)
    }

    @Test
    fun adoptionAppliesToOneGestureInsteadOfEveryLaterFrame() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }
        parser.adoptInProgressGesture()

        moveTo(parser, 10, 20)
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1)
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)
        // A position frame after the lift must not fabricate a second gesture.
        moveTo(parser, 30, 40)

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), actions)
    }

    @Test
    fun anAdoptedGestureStillCancelsOnASecondFinger() {
        val actions: MutableList<Int> = ArrayList()
        val parser = EvdevTouchParser { action, _, _ -> actions.add(action) }
        parser.adoptInProgressGesture()

        moveTo(parser, 10, 20)
        pointerDown(parser, 1, 43, 30, 40)

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), actions)
    }

    private companion object {
        private fun moveTo(parser: EvdevTouchParser, x: Int, y: Int) {
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, x)
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_Y, y)
            parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)
        }

        private fun pointerDown(
            parser: EvdevTouchParser,
            slot: Int,
            trackingId: Int,
            x: Int,
            y: Int,
        ) {
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_SLOT, slot)
            parser.consume(
                EvdevTouchParser.EV_ABS,
                EvdevTouchParser.ABS_MT_TRACKING_ID,
                trackingId,
            )
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, x)
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_Y, y)
            parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0)
        }
    }
}
