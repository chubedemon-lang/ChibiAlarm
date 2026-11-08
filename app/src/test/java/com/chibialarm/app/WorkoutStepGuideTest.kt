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
        assertEquals(1, WorkoutStepGuide.split(" ").size)
    }
}
