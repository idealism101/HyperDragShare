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

    private companion object {
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
