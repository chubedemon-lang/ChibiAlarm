package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutWeekProgramPlannerTest {
    private val monday = LocalDate.of(2026, 11, 30)

    private fun program(id: String, category: String) = TrainingProgram(
        id = id,
        name = id,
        subtitle = "test fixture",
        category = category,
        moves = listOf(
            TrainingMove(
                name = "Động tác $id",
                dose = "1 hiệp × 5 lần",
                steps = "Thực hiện chậm và có kiểm soát.",
                easier = "Giảm biên độ.",
                avoid = "Dừng nếu khó chịu."
            )
        )
    )

    private val strength = program("full", "Sức mạnh")
    private val cardio = program("lowcardio", "Cardio")
    private val mobility = program("mobility", "Linh hoạt")
    private val light = program("quickrecovery", "Phục hồi nhẹ")
    private val cardioLight = program("quick15lowcardio", "Cardio nhẹ")
    private val challenge = program("advanced", "Thử thách")
    private val catalog = listOf(strength, cardio, mobility, light, cardioLight, challenge)

    @Test fun preservesSelectedProgramThenRotatesStrengthGoalAcrossCardioAndMobility() {
        val sessions = WorkoutWeekProgramPlanner.plan(
            listOf(monday, monday.plusDays(2), monday.plusDays(5)),
            preferred = strength,
            eligiblePrograms = catalog,
            goalIndex = 0
        )

        assertEquals(listOf("full", "lowcardio", "mobility"), sessions.map { it.program.id })
        assertEquals(listOf(monday, monday.plusDays(2), monday.plusDays(5)), sessions.map { it.date })
    }

    @Test fun cardioGoalAddsMobilityBeforeTheNextCardioSession() {
        val sessions = WorkoutWeekProgramPlanner.plan(
            listOf(monday, monday.plusDays(2), monday.plusDays(5)),
            preferred = cardio,
            eligiblePrograms = catalog,
            goalIndex = 1
        )

        assertEquals(listOf("lowcardio", "mobility", "quick15lowcardio"), sessions.map { it.program.id })
    }

    @Test fun neverSelectsProgramsThatWereRemovedByEquipmentOrLevelFiltering() {
        val compatible = listOf(strength, mobility, light)
        val sessions = WorkoutWeekProgramPlanner.plan(
            listOf(monday, monday.plusDays(2), monday.plusDays(5)),
            preferred = strength,
            eligiblePrograms = compatible,
            goalIndex = 0
        )

        assertTrue(sessions.all { session -> compatible.any { it.id == session.program.id } })
        assertNotEquals("advanced", sessions.last().program.id)
    }

    @Test fun usesEveryCompatibleProgramBeforeRepeatingOne() {
        val sessions = WorkoutWeekProgramPlanner.plan(
            (0L..4L).map(monday::plusDays),
            preferred = strength,
            eligiblePrograms = catalog.take(5),
            goalIndex = 5
        )

        assertEquals(5, sessions.map { it.program.id }.distinct().size)
    }

    @Test fun repeatsOnlyAfterCompatibleCatalogIsExhausted() {
        val sessions = WorkoutWeekProgramPlanner.plan(
            (0L..3L).map(monday::plusDays),
            preferred = strength,
            eligiblePrograms = listOf(strength, cardio),
            goalIndex = 0
        )

        assertEquals(2, sessions.map { it.program.id }.distinct().size)
        assertEquals(2, sessions.groupingBy { it.program.id }.eachCount().values.maxOrNull())
    }

    @Test fun recurringMaskAndTitleUseTheCorrectVietnameseWeekday() {
        val mondaySession = WorkoutWeekProgramPlanner.plan(
            listOf(monday), strength, listOf(strength), goalIndex = 0
        ).single()
        val sundaySession = WorkoutWeekProgramPlanner.plan(
            listOf(monday.plusDays(6)), strength, listOf(strength), goalIndex = 0
        ).single()

        assertEquals(1 shl 1, mondaySession.repeatMask)
        assertEquals(1, sundaySession.repeatMask)
        assertEquals("Thứ Hai · full", mondaySession.routineTitle)
        assertEquals("Chủ nhật · full", sundaySession.routineTitle)
    }

    @Test fun rejectsDuplicateDatesUnknownGoalsAndMissingPreferredProgram() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutWeekProgramPlanner.plan(listOf(monday, monday), strength, catalog, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutWeekProgramPlanner.plan(listOf(monday), strength, catalog, 6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutWeekProgramPlanner.plan(listOf(monday), strength, listOf(cardio), 0)
        }
    }
}
