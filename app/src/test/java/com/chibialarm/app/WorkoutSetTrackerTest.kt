package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutSetTrackerTest {
    private fun move(dose: String) = TrainingMove(
        name = "Động tác thử",
        dose = dose,
        steps = "Thực hiện có kiểm soát",
        easier = "Giảm biên độ",
        avoid = "Dừng nếu đau"
    )

    @Test fun parsesFixedSetAndRepDose() {
        val plan = WorkoutSetTracker.parse("2 hiệp × 6–10 lần")
        assertNotNull(plan)
        assertEquals(2, plan!!.requiredSets)
        assertEquals(2, plan.maximumSets)
        assertEquals(6, plan.targetMin)
        assertEquals(10, plan.targetMax)
        assertEquals("lần", plan.unit)
        assertEquals(45, plan.defaultRestSeconds)
    }

    @Test fun parsesConservativeMinimumFromSetAndRepRanges() {
        val plan = WorkoutSetTracker.parse("2–3 hiệp × 8–12 lần")
        assertNotNull(plan)
        assertEquals(2, plan!!.requiredSets)
        assertEquals(3, plan.maximumSets)
        assertEquals(8, plan.targetMin)
        assertEquals(12, plan.targetMax)
    }

    @Test fun parsesTimedHoldsAsSetsWithShorterDefaultRest() {
        val plan = WorkoutSetTracker.parse("3 × 15 giây mỗi bên")
        assertNotNull(plan)
        assertEquals(3, plan!!.requiredSets)
        assertEquals(15, plan.targetMin)
        assertEquals("giây", plan.unit)
        assertEquals(30, plan.defaultRestSeconds)
    }

    @Test fun timeOnlyAndRepOnlyDosesDoNotBecomeSetPlans() {
        assertNull(WorkoutSetTracker.parse("3–5 phút"))
        assertNull(WorkoutSetTracker.parse("6–8 nhịp"))
        assertNull(WorkoutSetTracker.parse("2 phút mỗi bên"))
    }

    @Test fun rejectsUnboundedOrInvalidPrescriptions() {
        assertNull(WorkoutSetTracker.parse("21 hiệp × 8 lần"))
        assertNull(WorkoutSetTracker.parse("2 hiệp × 201 lần"))
        assertNull(WorkoutSetTracker.parse("3–2 hiệp × 8 lần"))
    }

    @Test fun completingSetsIsBoundedAndIdempotentAtTarget() {
        val plan = WorkoutSetTracker.parse("2 hiệp × 8–12 lần")!!
        assertEquals(1, WorkoutSetTracker.complete(0, plan))
        assertEquals(2, WorkoutSetTracker.complete(1, plan))
        assertEquals(2, WorkoutSetTracker.complete(2, plan))
        assertFalse(WorkoutSetTracker.isComplete(1, plan))
        assertTrue(WorkoutSetTracker.isComplete(2, plan))
    }

    @Test fun checkpointRoundTripKeepsOnlyValidSetBasedMoves() {
        val moves = listOf(
            move("2 hiệp × 8–12 lần"),
            move("3–5 phút"),
            move("2 × 20 giây")
        )
        val encoded = WorkoutSetTracker.encode(mapOf(0 to 2, 1 to 1, 2 to 1, 99 to 3), moves)
        assertEquals("0:2,2:1", encoded)
        assertEquals(mapOf(0 to 2, 2 to 1), WorkoutSetTracker.decode(encoded, moves))
    }

    @Test fun malformedDuplicateAndOutOfRangeCheckpointEntriesAreIgnored() {
        val moves = listOf(move("2 hiệp × 8 lần"), move("3–5 phút"))
        assertEquals(
            mapOf(0 to 1),
            WorkoutSetTracker.decode("0:1,0:2,1:1,9:2,bad,0:99", moves)
        )
        assertTrue(WorkoutSetTracker.decode(null, moves).isEmpty())
        assertTrue(WorkoutSetTracker.decode("", moves).isEmpty())
    }
}
