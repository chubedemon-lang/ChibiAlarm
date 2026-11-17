package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(true, cue?.hasExplicitPhase)
        val speech = WorkoutRestStepPreview.spokenMessage(squat)
        assertTrue(speech!!.contains("Hạ hông"))
        assertTrue(speech.contains(cue!!.instruction))
    }

    @Test fun restStepCueFallsBackToSetupWithoutInventingAMovementPhase() {
        val plank = TrainingCatalog.allMoves.first { it.name == "Plank khuỷu hoặc plank gối" }
        val cue = WorkoutRestStepPreview.forMove(plank)
        assertEquals("Giữ thân thành đường thẳng", cue?.instruction)
        assertEquals(false, cue?.alternate)
        assertEquals(false, cue?.hasExplicitPhase)
        val speech = WorkoutRestStepPreview.spokenMessage(plank)
        assertTrue(speech!!.contains("Bước cần chú ý: Giữ thân thành đường thẳng"))
        assertFalse("A setup fallback must not invent a squat/standing phase", speech.contains("Hạ hông"))
    }

    @Test fun spokenCueIncludesTheExactNextRequiredSetTargetAndRest() {
        val squat = TrainingCatalog.allMoves.first { it.name == "Squat không tạ" }
        val speech = WorkoutRestStepPreview.spokenMessage(squat, plan, completedSets = 1)!!

        assertTrue(speech.contains("hiệp bắt buộc 2/3"))
        assertTrue(speech.contains("8 đến 12 lần"))
        assertTrue(speech.contains("45 giây"))
        assertTrue(speech.contains("Hạ hông"))
    }

    @Test fun spokenCueLabelsTheRemainingOptionalSetWithoutPressuringTheUser() {
        val squat = TrainingCatalog.allMoves.first { it.name == "Squat không tạ" }
        val speech = WorkoutRestStepPreview.spokenMessage(squat, plan, completedSets = 2)!!

        assertTrue(speech.contains("hiệp tùy chọn 3/3"))
        assertTrue(speech.contains("có thể bỏ qua"))
        assertFalse(speech.contains("hiệp bắt buộc 3/3"))
    }

    @Test fun timedMoveReadsItsCatalogDoseWithoutInventingSetCounts() {
        val timed = TrainingCatalog.allMoves.first { it.name == "Plank khuỷu hoặc plank gối" }
            .copy(dose = "3–5 phút")
        val speech = WorkoutRestStepPreview.spokenMessage(timed)!!

        assertTrue(speech.contains("Liều lượng theo giáo án: 3–5 phút"))
        assertFalse(speech.contains("hiệp"))
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
