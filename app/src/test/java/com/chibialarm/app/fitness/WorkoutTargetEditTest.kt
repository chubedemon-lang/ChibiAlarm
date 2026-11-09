package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test

class WorkoutTargetEditTest {
    private val original = CoachSessionPlanner.Session(listOf(
        CoachSessionPlanner.SetPlan("squat", 12, 45),
        CoachSessionPlanner.SetPlan("walk", 20, 30),
        CoachSessionPlanner.SetPlan("squat", 10, 60)
    ))

    @Test fun editsOnlyUnfinishedMatchingExerciseAfterConfirmation() {
        val target = WorkoutTargetEdit.Target("squat", reps = 6, restSeconds = 90)
        val preview = WorkoutTargetEdit.preview(original, 1, target)
        assertTrue(preview.changed)
        assertEquals(original.sets, original.sets)
        assertEquals(original.sets[0], preview.after.sets[0])
        assertEquals(original.sets[1], preview.after.sets[1])
        assertEquals(6, preview.after.sets[2].reps)
        assertEquals(90, preview.after.sets[2].restSeconds)
        assertEquals(preview.after, WorkoutTargetEdit.accept(preview, original, 1))
    }

    @Test fun restOnlyDoesNotChangeReps() {
        val preview = WorkoutTargetEdit.preview(original, 0,
            WorkoutTargetEdit.Target("walk", restSeconds = 75))
        assertEquals(20, preview.after.sets[1].reps)
        assertEquals(75, preview.after.sets[1].restSeconds)
    }

    @Test fun staleProgressOrChangedPlanCannotApply() {
        val preview = WorkoutTargetEdit.preview(original, 0,
            WorkoutTargetEdit.Target("squat", reps = 5))
        assertNull(WorkoutTargetEdit.accept(preview, original, 1))
        val newer = original.copy(sets = original.sets.dropLast(1))
        assertNull(WorkoutTargetEdit.accept(preview, newer, 0))
    }

    @Test fun painStopBlocksPreviewAndAcceptance() {
        val preview = WorkoutTargetEdit.preview(original, 0,
            WorkoutTargetEdit.Target("squat", reps = 5))
        val stopped = original.copy(pausedForSafety = true)
        assertNull(WorkoutTargetEdit.accept(preview, stopped, 0))
        assertFalse(WorkoutTargetEdit.preview(stopped, 0,
            WorkoutTargetEdit.Target("squat", reps = 5)).changed)
    }

    @Test fun invalidTargetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTargetEdit.Target("squat", reps = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTargetEdit.Target("squat", restSeconds = 601)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTargetEdit.Target(" ", reps = 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTargetEdit.Target("squat")
        }
    }

    @Test fun missingExerciseIsNoOp() {
        val preview = WorkoutTargetEdit.preview(original, 1,
            WorkoutTargetEdit.Target("pushup", reps = 8))
        assertFalse(preview.changed)
        assertEquals(original, WorkoutTargetEdit.accept(preview, original, 1))
    }
}
