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
        assertEquals(1, preview.rounds)
        assertEquals(15, preview.estimatedMinutes)
    }

    @Test fun longerTargetAddsRoundsWithoutChangingTheCatalogOrder() {
        val program = TrainingCatalog.byId("full")
        val short = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 10)
        val longer = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 30)

        assertEquals(1, short.rounds)
        assertEquals(2, longer.rounds)
        assertTrue(longer.moves.size > short.moves.size)
        assertEquals(short.moves.first(), longer.moves.first())
        assertEquals(short.moves.last(), longer.moves.last())
    }

    @Test fun veryShortTargetReportsWhenMinimumSequenceExceedsIt() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), level = 0, targetMinutes = 5
        )

        assertTrue(plan.exceedsTarget)
        assertEquals(15, plan.estimatedMinutes)
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

        assertEquals(110, plan.estimatedSeconds)
        assertEquals(2, plan.estimatedMinutes)
    }

    @Test fun longProgramsAreCappedAtFourRounds() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("advanced"), level = 2, targetMinutes = 120
        )

        assertEquals(4, plan.rounds)
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
