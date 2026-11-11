package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDurationPlannerTest {
    @Test fun previewAndGuidedSessionUseTheSameDeterministicPlan() {
        val program = TrainingCatalog.byId("full")
        val preview = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 20)
        val guided = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 20)

        assertEquals(preview, guided)
        assertEquals("Khởi động tại chỗ", preview.moves.first().name)
        assertEquals("Thả lỏng", preview.moves.last().name)
        assertEquals(program.moves, preview.moves)
        assertEquals(1, preview.rounds)
    }

    @Test fun longerTargetDoesNotDuplicateFullSetPrescriptions() {
        val program = TrainingCatalog.byId("full")
        val short = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 10)
        val longer = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 30)

        assertEquals(short.moves, longer.moves)
        assertEquals(1, short.rounds)
        assertEquals(1, longer.rounds)
        assertEquals(short.estimatedSeconds, longer.estimatedSeconds)
        assertTrue(longer.unallocatedSeconds > short.unallocatedSeconds)
    }

    @Test fun reportsWhenMinimumSequenceExceedsVeryShortTarget() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), level = 0, targetMinutes = 5
        )

        assertTrue(plan.exceedsTarget)
        assertEquals(17, plan.estimatedMinutes)
        assertEquals(0, plan.unallocatedSeconds)
        assertEquals(1, plan.rounds)
    }

    @Test fun estimatedDurationIncludesRequiredBetweenSetRest() {
        val move = TrainingMove(
            name = "Động tác thử",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện có kiểm soát",
            easier = "Giảm biên độ",
            avoid = "Dừng nếu đau",
            seconds = 60,
            restSeconds = 50
        )
        val program = TrainingProgram("rest-test", "Thử nghỉ", "Kiểm tra", "Sức mạnh", listOf(move))
        val plan = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 5)

        assertEquals(60, plan.activeSeconds)
        assertEquals(50, plan.prescribedRestSeconds)
        assertEquals(0, plan.transitionSeconds)
        assertEquals(110, plan.estimatedSeconds)
        assertEquals(2, plan.estimatedMinutes)
    }

    @Test fun fullBodyPlanExplainsActiveRestAndTransitionBreakdown() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), level = 0, targetMinutes = 20
        )

        assertEquals(660, plan.activeSeconds)
        assertEquals(240, plan.prescribedRestSeconds)
        assertEquals(120, plan.transitionSeconds)
        assertEquals(1_020, plan.estimatedSeconds)
        assertEquals(180, plan.unallocatedSeconds)
    }

    @Test fun transitionAllowanceReflectsExperienceWithoutChangingExercises() {
        val program = TrainingCatalog.byId("full")
        val beginner = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 30)
        val experienced = WorkoutDurationPlanner.build(program, level = 2, targetMinutes = 30)

        assertEquals(program.moves, beginner.moves)
        assertEquals(program.moves, experienced.moves)
        assertTrue(beginner.transitionSeconds > experienced.transitionSeconds)
        assertTrue(beginner.estimatedSeconds > experienced.estimatedSeconds)
    }

    @Test fun longTargetsNeverMultiplyExerciseVolume() {
        val program = TrainingCatalog.byId("advanced")
        val plan = WorkoutDurationPlanner.build(program, level = 2, targetMinutes = 120)

        assertEquals(program.moves, plan.moves)
        assertEquals(1, plan.rounds)
        assertEquals("Khởi động tại chỗ", plan.moves.first().name)
        assertEquals("Thả lỏng", plan.moves.last().name)
    }

    @Test fun rejectsInvalidInputs() {
        val program = TrainingCatalog.byId("full")
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutDurationPlanner.build(program, level = -1, targetMinutes = 20)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 4)
        }
    }
}
