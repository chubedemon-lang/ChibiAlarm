package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRestPosePreviewTest {
    private val plan = WorkoutSetTracker.Plan(
        requiredSets = 2,
        maximumSets = 3,
        targetMin = 8,
        targetMax = 12,
        unit = "lần",
        restSeconds = 45
    )

    @Test fun keepsCurrentMoveWhileAnotherRequiredSetRemains() {
        assertEquals(1, WorkoutRestPosePreview.nextMoveIndex(1, 4, 1, plan))
    }

    @Test fun keepsCurrentMoveForAnOptionalSetUntilTheMaximumIsReached() {
        assertEquals(1, WorkoutRestPosePreview.nextMoveIndex(1, 4, 2, plan))
    }

    @Test fun advancesAfterTheMaximumSetCountIsReached() {
        assertEquals(2, WorkoutRestPosePreview.nextMoveIndex(1, 4, 3, plan))
    }

    @Test fun timedMoveAdvancesWithoutInventingSetCounts() {
        assertEquals(2, WorkoutRestPosePreview.nextMoveIndex(1, 4, 0, null))
    }

    @Test fun clampsExcessCompletedSetsAndKeepsTheLastMoveAtProgramEnd() {
        assertEquals(3, WorkoutRestPosePreview.nextMoveIndex(3, 4, 99, plan))
    }

    @Test fun invalidIndexOrEmptyProgramHasNoPreview() {
        assertNull(WorkoutRestPosePreview.nextMoveIndex(-1, 4, 0, plan))
        assertNull(WorkoutRestPosePreview.nextMoveIndex(4, 4, 0, plan))
        assertNull(WorkoutRestPosePreview.nextMoveIndex(0, 0, 0, plan))
    }
}
