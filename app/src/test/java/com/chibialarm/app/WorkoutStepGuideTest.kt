package com.chibialarm.app

import org.junit.Assert.assertEquals
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

    @Test fun keepsDecimalWeightsAndTimesIntact() {
        assertEquals(listOf("Giữ tạ 2.5 kg.", "Nghỉ 1.5 phút."),
            WorkoutStepGuide.split("Giữ tạ 2.5 kg. Nghỉ 1.5 phút."))
    }

    @Test fun skipsEmptyBullets() {
        assertEquals(listOf("Nâng tay"), WorkoutStepGuide.split("•\n-\n3) Nâng tay"))
    }

    @Test fun walkthroughMovesBackwardAndForwardWithoutLeavingBounds() {
        val first = WorkoutStepGuide.Walkthrough.start(
            "Đứng thẳng.\\nHạ người.\\nĐứng lên."
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
}
