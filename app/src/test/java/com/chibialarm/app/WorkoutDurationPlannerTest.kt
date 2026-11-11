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

        assertEquals(program.moves, short.moves)
        assertEquals(program.moves, longer.moves.filterNot { it in longer.extensionMoves })
        assertTrue(longer.extensionMoves.isNotEmpty())
        assertEquals(1, short.rounds)
        assertEquals(1, longer.rounds)
        assertTrue(longer.estimatedSeconds > short.estimatedSeconds)
        val unextendedLong = WorkoutDurationPlanner.build(
            program, level = 0, targetMinutes = 30,
            preferences = WorkoutExtensionPreferences(energy = 0)
        )
        assertTrue(longer.unallocatedSeconds < unextendedLong.unallocatedSeconds)
        assertEquals(longer.moves.size, longer.moves.map { it.name }.distinct().size)
        assertEquals("Thả lỏng", longer.moves.last().name)
    }

    @Test fun reportsWhenMinimumSequenceExceedsVeryShortTarget() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), level = 0, targetMinutes = 5
        )

        assertTrue(plan.exceedsTarget)
        assertEquals(19, plan.estimatedMinutes)
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

        assertEquals(780, plan.activeSeconds)
        assertEquals(240, plan.prescribedRestSeconds)
        assertEquals(120, plan.transitionSeconds)
        assertEquals(1_140, plan.estimatedSeconds)
        assertEquals(60, plan.unallocatedSeconds)
    }

    @Test fun transitionAllowanceReflectsExperienceWithoutChangingExercises() {
        val program = TrainingCatalog.byId("full")
        val beginner = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 30)
        val experienced = WorkoutDurationPlanner.build(program, level = 2, targetMinutes = 30)

        assertEquals(program.moves, beginner.moves.filterNot { it in beginner.extensionMoves })
        assertEquals(program.moves, experienced.moves.filterNot { it in experienced.extensionMoves })
        assertTrue(beginner.extensionMoves.size <= 2)
        assertTrue(experienced.extensionMoves.size <= 3)
        assertTrue(beginner.transitionSeconds > experienced.transitionSeconds)
        assertTrue(beginner.estimatedSeconds <= 30 * 60)
        assertTrue(experienced.estimatedSeconds <= 30 * 60)
    }

    @Test fun longTargetsNeverMultiplyExerciseVolume() {
        val program = TrainingCatalog.byId("advanced")
        val plan = WorkoutDurationPlanner.build(program, level = 2, targetMinutes = 120)

        assertEquals(program.moves, plan.moves.filterNot { it in plan.extensionMoves })
        assertTrue(plan.extensionMoves.size <= 3)
        assertEquals(plan.moves.size, plan.moves.map { it.name }.distinct().size)
        assertEquals(1, plan.rounds)
        assertEquals("Khởi động tại chỗ", plan.moves.first().name)
        assertEquals("Thả lỏng", plan.moves.last().name)
    }

    @Test fun minuteDoseUsesItsConservativeLowerBoundInsteadOfAnUnrelatedTimerHint() {
        val warmup = TrainingMove(
            name = "Khởi động thử",
            dose = "3–5 phút",
            steps = "Bắt đầu chậm",
            easier = "Đi chậm",
            avoid = "Dừng nếu khó chịu",
            seconds = 120
        )
        val fixed = TrainingMove(
            name = "Đi bộ thử",
            dose = "2 phút",
            steps = "Bước đều",
            easier = "Giảm tốc",
            avoid = "Dừng nếu chóng mặt",
            seconds = 90
        )
        val plan = WorkoutDurationPlanner.build(
            TrainingProgram("timed", "Thử thời lượng", "Kiểm tra", "Vận động nhẹ", listOf(warmup, fixed)),
            level = 1,
            targetMinutes = 5
        )

        assertEquals(300, plan.activeSeconds)
        assertEquals(0, plan.prescribedRestSeconds)
        assertEquals(15, plan.transitionSeconds)
        assertEquals(315, plan.estimatedSeconds)
    }

    @Test fun targetWithNoSafeExtensionRoomKeepsTheBasePrescription() {
        val program = TrainingCatalog.byId("full")
        val plan = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 20)
        assertTrue(plan.extensionMoves.isEmpty())
        assertEquals(program.moves, plan.moves)
        assertEquals(60, plan.unallocatedSeconds)
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
