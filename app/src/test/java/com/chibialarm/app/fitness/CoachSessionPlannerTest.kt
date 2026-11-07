package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test

class CoachSessionPlannerTest {
    private val initial = CoachSessionPlanner.Session(listOf(
        CoachSessionPlanner.SetPlan("squat", 12, 45),
        CoachSessionPlanner.SetPlan("pushup", 8, 60),
        CoachSessionPlanner.SetPlan("stretch", 10, 20)
    ))

    @Test fun easierWorkoutReducesRepsAndAddsRest() {
        val adjusted = CoachSessionPlanner.adjust(initial, CoachSessionPlanner.Request.EASIER)
        assertEquals(listOf(9, 6, 7), adjusted.sets.map { it.reps })
        assertEquals(listOf(65, 80, 40), adjusted.sets.map { it.restSeconds })
        assertEquals(12, initial.sets.first().reps)
    }

    @Test fun shorterWorkoutKeepsOrderAndAtLeastFirstSet() {
        val adjusted = CoachSessionPlanner.adjust(initial, CoachSessionPlanner.Request.SHORTER)
        assertEquals(listOf("squat", "stretch"), adjusted.sets.map { it.exerciseId })
    }

    @Test fun painPausesAndCannotBeOverriddenByResumeOrHarder() {
        val paused = CoachSessionPlanner.adjust(initial, CoachSessionPlanner.Request.PAIN)
        assertTrue(paused.pausedForSafety)
        assertEquals(paused, CoachSessionPlanner.adjust(paused, CoachSessionPlanner.Request.RESUME))
        assertEquals(paused, CoachSessionPlanner.adjust(paused, CoachSessionPlanner.Request.HARDER))
    }

    @Test fun repeatedAdjustmentsStayWithinBounds() {
        var session = CoachSessionPlanner.Session(listOf(CoachSessionPlanner.SetPlan("walk", 1, 590)))
        repeat(100) { session = CoachSessionPlanner.adjust(session, CoachSessionPlanner.Request.MORE_REST) }
        assertEquals(600, session.sets.single().restSeconds)
        repeat(100) { session = CoachSessionPlanner.adjust(session, CoachSessionPlanner.Request.EASIER) }
        assertEquals(1, session.sets.single().reps)
        repeat(100) { session = CoachSessionPlanner.adjust(session, CoachSessionPlanner.Request.HARDER) }
        assertEquals(100, session.sets.single().reps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRepetitions() {
        CoachSessionPlanner.SetPlan("walk", 0, 30)
    }

    @Test fun midSessionEasierPreservesCompletedSets() {
        val changed = CoachSessionPlanner.adjustRemaining(initial, 1, CoachSessionPlanner.Request.EASIER)
        assertEquals(initial.sets.first(), changed.sets.first())
        assertEquals(listOf(12, 6, 7), changed.sets.map { it.reps })
        assertEquals(listOf(45, 80, 40), changed.sets.map { it.restSeconds })
    }

    @Test fun midSessionShorterKeepsCompletedAndNextSet() {
        val changed = CoachSessionPlanner.adjustRemaining(initial, 1, CoachSessionPlanner.Request.SHORTER)
        assertEquals(listOf("squat", "pushup"), changed.sets.map { it.exerciseId })
    }

    @Test fun painStillPausesWhenAllSetsFinished() {
        val changed = CoachSessionPlanner.adjustRemaining(initial, 3, CoachSessionPlanner.Request.PAIN)
        assertTrue(changed.pausedForSafety)
        assertEquals(initial.sets, changed.sets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCompletedCountIsRejected() {
        CoachSessionPlanner.adjustRemaining(initial, 4, CoachSessionPlanner.Request.EASIER)
    }
}
