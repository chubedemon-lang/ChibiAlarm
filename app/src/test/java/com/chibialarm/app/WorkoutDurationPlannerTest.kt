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
        assertEquals(2, preview.rounds)
        assertEquals(18, preview.estimatedMinutes)
    }

    @Test fun longerTargetAddsRoundsWithoutChangingTheCatalogOrder() {
        val program = TrainingCatalog.byId("full")
        val short = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 10)
        val longer = WorkoutDurationPlanner.build(program, level = 0, targetMinutes = 20)

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
        assertEquals(11, plan.estimatedMinutes)
        assertEquals(1, plan.rounds)
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
