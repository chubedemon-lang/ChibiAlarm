package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutCompletionTrackerTest {
    @Test fun timerCompletionAddsOneDistinctMove() {
        val first = WorkoutCompletionTracker.complete(emptySet(), 0, 4)
        val repeated = WorkoutCompletionTracker.complete(first, 0, 4)
        val second = WorkoutCompletionTracker.complete(repeated, 1, 4)
        assertEquals(setOf(0), first)
        assertEquals(setOf(0), repeated)
        assertEquals(setOf(0, 1), second)
    }

    @Test fun oneCompletedMoveCannotBeLoggedEvenAfterLongElapsedTime() {
        val completed = WorkoutCompletionTracker.complete(emptySet(), 0, 4)
        assertFalse(WorkoutCompletionTracker.canLog(completed, 4, 600_000L))
    }

    @Test fun twoDistinctCompletedMovesAndOneActiveMinuteCanBeLogged() {
        val first = WorkoutCompletionTracker.complete(emptySet(), 0, 4)
        val two = WorkoutCompletionTracker.complete(first, 1, 4)
        assertTrue(WorkoutCompletionTracker.canLog(two, 4, 60_000L))
        assertTrue(WorkoutCompletionTracker.canLog(two, 4, 120_000L))
    }

    @Test fun elapsedTimeAloneCannotReplaceCompletedMoves() {
        assertFalse(WorkoutCompletionTracker.canLog(emptySet(), 4, 600_000L))
        val two = setOf(0, 1)
        assertFalse(WorkoutCompletionTracker.canLog(two, 4, 59_999L))
    }

    @Test fun checkpointRoundTripIsSortedAndDeduplicated() {
        val completed = setOf(3, 1, 2)
        val encoded = WorkoutCompletionTracker.encode(completed, 4)
        assertEquals("1,2,3", encoded)
        assertEquals(completed, WorkoutCompletionTracker.decode(encoded, 4))
    }

    @Test fun malformedAndOutOfRangeCheckpointValuesAreIgnored() {
        assertEquals(setOf(0, 2),
            WorkoutCompletionTracker.decode("0,garbage,-1,2,99,2", 4))
        assertTrue(WorkoutCompletionTracker.decode(null, 4).isEmpty())
        assertTrue(WorkoutCompletionTracker.decode("", 4).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotCompleteIndexOutsidePlan() {
        WorkoutCompletionTracker.complete(emptySet(), 4, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedPlans() {
        WorkoutCompletionTracker.decode("0", 501)
    }
}
