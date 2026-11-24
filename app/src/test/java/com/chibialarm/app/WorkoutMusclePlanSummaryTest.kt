package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMusclePlanSummaryTest {
    private val catalog = TrainingCatalog.allMoves.associateBy { it.name }

    @Test fun summarizesOnlyTheSelectedMainMovesAndExcludesWarmupAndCooldown() {
        val moves = listOf(
            catalog.getValue("Khởi động tại chỗ"),
            catalog.getValue("Squat không tạ"),
            catalog.getValue("Kéo tạ đơn hoặc dây kháng lực"),
            catalog.getValue("Chống đẩy tường"),
            catalog.getValue("Thả lỏng")
        )

        val summary = requireNotNull(WorkoutMusclePlanSummary.summarize(moves))
        assertEquals(3, summary.eligibleMoveCount)
        assertEquals(3, summary.cataloguedMoveCount)
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.QUADRICEPS })
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.BACK })
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.CHEST })
        assertTrue(summary.supporting.any { it.group == TrainingMuscleGroup.CORE })
    }

    @Test fun countsExerciseEntriesRatherThanSetsOrRepetitions() {
        val squat = catalog.getValue("Squat không tạ")
        val summary = requireNotNull(WorkoutMusclePlanSummary.summarize(listOf(squat, squat)))
        assertEquals(2, summary.primary.single { it.group == TrainingMuscleGroup.QUADRICEPS }.exerciseCount)
        assertEquals(2, summary.primary.single { it.group == TrainingMuscleGroup.GLUTES }.exerciseCount)
    }

    @Test fun unknownMovesAreReportedAsUnclassifiedInsteadOfGuessedAsFullBody() {
        val custom = TrainingMove(
            name = "Động tác tự tạo",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện theo hướng dẫn riêng.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )
        val squat = catalog.getValue("Squat không tạ")
        val summary = requireNotNull(WorkoutMusclePlanSummary.summarize(listOf(squat, custom)))
        assertEquals(2, summary.eligibleMoveCount)
        assertEquals(1, summary.cataloguedMoveCount)
        assertFalse(summary.primary.any { it.group == TrainingMuscleGroup.FULL_BODY })
        assertTrue(WorkoutMusclePlanSummary.format(listOf(squat, custom))!!.contains("1/2"))
    }

    @Test fun returnsNoSummaryWhenThereAreNoCataloguedMainExercises() {
        val custom = TrainingMove(
            name = "Động tác tự tạo",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện theo hướng dẫn riêng.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )
        assertNull(WorkoutMusclePlanSummary.summarize(listOf(custom)))
        assertNull(WorkoutMusclePlanSummary.summarize(listOf(catalog.getValue("Khởi động tại chỗ"))))
    }

    @Test fun summaryClearlyStatesThatExerciseCountsAreNotTrainingVolume() {
        val text = requireNotNull(WorkoutMusclePlanSummary.format(
            listOf(catalog.getValue("Squat không tạ"), catalog.getValue("Chống đẩy tường"))
        ))
        assertTrue(text.contains("không phải khối lượng tập"))
        assertTrue(text.contains("\n• Nhóm chính:"))
        val spoken = requireNotNull(WorkoutMusclePlanSummary.spoken(
            listOf(catalog.getValue("Squat không tạ"))
        ))
        assertTrue(spoken.contains("không phải phép đo khối lượng tập"))
    }

    @Test fun comparisonShowsCuratedMuscleEmphasisChangesForAReplacement() {
        val jumping = catalog.getValue("Bật nhảy dang tay chân")
        val sideStep = catalog.getValue("Bước ngang nhẹ")

        val comparison = requireNotNull(
            WorkoutMusclePlanSummary.compare(listOf(jumping), listOf(sideStep))
        )
        assertTrue(comparison.primary.any {
            it.group == TrainingMuscleGroup.FULL_BODY &&
                it.beforeCount == 1 && it.afterCount == 0
        })
        assertTrue(comparison.primary.any {
            it.group == TrainingMuscleGroup.HIP_ABDUCTORS &&
                it.beforeCount == 0 && it.afterCount == 1
        })
        assertTrue(comparison.primary.any {
            it.group == TrainingMuscleGroup.GLUTES &&
                it.beforeCount == 0 && it.afterCount == 1
        })

        val text = requireNotNull(
            WorkoutMusclePlanSummary.formatComparison(listOf(jumping), listOf(sideStep))
        )
        assertTrue(text.contains("Toàn thân: 1 → 0"))
        assertTrue(text.contains("Cơ dạng hông: 0 → 1"))
        assertTrue(text.contains("đếm động tác, không phải khối lượng tập"))
        assertTrue(text.contains("không chứng minh hai bài tương đương"))
    }

    @Test fun comparisonDoesNotGuessWhenAPlanHasOnlyUnknownExercises() {
        val squat = catalog.getValue("Squat không tạ")
        val custom = TrainingMove(
            name = "Động tác tự tạo",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện theo hướng dẫn riêng.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )

        assertNull(WorkoutMusclePlanSummary.compare(listOf(squat), listOf(custom)))
        assertNull(WorkoutMusclePlanSummary.formatComparison(listOf(squat), listOf(custom)))
    }

    @Test fun identicalCuratedPlansReportNoMuscleEmphasisDelta() {
        val squat = catalog.getValue("Squat không tạ")
        val text = requireNotNull(
            WorkoutMusclePlanSummary.formatComparison(listOf(squat), listOf(squat))
        )

        assertTrue(text.contains("Nhóm chính: không thay đổi theo nhãn đã duyệt"))
        assertTrue(text.contains("Nhóm hỗ trợ: không thay đổi theo nhãn đã duyệt"))
    }
}
