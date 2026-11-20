package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        val summary = WorkoutMusclePlanSummary.summarize(moves)
        assertNotNull(summary)
        assertEquals(3, summary!!.eligibleMoveCount)
        assertEquals(3, summary.cataloguedMoveCount)
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.QUADRICEPS })
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.BACK })
        assertTrue(summary.primary.any { it.group == TrainingMuscleGroup.CHEST })
        assertTrue(summary.supporting.any { it.group == TrainingMuscleGroup.CORE })
    }

    @Test fun countsExerciseEntriesRatherThanSetsOrRepetitions() {
        val squat = catalog.getValue("Squat không tạ")
        val summary = WorkoutMusclePlanSummary.summarize(listOf(squat, squat))
        assertNotNull(summary)
        assertEquals(2, summary!!.primary.single { it.group == TrainingMuscleGroup.QUADRICEPS }.exerciseCount)
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
        val summary = WorkoutMusclePlanSummary.summarize(listOf(squat, custom))
        assertNotNull(summary)
        assertEquals(2, summary!!.eligibleMoveCount)
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
        val text = WorkoutMusclePlanSummary.format(
            listOf(catalog.getValue("Squat không tạ"), catalog.getValue("Chống đẩy tường"))
        )
        assertNotNull(text)
        assertTrue(text!!.contains("không phải khối lượng tập"))
        assertTrue(WorkoutMusclePlanSummary.spoken(
            listOf(catalog.getValue("Squat không tạ"))
        )!!.contains("không phải phép đo khối lượng tập"))
    }
}
