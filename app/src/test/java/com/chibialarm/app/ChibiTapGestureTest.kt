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

    @Test fun diagonalScrollBeyondCircularSlopCannotPetEvenIfEachAxisIsInsideSlop() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(160f, 110f))
        assertFalse(gesture.move(169f, 119f))
        assertFalse(gesture.end(160f, 110f))
    }

    @Test fun radialBoundaryAndShortDiagonalTapRemainValid() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(0f, 0f))
        assertTrue(gesture.end(12f, 0f))
        assertTrue(gesture.begin(0f, 0f))
        assertTrue(gesture.end(6f, 8f))
    }

    @Test fun largeFiniteCoordinatesCannotOverflowSlopComparison() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(Float.MAX_VALUE, Float.MAX_VALUE))
        assertFalse(gesture.end(-Float.MAX_VALUE, -Float.MAX_VALUE))
    }


    @Test fun zeroSlopOnlyAcceptsExactlyStationaryTap() {
        val gesture = ChibiTapGesture(0f)
        assertTrue(gesture.begin(5f, 8f))
        assertTrue(gesture.end(5f, 8f))
        assertTrue(gesture.begin(5f, 8f))
        assertFalse(gesture.end(5.001f, 8f))
    }

    @Test fun newDownInvalidatesPriorGestureAndDoesNotReuseItsOrigin() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(10f, 10f))
        assertTrue(gesture.begin(100f, 100f))
        assertFalse(gesture.end(10f, 10f))
        assertTrue(gesture.begin(100f, 100f))
        assertTrue(gesture.end(105f, 105f))
    }

    @Test fun invalidNewDownCancelsPreviousActiveGesture() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(10f, 10f))
        assertFalse(gesture.begin(Float.NaN, 10f))
        assertFalse(gesture.end(10f, 10f))
    }


    @Test fun cancelledGestureCanRecoverOnNextValidDown() {
        val gesture = ChibiTapGesture(12f)
        assertTrue(gesture.begin(10f, 10f))
        assertFalse(gesture.move(40f, 40f))
        assertFalse(gesture.end(10f, 10f))
        assertTrue(gesture.begin(50f, 50f))
        assertTrue(gesture.end(54f, 54f))
    }

    @Test fun repeatedCancelAndMoveWithoutDownNeverProduceTap() {
        val gesture = ChibiTapGesture(12f)
        gesture.cancel()
        gesture.cancel()
        assertFalse(gesture.move(0f, 0f))
        assertFalse(gesture.end(0f, 0f))
        assertTrue(gesture.begin(0f, 0f))
        gesture.cancel()
        assertFalse(gesture.move(0f, 0f))
        assertFalse(gesture.end(0f, 0f))
    }

    @Test fun finiteSubpixelMovementAndNegativeCoordinatesAreHandled() {
        val gesture = ChibiTapGesture(0.5f)
        assertTrue(gesture.begin(-10f, -20f))
        assertTrue(gesture.move(-9.7f, -19.8f))
        assertTrue(gesture.end(-9.7f, -19.8f))
        assertTrue(gesture.begin(-10f, -20f))
        assertFalse(gesture.end(-9.6f, -19.6f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteTouchSlopIsRejected() {
        ChibiTapGesture(Float.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTouchSlopIsRejected() {
        ChibiTapGesture(-1f)
    }
}
