package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSpeechRateTest {
    @Test fun slowerReducesRateInSmallStepsAndStopsAtMinimum() {
        assertEquals(0.85f, WorkoutSpeechRate.slower(1.0f), 0.001f)
        assertEquals(0.70f, WorkoutSpeechRate.slower(0.85f), 0.001f)
        assertEquals(WorkoutSpeechRate.MIN_RATE, WorkoutSpeechRate.slower(0.5f), 0.001f)
        assertEquals(WorkoutSpeechRate.MIN_RATE, WorkoutSpeechRate.slower(0.6f), 0.001f)
    }

    @Test fun normalRestoresThePreferredSettingWithinPlatformBounds() {
        assertEquals(1.2f, WorkoutSpeechRate.normal(1.2f), 0.001f)
        assertEquals(WorkoutSpeechRate.MIN_RATE, WorkoutSpeechRate.normal(0.1f), 0.001f)
        assertEquals(WorkoutSpeechRate.MAX_RATE, WorkoutSpeechRate.normal(3.0f), 0.001f)
    }

    @Test fun invalidStoredRatesFallBackToDefault() {
        assertEquals(WorkoutSpeechRate.DEFAULT_RATE, WorkoutSpeechRate.normalize(Float.NaN), 0.001f)
        assertEquals(WorkoutSpeechRate.DEFAULT_RATE, WorkoutSpeechRate.normalize(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(WorkoutSpeechRate.DEFAULT_RATE, WorkoutSpeechRate.normalize(Float.NEGATIVE_INFINITY), 0.001f)
    }
}
