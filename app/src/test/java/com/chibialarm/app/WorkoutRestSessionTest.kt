package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutRestSessionTest {
    @Test fun restUsesSeparateCountdownAndPreservesExerciseTime() {
        val state = WorkoutRestSession.start(exerciseRemainingMs = 42_000L, restSeconds = 90)
        assertEquals(42_000L, state.exerciseRemainingMs)
        assertEquals(90_000L, state.restRemainingMs)
    }

    @Test fun restCanStartAfterExerciseTimerReminderExpires() {
        val state = WorkoutRestSession.start(exerciseRemainingMs = 0L, restSeconds = 30)
        assertEquals(0L, state.exerciseRemainingMs)
        assertEquals(30_000L, state.restRemainingMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRestLongerThanTenMinutes() {
        WorkoutRestSession.start(10_000L, 601)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidExerciseCountdown() {
        WorkoutRestSession.start(600_001L, 30)
    }
}
