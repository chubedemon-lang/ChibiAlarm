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
}
