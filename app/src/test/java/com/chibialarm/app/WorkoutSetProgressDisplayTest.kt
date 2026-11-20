package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetProgressDisplayTest {
    private val optionalPlan = WorkoutSetTracker.Plan(
        requiredSets = 2,
        maximumSets = 3,
        targetMin = 8,
        targetMax = 12,
        unit = "lần",
        restSeconds = 45
    )

    @Test fun partialProgressCountsOnlyConfirmedRequiredSets() {
        val state = WorkoutSetProgressDisplay.from(optionalPlan, 1)
        assertEquals(2, state.progressMax)
        assertEquals(1, state.progress)
        assertEquals(0, state.optionalCompleted)
        assertEquals("Bắt buộc: 1/2 • Tùy chọn: 0/1", state.summary)
    }

    @Test fun optionalSetDoesNotIncreaseOrReduceRequiredProgress() {
        val state = WorkoutSetProgressDisplay.from(optionalPlan, 3)
        assertEquals(2, state.progressMax)
        assertEquals(2, state.progress)
        assertEquals(1, state.optionalCompleted)
        assertEquals(1, state.optionalTotal)
        assertTrue(state.summary.contains("Tùy chọn: 1/1"))
        assertTrue(state.contentDescription.contains("không bắt buộc"))
    }

    @Test fun requiredCompletionIsFullBeforeAnyOptionalSet() {
        val state = WorkoutSetProgressDisplay.from(optionalPlan, 2)
        assertEquals(2, state.progress)
        assertEquals(0, state.optionalCompleted)
        assertTrue(state.summary.startsWith("Bắt buộc: 2/2"))
    }

    @Test fun negativeAndExcessiveCountsAreClampedToThePlan() {
        val before = WorkoutSetProgressDisplay.from(optionalPlan, -5)
        val after = WorkoutSetProgressDisplay.from(optionalPlan, 99)
        assertEquals(0, before.progress)
        assertEquals(0, before.optionalCompleted)
        assertEquals(2, after.progress)
        assertEquals(1, after.optionalCompleted)
    }

    @Test fun plansWithoutOptionalSetsDoNotShowAnOptionalCounter() {
        val plan = WorkoutSetTracker.Plan(
            requiredSets = 3,
            maximumSets = 3,
            targetMin = 10,
            targetMax = 10,
            unit = "lần",
            restSeconds = 60
        )
        val state = WorkoutSetProgressDisplay.from(plan, 2)
        assertEquals(3, state.progressMax)
        assertEquals(2, state.progress)
        assertFalse(state.summary.contains("Tùy chọn"))
        assertFalse(state.contentDescription.contains("tùy chọn"))
    }
}
