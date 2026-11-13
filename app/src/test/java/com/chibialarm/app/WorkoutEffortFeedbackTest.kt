package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEffortFeedbackTest {
    @Test fun easyAndModerateKeepThePrescribedRestWithoutIncreasingWorkload() {
        val easy = WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.EASY, 45)
        val moderate = WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.MODERATE, 75)

        assertEquals(45, easy.restSeconds)
        assertEquals(75, moderate.restSeconds)
        assertFalse(easy.holdBeforeNextSet)
        assertFalse(moderate.holdBeforeNextSet)
        assertTrue(easy.message.contains("không tự tăng"))
    }

    @Test fun hardEffortAddsOnlyFifteenSecondsOfRest() {
        val advice = WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.HARD, 45)

        assertEquals(60, advice.restSeconds)
        assertFalse(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("không tăng tải"))
    }

    @Test fun veryHardEffortRequiresAnExplicitPauseDecision() {
        val advice = WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.VERY_HARD, 45)

        assertEquals(120, advice.restSeconds)
        assertTrue(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("tạm dừng"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRestBelowCatalogBounds() {
        WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.HARD, 14)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRestAboveCatalogBounds() {
        WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.MODERATE, 181)
    }
}
