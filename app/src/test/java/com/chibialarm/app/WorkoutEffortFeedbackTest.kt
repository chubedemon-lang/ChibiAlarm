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

    @Test fun hardEffortAddsOnlyFifteenSecondsOfRestWithoutHistory() {
        val advice = WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.HARD, 45)

        assertEquals(60, advice.restSeconds)
        assertFalse(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("không tăng tải"))
    }

    @Test fun consecutiveHardCheckInsIncreaseOnlyTheOptionalRestSuggestion() {
        val secondHardSet = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD)
        )
        val thirdHardSet = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.HARD)
        )

        assertEquals(75, secondHardSet.restSeconds)
        assertEquals(90, thirdHardSet.restSeconds)
        assertTrue(secondHardSet.message.contains("2 hiệp liên tiếp"))
        assertTrue(thirdHardSet.message.contains("3 hiệp liên tiếp"))
        assertFalse(secondHardSet.holdBeforeNextSet)
    }

    @Test fun moderateEasyOrUnratedPreviousSetBreaksTheHardStreak() {
        val moderateBreak = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.MODERATE)
        )
        val easyBreak = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.EASY)
        )
        val unansweredBreak = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD, null)
        )

        assertEquals(60, moderateBreak.restSeconds)
        assertEquals(60, easyBreak.restSeconds)
        assertEquals(60, unansweredBreak.restSeconds)
    }

    @Test fun restSuggestionIsBoundedAndNeverChangesTheWorkPrescription() {
        val advice = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            180,
            List(20) { WorkoutEffortFeedback.Level.HARD }
        )

        assertEquals(270, advice.restSeconds)
        assertFalse(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("không tăng tải"))
    }

    @Test fun veryHardAlwaysRequiresAnExplicitPauseDecision() {
        val advice = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.VERY_HARD,
            45,
            listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.HARD)
        )

        assertEquals(120, advice.restSeconds)
        assertTrue(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("tạm dừng"))
    }

    @Test fun veryHardNeverShortensALongerPrescribedRest() {
        val advice = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.VERY_HARD,
            180,
            listOf(WorkoutEffortFeedback.Level.HARD)
        )

        assertEquals(180, advice.restSeconds)
        assertTrue(advice.holdBeforeNextSet)
        assertTrue(advice.message.contains("tạm dừng"))
    }

    @Test fun confirmedNumericEightAddsOneMoreRestIncrementThanSeven() {
        val seven = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, emptyList(), 7
        )
        val eight = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, emptyList(), 8
        )

        assertEquals(60, seven.restSeconds)
        assertEquals(75, eight.restSeconds)
        assertTrue(eight.message.contains("8/10"))
        assertFalse(eight.holdBeforeNextSet)
    }

    @Test fun numericEightRefinesTheExistingHardStreakWithoutChangingTheWorkPrescription() {
        val previousHard = listOf(WorkoutEffortFeedback.Level.HARD)
        val seven = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, previousHard, 7
        )
        val eight = WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, previousHard, 8
        )

        assertEquals(75, seven.restSeconds)
        assertEquals(90, eight.restSeconds)
        assertFalse(eight.holdBeforeNextSet)
        assertTrue(eight.message.contains("2 hiệp liên tiếp"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNumericEffortOutsideTheOneToTenScale() {
        WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, emptyList(), 11
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNumericEffortThatContradictsTheSelectedCategory() {
        WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD, 45, emptyList(), 9
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRestBelowCatalogBounds() {
        WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.HARD, 14)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRestAboveCatalogBounds() {
        WorkoutEffortFeedback.recommend(WorkoutEffortFeedback.Level.MODERATE, 181)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedPreviousSetHistory() {
        WorkoutEffortFeedback.recommend(
            WorkoutEffortFeedback.Level.HARD,
            45,
            List(21) { WorkoutEffortFeedback.Level.HARD }
        )
    }
}
