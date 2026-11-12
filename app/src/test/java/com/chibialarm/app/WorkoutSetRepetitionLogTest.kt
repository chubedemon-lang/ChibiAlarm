package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutSetRepetitionLogTest {
    private val moves = listOf(
        TrainingMove("Squat", "2 hiệp × 8–12 lần", "steps", "easy", "avoid"),
        TrainingMove("Plank", "2 × 15–30 giây", "steps", "easy", "avoid"),
        TrainingMove("Walk", "3–5 phút", "steps", "easy", "avoid")
    )

    @Test fun appendsSelfReportedValuesWithoutChangingOtherMoves() {
        val plan = WorkoutSetTracker.parse(moves[0].dose)!!
        val first = WorkoutSetRepetitionLog.append(emptyMap(), 0, 0, 9, plan)
        val second = WorkoutSetRepetitionLog.append(first, 0, 1, 10, plan)
        val timed = WorkoutSetTracker.parse(moves[1].dose)!!
        val third = WorkoutSetRepetitionLog.append(second, 1, 0, 22, timed)
        assertEquals(listOf(9, 10), third[0])
        assertEquals(listOf(22), third[1])
        assertEquals(emptyList<Int>(), third[2] ?: emptyList())
    }

    @Test fun rejectsValuesOutsideTheExerciseUnitBounds() {
        val reps = WorkoutSetTracker.parse(moves[0].dose)!!
        val seconds = WorkoutSetTracker.parse(moves[1].dose)!!
        assertThrows(IllegalArgumentException::class.java) { WorkoutSetRepetitionLog.append(emptyMap(), 0, 0, 201, reps) }
        assertThrows(IllegalArgumentException::class.java) { WorkoutSetRepetitionLog.append(emptyMap(), 1, 0, 601, seconds) }
    }

    @Test fun rejectsMoreReportedSetsThanThePrescriptionAllows() {
        val plan = WorkoutSetTracker.parse("1 hiệp × 8 lần")!!
        val one = WorkoutSetRepetitionLog.append(emptyMap(), 0, 0, 8, plan)
        assertThrows(IllegalArgumentException::class.java) { WorkoutSetRepetitionLog.append(one, 0, 1, 9, plan) }
    }

    @Test fun checkpointRoundTripRequiresMatchingConfirmedSetEvidence() {
        val values = mapOf(0 to listOf(8, 11), 1 to listOf(20))
        val encoded = WorkoutSetRepetitionLog.encode(values, moves)
        assertEquals("0:8.11,1:20", encoded)
        assertEquals(values, WorkoutSetRepetitionLog.decode(encoded, moves, mapOf(0 to 2, 1 to 1)))
        assertEquals(mapOf(0 to listOf(0), 1 to listOf(20)),
            WorkoutSetRepetitionLog.decode(encoded, moves, mapOf(0 to 1, 1 to 1)))
    }

    @Test fun malformedDuplicateAndOutOfRangeEntriesAreIgnored() {
        val raw = "0:8.10,0:12,1:20.21,2:8,9:4,bad,1:999"
        assertEquals(mapOf(0 to listOf(8, 10)),
            WorkoutSetRepetitionLog.decode(raw, moves, mapOf(0 to 2, 1 to 1)))
        assertEquals(listOf(0), WorkoutSetRepetitionLog.decode(null, moves, mapOf(1 to 1))[1])
    }

    @Test fun journalSummarySkipsUnknownLegacySetValuesButKeepsSetNumbers() {
        assertEquals("Squat: hiệp 2: 10 lần",
            WorkoutSetRepetitionLog.summary(mapOf(0 to listOf(0, 10)), moves))
    }

    @Test fun legacyCheckpointCanAppendTheNextSetWithoutMislabelingItAsTheFirst() {
        val plan = WorkoutSetTracker.parse(moves[0].dose)!!
        val restored = WorkoutSetRepetitionLog.decode(null, moves, mapOf(0 to 1))
        val updated = WorkoutSetRepetitionLog.append(restored, 0, 1, 9, plan)
        assertEquals(listOf(0, 9), updated[0])
        assertEquals("Squat: hiệp 2: 9 lần", WorkoutSetRepetitionLog.summary(updated, moves))
    }
}
