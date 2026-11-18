package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLibrarySearchTest {
    private fun move(
        name: String,
        dose: String = "2 hiệp × 8 lần",
        steps: String = "Thực hiện chậm và có kiểm soát.",
        easier: String = "Giảm biên độ.",
        avoid: String = "Dừng nếu đau.",
        equipment: Set<TrainingEquipment> = emptySet()
    ) = TrainingMove(
        name = name,
        dose = dose,
        steps = steps,
        easier = easier,
        avoid = avoid,
        equipmentOptions = equipment
    )

    private val strength = TrainingProgram(
        id = "strength",
        name = "Toàn thân cơ bản",
        subtitle = "Sức mạnh tại nhà",
        category = "Sức mạnh",
        moves = listOf(
            move("Squat không tạ", steps = "Chân rộng bằng vai, hạ hông có kiểm soát."),
            move(
                "Kéo tạ đơn",
                steps = "Kéo khuỷu về sau.",
                avoid = "Kiểm tra tạ và không giật lưng.",
                equipment = setOf(TrainingEquipment.DUMBBELL)
            )
        )
    )

    private val mobility = TrainingProgram(
        id = "mobility",
        name = "Giãn cơ nhẹ",
        subtitle = "Vận động linh hoạt",
        category = "Linh hoạt",
        moves = listOf(
            move(
                "Giãn gân kheo ngồi ghế",
                dose = "2 hiệp × 20 giây",
                steps = "Ngồi ghế vững, duỗi một chân.",
                equipment = setOf(TrainingEquipment.STABLE_CHAIR)
            )
        )
    )

    @Test fun searchesVietnameseNamesWithoutDiacritics() {
        val result = WorkoutLibrarySearch.filter(
            listOf(strength, mobility), "Tất cả", "squat khong ta"
        )
        assertEquals(listOf("Toàn thân cơ bản"), result.map { it.program.name })
        assertEquals(listOf("Squat không tạ"), result.single().matchingMoves.map { it.name })
    }

    @Test fun searchesMovementInstructionsAndSafetyNotesNotOnlyTitles() {
        val result = WorkoutLibrarySearch.filter(
            listOf(strength), "Tất cả", "kiem tra ta"
        )
        assertEquals(1, result.size)
        assertEquals("Kéo tạ đơn", result.single().matchingMoves.single().name)
    }

    @Test fun searchesEquipmentLabelsAndKeepsTheMatchedExerciseVisible() {
        val result = WorkoutLibrarySearch.filter(
            listOf(mobility), "Linh hoạt", "ghe vung chac"
        )
        assertEquals("Giãn cơ nhẹ", result.single().program.name)
        assertEquals("Giãn gân kheo ngồi ghế", result.single().matchingMoves.single().name)
    }

    @Test fun categoryFilterIsAppliedBeforeReturningSearchResults() {
        assertTrue(
            WorkoutLibrarySearch.filter(listOf(strength, mobility), "Cardio", "squat").isEmpty()
        )
    }

    @Test fun emptySearchPreservesTheExistingCategoryListing() {
        val result = WorkoutLibrarySearch.filter(listOf(strength, mobility), "Tất cả", "  ")
        assertEquals(listOf("strength", "mobility"), result.map { it.program.id })
        assertTrue(result.all { it.matchingMoves.isEmpty() })
    }

    @Test fun programNameMatchesEvenWhenNoSingleMoveMatchesEveryTerm() {
        val result = WorkoutLibrarySearch.filter(
            listOf(strength), "Tất cả", "toan than co ban"
        )
        assertEquals(1, result.size)
        assertTrue(result.single().matchingMoves.isEmpty())
    }

    @Test fun inputAndResultCountsAreBounded() {
        val tooLong = "squat " + "x".repeat(200)
        assertTrue(WorkoutLibrarySearch.filter(listOf(strength), "Tất cả", tooLong).isEmpty())
        assertEquals(
            1,
            WorkoutLibrarySearch.filter(
                listOf(strength, mobility), "Tất cả", "squat", 
            ).size
        )
    }
}
