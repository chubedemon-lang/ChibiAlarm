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
    @Test fun restStepCueChoosesTheFirstUnambiguousMovementPhase() {
        val squat = TrainingCatalog.allMoves.first { it.name == "Squat không tạ" }
        val cue = WorkoutRestStepPreview.forMove(squat)
        assertEquals("hạ xuống theo khả năng", cue?.instruction)
        assertEquals(true, cue?.alternate)
    }

    @Test fun restStepCueFallsBackToSetupWithoutInventingAMovementPhase() {
        val plank = TrainingCatalog.allMoves.first { it.name == "Plank khuỷu hoặc plank gối" }
        val cue = WorkoutRestStepPreview.forMove(plank)
        assertEquals("Giữ thân thành đường thẳng", cue?.instruction)
        assertEquals(false, cue?.alternate)
    }

    @Test fun restStepCueDoesNotCreateAnInstructionForBlankCatalogText() {
        val move = TrainingMove(
            name = "Động tác thử",
            dose = "2 hiệp × 8 lần",
            steps = "",
            easier = "",
            avoid = ""
        )
        assertNull(WorkoutRestStepPreview.forMove(move))
    }

}
