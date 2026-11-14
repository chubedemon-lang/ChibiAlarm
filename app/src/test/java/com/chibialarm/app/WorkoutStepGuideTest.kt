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

    @Test fun autoSpeechOnlyRunsAfterOptInAndRealNavigation() {
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(false, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, null, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 1))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 0, 2))
        assertFalse(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 0))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 1, 2))
        assertTrue(WorkoutStepGuide.shouldSpeakOnNavigation(true, 3, 2))
    }

}
