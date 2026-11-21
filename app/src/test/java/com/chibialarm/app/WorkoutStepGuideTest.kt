package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStepGuideTest {
    @Test fun splitsLinesAndSentences() {
        assertEquals(listOf("Đứng thẳng.", "Hạ người.", "Đứng lên."),
            WorkoutStepGuide.split("Đứng thẳng. Hạ người.\nĐứng lên."))
    }

    @Test fun preservesSingleStepAndFallback() {
        assertEquals(listOf("Giữ lưng thẳng"), WorkoutStepGuide.split("  Giữ lưng thẳng  "))
        assertEquals(listOf("Đọc hướng dẫn trên màn hình và tập theo sức mình."),
            WorkoutStepGuide.split(" \n  "))
    }

    @Test fun stripsNumberedAndBulletMarkersForSpeech() {
        assertEquals(listOf("Đứng thẳng.", "Hạ người.", "Thở ra."),
            WorkoutStepGuide.split("1. Đứng thẳng.\n2) Hạ người.\n• Thở ra."))
    }

    @Test fun splitsCompactSemicolonAndInlineNumberedCues() {
        assertEquals(
            listOf("Đứng thẳng", "Hạ người", "Thở ra."),
            WorkoutStepGuide.split("1) Đứng thẳng; 2) Hạ người; 3) Thở ra.")
        )
    }

    @Test fun keepsDecimalWeightsAndTimesIntact() {
        assertEquals(listOf("Giữ tạ 2.5 kg.", "Nghỉ 1.5 phút."),
            WorkoutStepGuide.split("Giữ tạ 2.5 kg. Nghỉ 1.5 phút."))
    }

    @Test fun skipsEmptyBullets() {
        assertEquals(listOf("Nâng tay"), WorkoutStepGuide.split("•\n-\n3) Nâng tay"))
    }

    @Test fun spokenCueRepeatsOnlyTheSelectedStepWithItsMatchingMovementPhase() {
        val instructions = "Chân rộng bằng vai.\nĐẩy hông nhẹ về sau.\nHạ xuống theo khả năng.\nĐứng lên.\nGiữ gối cùng hướng bàn chân."
        val cue = WorkoutStepGuide.spokenCue("Squat không tạ", instructions, 3)
        assertTrue(cue.contains("Hạ hông"))
        assertTrue(cue.contains("Bước 3 trên 5"))
        assertTrue(cue.contains("Hạ xuống theo khả năng"))
        assertFalse(cue.contains("Đứng lên."))
    }

    @Test fun spokenCueClampsInvalidPositionsAndKeepsSetupInstructionsNeutral() {
        val instructions = "Chân rộng bằng vai.\nHạ xuống theo khả năng.\nĐứng lên."
        val cue = WorkoutStepGuide.spokenCue("Squat không tạ", instructions, 0)
        assertTrue(cue.contains("Bước 1 trên 3"))
        assertTrue(cue.contains("Tư thế minh họa cho bước này"))
        assertFalse(cue.contains("Hạ hông."))
    }

    @Test fun repeatCueUsesTheLastSelectedStepOnlyForTheSameExercise() {
        val instructions = "Chân rộng bằng vai.\nHạ xuống theo khả năng.\nĐứng lên."
        val selectedSpeech = WorkoutStepGuide.spokenCue("Squat không tạ", instructions, 2)
        val selected = WorkoutStepGuide.SelectedCue(moveIndex = 4, speech = selectedSpeech)

        assertEquals(selectedSpeech, WorkoutStepGuide.repeatCue(4, selected, "Squat không tạ", instructions))
        val nextExerciseCue = WorkoutStepGuide.repeatCue(5, selected, "Squat không tạ", instructions)
        assertTrue(nextExerciseCue.contains("Bước 1 trên 3"))
    }

    @Test fun walkthroughMovesBackwardAndForwardWithoutLeavingBounds() {
        val first = WorkoutStepGuide.Walkthrough.start(
            "Đứng thẳng.\nHạ người.\nĐứng lên."
        )
        assertEquals(1, first.position)
        assertEquals("Đứng thẳng.", first.currentStep)
        assertFalse(first.canGoPrevious)

        val second = first.next()
        assertEquals(2, second.position)
        assertEquals("Hạ người.", second.currentStep)
        assertTrue(second.canGoPrevious)

        val third = second.next().next()
        assertEquals(3, third.position)
        assertEquals("Đứng lên.", third.currentStep)
        assertFalse(third.canGoNext)
        assertEquals(3, third.next().position)
        assertEquals("Hạ người.", third.previous().currentStep)
    }

    @Test fun walkthroughCanReturnToTheFirstStepWithoutChangingItsSnapshot() {
        val last = WorkoutStepGuide.Walkthrough.start("Bước một.\nBước hai.\nBước ba.")
            .next().next()
        assertEquals(3, last.position)
        val first = last.first()
        assertEquals(1, first.position)
        assertEquals("Bước một.", first.currentStep)
        assertFalse(first.canGoPrevious)
        assertTrue(first.canGoNext)
        assertEquals(3, last.position)
        assertEquals(1, last.first().position)
    }

    @Test fun walkthroughAlwaysHasAReadableFallbackStep() {
        val walkthrough = WorkoutStepGuide.Walkthrough.start("  ")
        assertEquals(1, walkthrough.steps.size)
        assertEquals("Đọc hướng dẫn trên màn hình và tập theo sức mình.", walkthrough.currentStep)
    }

    @Test fun curatedCatalogInstructionsExposeActionableStepsForWalkthroughAndSpeech() {
        val expected = mapOf(
            "Squat không tạ" to listOf(
                "Chân rộng bằng vai",
                "đẩy hông nhẹ về sau",
                "hạ xuống theo khả năng",
                "đứng lên",
                "giữ gối cùng hướng bàn chân."
            ),
            "Chống đẩy tường" to listOf(
                "Đặt hai tay ngang ngực lên tường",
                "giữ thân thẳng",
                "gập khuỷu đưa ngực gần tường",
                "đẩy ra có kiểm soát."
            ),
            "Plank khuỷu hoặc plank gối" to listOf(
                "Giữ thân thành đường thẳng",
                "siết bụng nhẹ",
                "tiếp tục hít thở đều."
            )
        )
        expected.forEach { (name, cues) ->
            val move = TrainingCatalog.allMoves.single { it.name == name }
            val walkthrough = WorkoutStepGuide.Walkthrough.start(move.steps)
            assertEquals(name, cues, walkthrough.steps)
            assertEquals(name, 1, walkthrough.position)
            assertEquals(name, cues.first(), walkthrough.currentStep)
            assertEquals(name, cues.size > 1, walkthrough.canGoNext)
        }
    }

    @Test fun spokenEasierCueReadsOnlyBoundedCatalogGuidance() {
        val cue = WorkoutStepGuide.spokenEasierCue(
            "Squat không tạ",
            "Giảm biên độ.\nDùng ghế vững để hỗ trợ.\nGiữ nhịp chậm."
        )
        assertTrue(cue.startsWith("Squat không tạ."))
        assertTrue(cue.contains("Giảm biên độ. Dùng ghế vững để hỗ trợ. Giữ nhịp chậm."))
        assertFalse(cue.contains("\\n"))
        assertTrue(WorkoutStepGuide.spokenEasierCue("Squat", " ").contains("Chưa có hướng dẫn giảm độ khó"))
    }

    @Test fun spokenSafetyNoteReadsExerciseCautionWithoutTreatingItAsAUserSymptom() {
        val cue = WorkoutStepGuide.spokenSafetyNoteCue(
            "Chống đẩy tường",
            "Dừng nếu đau cổ tay. Không khóa cứng khuỷu."
        )
        assertTrue(cue.startsWith("Chống đẩy tường."))
        assertTrue(cue.contains("Dừng nếu đau cổ tay. Không khóa cứng khuỷu."))
        assertTrue(WorkoutStepGuide.spokenSafetyNoteCue("Plank", "").contains("Chưa có lưu ý riêng"))
    }

    @Test fun spokenCatalogNotesAreBoundedAndStripControlCharacters() {
        val cue = WorkoutStepGuide.spokenSafetyNoteCue(
            "Squat\nkhông tạ",
            "Giữ lưng thẳng\u0000\nKhông cố quá sức."
        )
        assertTrue(cue.startsWith("Squat không tạ."))
        assertTrue(cue.contains("Giữ lưng thẳng Không cố quá sức."))
        assertTrue(cue.length < 600)
    }

    @Test fun workoutSummaryShowsRequiredSetsRepRangeRestAndConfirmedProgress() {
        val plan = WorkoutSetTracker.Plan(
            requiredSets = 2,
            maximumSets = 3,
            targetMin = 8,
            targetMax = 12,
            unit = "lần",
            restSeconds = 45
        )
        val summary = WorkoutStepGuide.workoutSummary("2 hiệp × 8–12 lần", plan, 1)
        assertTrue(summary.contains("2 hiệp bắt buộc"))
        assertTrue(summary.contains("8 đến 12 lần/hiệp"))
        assertTrue(summary.contains("Nghỉ đề xuất 45 giây"))
        assertTrue(summary.contains("Đã xác nhận 1/3 hiệp"))
    }

    @Test fun workoutSummaryUsesTheAdjustedTargetForRemainingSets() {
        val plan = WorkoutSetTracker.Plan(
            requiredSets = 2,
            maximumSets = 3,
            targetMin = 10,
            targetMax = 10,
            unit = "lần",
            restSeconds = 60
        )
        val summary = WorkoutStepGuide.workoutSummary("10 lần (đã chỉnh)", plan, 2)
        assertTrue(summary.contains("10 lần/hiệp"))
        assertTrue(summary.contains("Nghỉ đề xuất 60 giây"))
        assertTrue(summary.contains("hiệp bổ sung còn lại là tùy chọn"))
    }

    @Test fun workoutSummaryDoesNotInviteSetsAfterMaximum() {
        val plan = WorkoutSetTracker.Plan(
            requiredSets = 2,
            maximumSets = 3,
            targetMin = 8,
            targetMax = 12,
            unit = "lần",
            restSeconds = 45
        )
        val summary = WorkoutStepGuide.workoutSummary("Squat", plan, 99)
        assertTrue(summary.contains("Đã ghi nhận đủ 3 hiệp"))
        assertFalse(summary.contains("Đã xác nhận 99"))
    }

    @Test fun timedWorkoutSummaryDoesNotInventSetCounts() {
        val summary = WorkoutStepGuide.workoutSummary("30 giây", null, 0)
        assertTrue(summary.contains("Mục tiêu: 30 giây"))
        assertTrue(summary.contains("Bộ đếm thời gian chỉ là gợi ý"))
        assertFalse(summary.contains("hiệp bắt buộc"))
    }

    @Test fun workoutSummarySanitizesMultilineCatalogDose() {
        val summary = WorkoutStepGuide.workoutSummary("  2 hiệp\n× 8 lần  ", null, 0)
        assertTrue(summary.contains("2 hiệp × 8 lần"))
        assertFalse(summary.contains("\\n"))
    }

    @Test fun autoSpeechOnlyRunsAfterOptInAndRealNavigation() {
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(false, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 0, 2))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 0))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 2))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 3, 2))
    }


    @Test fun automaticStepSpeechRequiresOptInAndAnActualNavigation() {
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(false, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 1))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 2))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 2, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 0, 1))
    }

}
