package com.chibialarm.app.fitness

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingWorkoutDurationTest {
    @Test fun keepsFinishedStepsUnchanged() {
        assertEquals(40, RemainingWorkoutDuration.seconds(40, 0, 2))
        assertEquals(30, RemainingWorkoutDuration.seconds(60, 1, 1))
        assertEquals(20, RemainingWorkoutDuration.seconds(40, 2, 1))
    }

    @Test fun clampsVeryShortStepsToFiveSeconds() {
        assertEquals(5, RemainingWorkoutDuration.seconds(4, 2, 1))
        assertEquals(5, RemainingWorkoutDuration.seconds(0, 2, 1))
    }

    @Test fun noAdjustmentKeepsOriginalDuration() {
        assertEquals(45, RemainingWorkoutDuration.seconds(45, 3, Int.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDuration() {
        RemainingWorkoutDuration.seconds(-1, 1, 0)
    }
}
