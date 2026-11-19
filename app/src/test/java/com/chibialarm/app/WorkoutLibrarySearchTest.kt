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

    @Test fun previewStartsAtTheFirstMatchedExerciseThatSurvivedThePlan() {
        val match = WorkoutLibrarySearch.filter(
            listOf(strength), "Tất cả", "squat khong ta"
        ).single()
        val planned = listOf(
            move("Khởi động"),
            move("Squat không tạ"),
            move("Thả lỏng")
        )

        assertEquals(1, WorkoutLibrarySearch.previewIndex(match, planned))
    }

    @Test fun previewFallsBackToTheFirstAllowedMoveWhenTheMatchWasFilteredOut() {
        val match = WorkoutLibrarySearch.filter(
            listOf(strength), "Tất cả", "squat khong ta"
        ).single()
        val planned = listOf(move("Khởi động"), move("Chống đẩy tường"))

        assertEquals(0, WorkoutLibrarySearch.previewIndex(match, planned))
        assertEquals(0, WorkoutLibrarySearch.previewIndex(match.copy(matchingMoves = emptyList()), planned))
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

    @Test fun searchesCuratedMuscleFocusNotesWithoutInventingExerciseMatches() {
        val results = WorkoutLibrarySearch.filter(
            TrainingCatalog.programs,
            "Tất cả",
            "đùi trước"
        )
        val matchedMoves = results.flatMap { it.matchingMoves }

        assertTrue(matchedMoves.any { it.name == "Squat không tạ" })
        assertTrue(matchedMoves.isNotEmpty())
        assertTrue(matchedMoves.all {
            WorkoutExerciseFocus.descriptionFor(it.name)?.contains("đùi trước") == true
        })
    }

    @Test fun muscleFocusSearchIgnoresGenericPrefixesAndUsesOnlyCuratedFocusText() {
        val results = WorkoutLibrarySearch.filter(
            TrainingCatalog.programs,
            "Tất cả",
            "bài tập đùi trước"
        )
        val matchedMoves = results.flatMap { it.matchingMoves }

        assertTrue(matchedMoves.isNotEmpty())
        assertTrue(matchedMoves.all {
            WorkoutExerciseFocus.descriptionFor(it.name)?.contains("đùi trước") == true
        })
    }

    @Test fun muscleFocusSearchFindsUpperBodyMovesAndStillHonorsCategory() {
        val results = WorkoutLibrarySearch.filter(
            TrainingCatalog.programs,
            "Sức mạnh",
            "cơ tay sau"
        )
        val matchedMoves = results.flatMap { it.matchingMoves }

        assertTrue(matchedMoves.any { it.name == "Chống đẩy tường" })
        assertTrue(results.all { it.program.category == "Sức mạnh" })
        assertTrue(matchedMoves.all {
            WorkoutExerciseFocus.descriptionFor(it.name)?.contains("cơ tay sau") == true
        })
    }

    @Test fun wristAndGripTermsStillSearchGeneralExerciseInstructions() {
        val gripProgram = TrainingProgram(
            id = "grip",
            name = "Cầm tạ an toàn",
            subtitle = "Hướng dẫn cầm dụng cụ",
            category = "Sức mạnh",
            moves = listOf(
                move("Kéo tạ bằng tay", steps = "Cầm tạ bằng hai tay và giữ cổ tay thẳng.")
            )
        )

        val results = WorkoutLibrarySearch.filter(
            listOf(gripProgram),
            "Tất cả",
            "tay cầm"
        )
        assertEquals("Kéo tạ bằng tay", results.single().matchingMoves.single().name)
    }

    @Test fun uncuratedMuscleNamesDoNotCreateSpeculativeSearchResults() {
        val results = WorkoutLibrarySearch.filter(
            TrainingCatalog.programs,
            "Tất cả",
            "cơ tam đầu"
        )
        assertTrue(results.isEmpty())
    }
}
