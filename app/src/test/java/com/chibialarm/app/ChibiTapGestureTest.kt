package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChibiTapGestureTest {
    @Test fun validTapFiresOnlyOnce() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(160f, 110f))
        assertTrue(gesture.move(164f, 115f))
        assertTrue(gesture.end(164f, 115f))
        assertFalse(gesture.end(164f, 115f))
    }

    @Test fun scrollPastSlopCannotTurnIntoTapOnReturn() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(160f, 110f))
        assertFalse(gesture.move(160f, 130f))
        assertFalse(gesture.end(160f, 110f))
    }

    @Test fun cancelledAndMissingDownAreIgnored() {
        val gesture = ChibiTapGesture(12f)
        assertFalse(gesture.end(10f, 10f))
        assertTrue(gesture.begin(10f, 10f))
        gesture.cancel()
        assertFalse(gesture.end(10f, 10f))
    }

    @Test fun invalidCoordinatesCannotStartOrFinishGesture() {
        val gesture = ChibiTapGesture(8f)
        assertFalse(gesture.begin(Float.NaN, 10f))
        assertFalse(gesture.end(10f, 10f))
        assertTrue(gesture.begin(10f, 10f))
        assertFalse(gesture.move(Float.POSITIVE_INFINITY, 10f))
        assertFalse(gesture.end(10f, 10f))
        assertTrue(gesture.begin(10f, 10f))
        assertFalse(gesture.end(Float.NEGATIVE_INFINITY, 10f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTouchSlopIsRejected() {
        ChibiTapGesture(-1f)
    }
}
