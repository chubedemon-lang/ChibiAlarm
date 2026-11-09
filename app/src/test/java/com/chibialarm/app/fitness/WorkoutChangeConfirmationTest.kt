package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test

class WorkoutChangeConfirmationTest {
    private val original = CoachSessionPlanner.Session(listOf(
        CoachSessionPlanner.SetPlan("squat", 12, 45),
        CoachSessionPlanner.SetPlan("pushup", 8, 60),
        CoachSessionPlanner.SetPlan("walk", 20, 30)
    ))

    @Test fun previewDoesNotChangeCompletedWorkOrOriginal() {
        val p = WorkoutChangeConfirmation.preview(original, 1, CoachSessionPlanner.Request.EASIER)
        assertTrue(p.changed)
        assertEquals(original.sets[0], p.after.sets[0])
        assertEquals(6, p.after.sets[1].reps)
        assertEquals(12, original.sets[0].reps)
        assertEquals(8, original.sets[1].reps)
    }

    @Test fun explicitAcceptanceAppliesOnlyToUnfinishedSets() {
        val p = WorkoutChangeConfirmation.preview(original, 1, CoachSessionPlanner.Request.SHORTER)
        val accepted = WorkoutChangeConfirmation.accept(p, original, 1)!!
        assertEquals(listOf("squat", "pushup"), accepted.sets.map { it.exerciseId })
        assertEquals(original.sets[0], accepted.sets[0])
    }

    @Test fun changedProgressInvalidatesPendingDialog() {
        val p = WorkoutChangeConfirmation.preview(original, 0, CoachSessionPlanner.Request.EASIER)
        assertNull(WorkoutChangeConfirmation.accept(p, original, 1))
    }

    @Test fun newerEditInvalidatesOldDialog() {
        val p = WorkoutChangeConfirmation.preview(original, 0, CoachSessionPlanner.Request.HARDER)
        val newer = CoachSessionPlanner.adjustRemaining(original, 0, CoachSessionPlanner.Request.MORE_REST)
        assertNull(WorkoutChangeConfirmation.accept(p, newer, 0))
    }

    @Test fun safetyStopCannotBeOverriddenByStaleConfirmation() {
        val p = WorkoutChangeConfirmation.preview(original, 0, CoachSessionPlanner.Request.HARDER)
        val stopped = CoachSessionPlanner.adjustRemaining(original, 0, CoachSessionPlanner.Request.PAIN)
        assertNull(WorkoutChangeConfirmation.accept(p, stopped, 0))
        assertTrue(stopped.pausedForSafety)
    }

    @Test fun noOpPreviewDoesNotInventProgress() {
        val p = WorkoutChangeConfirmation.preview(original, 3, CoachSessionPlanner.Request.HARDER)
        assertFalse(p.changed)
        assertEquals(original, WorkoutChangeConfirmation.accept(p, original, 3))
    }
}
