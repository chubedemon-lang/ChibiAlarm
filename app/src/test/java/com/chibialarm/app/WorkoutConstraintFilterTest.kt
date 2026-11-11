package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutConstraintFilterTest {
    private val wristOnly = WorkoutConstraintProfile(avoidWristLoad = true)

    @Test fun noRestrictionPreservesTheOriginalProgram() {
        val original = TrainingCatalog.byId("full")
        val result = WorkoutConstraintFilter.apply(original, WorkoutConstraintProfile())
        assertSame(original, result.program)
        assertTrue(result.substitutions.isEmpty())
        assertTrue(result.removedMoves.isEmpty())
    }

    @Test fun fullBodyPlanReplacesPalmSupportedMovesAndKeepsTheSourceImmutable() {
        val original = TrainingCatalog.byId("full")
        val result = WorkoutConstraintFilter.apply(original, wristOnly)

        assertTrue(result.canStart)
        assertTrue(result.program.moves.any { it.name == "Ép ngực tường bằng cẳng tay" })
        assertTrue(result.program.moves.any { it.name == "Dead bug cơ bụng" })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
        assertEquals(listOf("Khởi động tại chỗ", "Squat không tạ", "Chống đẩy tường",
            "Nâng hông trên sàn", "Bird-dog", "Plank khuỷu hoặc plank gối", "Thả lỏng"),
            original.moves.map { it.name })
        assertTrue(result.notice.contains("không tương đương hoàn toàn"))
    }

    @Test fun weightedPlanDoesNotKeepGripLoadedCatalogMoves() {
        val result = WorkoutConstraintFilter.apply(TrainingCatalog.byId("weighted"), wristOnly)
        assertTrue(result.program.moves.none { it.name.contains("tạ đơn hoặc dây") })
        assertTrue(result.program.moves.any { it.name == "Siết bả vai đứng" })
        assertTrue(result.program.moves.any { it.name == "Nâng hông trên sàn" })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
    }

    @Test fun everyCuratedProgramRemainsBuildableAfterFiltering() {
        TrainingCatalog.programs.forEach { program ->
            val filtered = WorkoutConstraintFilter.apply(program, wristOnly)
            assertTrue("No main movement remains for ${program.id}", filtered.canStart)
            val plan = WorkoutDurationPlanner.build(filtered.program, level = 0, targetMinutes = 20)
            assertTrue(plan.moves.isNotEmpty())
            assertTrue(plan.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
        }
    }

    @Test fun substitutionsUseCanonicalCatalogInstructions() {
        val result = WorkoutConstraintFilter.apply(TrainingCatalog.byId("upper"), wristOnly)
        val press = result.program.moves.first { it.name == "Ép ngực tường bằng cẳng tay" }
        assertTrue(press.steps.contains("cẳng tay"))
        assertTrue(press.steps.contains("cổ tay thả lỏng"))
        assertFalse(press.steps.contains("instructions supplied by caller"))
    }

    @Test fun planFingerprintIsStableAndChangesWhenTheConstraintChanges() {
        val original = TrainingCatalog.byId("full").moves
        val filtered = WorkoutConstraintFilter.apply(
            TrainingCatalog.byId("full"),
            WorkoutConstraintProfile(avoidWristLoad = true)
        ).program.moves
        assertEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(original.toList())
        )
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(filtered)
        )
    }

    @Test fun fingerprintChangesWhenOnlyTheRestPrescriptionChanges() {
        val original = TrainingCatalog.byId("full").moves
        val changedRest = original.mapIndexed { index, move ->
            if (index == 1) move.copy(restSeconds = move.restSeconds + 15) else move
        }

        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(changedRest)
        )
    }


}
