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

    @Test fun previewUsesActualRemainingTimeAndLeavesCompletedStepsOut() {
        val steps = RemainingWorkoutDuration.preview(
            listOf("warmup", "squat", "stretch"), listOf(40, 60, 30),
            1, 25, Int.MAX_VALUE
        )
        assertEquals(listOf("squat", "stretch"), steps.map { it.name })
        assertEquals(listOf(25, 30), steps.map { it.beforeSeconds })
        assertEquals(listOf(12, 15), steps.map { it.afterSeconds })
    }

    @Test fun previewRespectsPreviouslyShortenedUpcomingSteps() {
        val steps = RemainingWorkoutDuration.preview(
            listOf("a", "b", "c"), listOf(60, 60, 60), 1, 20, 2
        )
        assertEquals(listOf(20, 30), steps.map { it.beforeSeconds })
        assertEquals(listOf(10, 30), steps.map { it.afterSeconds })
    }

    @Test fun previewNeverAddsTimeToNearlyFinishedExercise() {
        for (remaining in 0..5) {
            val steps = RemainingWorkoutDuration.preview(
                listOf("current", "next"), listOf(30, 40),
                0, remaining, Int.MAX_VALUE
            )
            assertEquals(remaining, steps.first().beforeSeconds)
            assertEquals(remaining.coerceAtMost((remaining / 2).coerceAtLeast(5)), steps.first().afterSeconds)
            assertEquals(20, steps.last().afterSeconds)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun previewRejectsMismatchedInputs() {
        RemainingWorkoutDuration.preview(listOf("a"), emptyList(), 0, 10, 0)
    }
}
