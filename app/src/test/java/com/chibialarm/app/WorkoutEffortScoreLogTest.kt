package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEffortScoreLogTest {
    private val moves = listOf(
        TrainingMove("Squat", "3 hiệp × 8–12 lần", "steps", "easy", "avoid"),
        TrainingMove("Plank", "2 × 15–30 giây", "steps", "easy", "avoid"),
        TrainingMove("Walk", "3–5 phút", "steps", "easy", "avoid")
    )

    @Test fun exactScoresRoundTripWithUnratedSetPlaceholders() {
        val confirmed = mapOf(0 to 3)
        val first = WorkoutEffortScoreLog.append(emptyMap(), 0, 0, 5, moves, confirmed)
        val complete = WorkoutEffortScoreLog.append(first, 0, 2, 8, moves, confirmed)

        assertEquals(mapOf(0 to listOf(5, null, 8)), complete)
        assertEquals("0:5.-.8", WorkoutEffortScoreLog.encode(complete, moves, confirmed))
        assertEquals(complete, WorkoutEffortScoreLog.decode(
            "0:5.-.8", moves, confirmed
        ))
        assertEquals(
            "Squat: hiệp 1: 5/10, hiệp 3: 8/10",
            WorkoutEffortScoreLog.summary(complete, moves, confirmed)
        )
    }

    @Test fun aManualCategoryAnswerDoesNotInventANumericScore() {
        val withoutScore = WorkoutEffortScoreLog.append(
            emptyMap(), 0, 0, null, moves, mapOf(0 to 1)
        )
        assertEquals(mapOf(0 to listOf<Int?>(null)), withoutScore)
        assertEquals("", WorkoutEffortScoreLog.encode(withoutScore, moves, mapOf(0 to 1)))
        assertTrue(WorkoutEffortScoreLog.summary(withoutScore, moves, mapOf(0 to 1)).isBlank())
    }

    @Test fun correctionClearsOnlyTheLatestNumericScore() {
        val confirmed = mapOf(0 to 2, 1 to 1)
        val current = mapOf(
            0 to listOf(5, 8),
            1 to listOf(4)
        )
        val corrected = WorkoutEffortScoreLog.correctLatest(current, 0, moves, confirmed)
        assertEquals(mapOf(0 to listOf(5, null), 1 to listOf(4)), corrected)
        assertEquals(mapOf(0 to listOf(5, 8), 1 to listOf(4)), current)
    }

    @Test fun malformedDuplicateUnconfirmedAndOutOfRangeScoresAreIgnored() {
        val confirmed = mapOf(0 to 2, 1 to 1)
        assertEquals(
            mapOf(0 to listOf(5, null)),
            WorkoutEffortScoreLog.decode(
                "0:5.-,0:8.9,1:11,2:4,broken",
                moves,
                confirmed
            )
        )
    }

    @Test fun rejectsInvalidScoresTimedMovesAndUnconfirmedSets() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortScoreLog.append(emptyMap(), 0, 0, 0, moves, mapOf(0 to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortScoreLog.append(emptyMap(), 0, 0, 11, moves, mapOf(0 to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortScoreLog.append(emptyMap(), 0, 1, 7, moves, mapOf(0 to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEffortScoreLog.append(emptyMap(), 2, 0, 7, moves, mapOf(2 to 1))
        }
    }
}
