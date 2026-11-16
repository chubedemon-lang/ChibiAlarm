package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEffortLogTest {
    private val moves = listOf(
        TrainingMove("Squat", "3 hiệp × 8–12 lần", "steps", "easy", "avoid"),
        TrainingMove("Plank", "2 × 15–30 giây", "steps", "easy", "avoid"),
        TrainingMove("Walk", "3–5 phút", "steps", "easy", "avoid")
    )

    @Test fun recordsOneSelfReportedEffortLevelForEachConfirmedSet() {
        val confirmed = mapOf(0 to 2)
        val first = WorkoutEffortLog.append(
            emptyMap(), 0, 0, WorkoutEffortFeedback.Level.HARD, moves, confirmed
        )
        val second = WorkoutEffortLog.append(
            first, 0, 1, WorkoutEffortFeedback.Level.MODERATE, moves, confirmed
        )
        assertEquals(
            listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.MODERATE),
            second[0]
        )
    }

    @Test fun sameAnswerIsIdempotentButConflictingReplacementIsRejected() {
        val confirmed = mapOf(0 to 1)
        val first = WorkoutEffortLog.append(
            emptyMap(), 0, 0, WorkoutEffortFeedback.Level.EASY, moves, confirmed
        )
        assertEquals(first, WorkoutEffortLog.append(
            first, 0, 0, WorkoutEffortFeedback.Level.EASY, moves, confirmed
        ))
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortLog.append(
                first, 0, 0, WorkoutEffortFeedback.Level.VERY_HARD, moves, confirmed
            )
        }
    }

    @Test fun checkpointRoundTripPreservesUnratedPlaceholders() {
        val confirmed = mapOf(0 to 3)
        val progress = mapOf(
            0 to listOf(
                WorkoutEffortFeedback.Level.HARD,
                null,
                WorkoutEffortFeedback.Level.EASY
            )
        )
        val encoded = WorkoutEffortLog.encode(progress, moves, confirmed)
        assertEquals("0:HARD.-.EASY", encoded)
        assertEquals(progress, WorkoutEffortLog.decode(encoded, moves, confirmed))
    }

    @Test fun malformedDuplicateAndUnconfirmedEntriesAreIgnored() {
        val confirmed = mapOf(0 to 1, 1 to 1)
        val decoded = WorkoutEffortLog.decode(
            "0:HARD,0:EASY,1:VERY_HARD.MODERATE,2:EASY,4:BAD,broken",
            moves,
            confirmed
        )
        assertEquals(
            mapOf(
                0 to listOf(WorkoutEffortFeedback.Level.HARD),
                1 to listOf(null)
            ),
            decoded
        )
    }

    @Test fun summaryNamesOnlyExplicitlyRatedConfirmedSets() {
        val confirmed = mapOf(0 to 2, 1 to 1)
        val progress = mapOf(
            0 to listOf(WorkoutEffortFeedback.Level.HARD, null),
            1 to listOf(WorkoutEffortFeedback.Level.MODERATE)
        )
        assertEquals(
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Plank: hiệp 1: Vừa sức",
            WorkoutEffortLog.summary(progress, moves, confirmed)
        )
        assertTrue(WorkoutEffortLog.summary(emptyMap(), moves, confirmed).isBlank())
    }

    @Test fun rejectsTimedExercisesAndUnconfirmedSets() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortLog.append(
                emptyMap(), 2, 0, WorkoutEffortFeedback.Level.EASY, moves, mapOf(2 to 1)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortLog.append(
                emptyMap(), 0, 1, WorkoutEffortFeedback.Level.EASY, moves, mapOf(0 to 1)
            )
        }
    }
}
