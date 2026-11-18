package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetResultCoachTest {
    private val repetitions = WorkoutSetTracker.Plan(
        requiredSets = 2,
        maximumSets = 3,
        targetMin = 8,
        targetMax = 12,
        unit = "lần"
    )

    @Test fun belowTargetExplainsThatTheUserDoesNotNeedToMakeUpReps() {
        val result = WorkoutSetResultCoach.feedback(
            moveName = "Squat không tạ",
            setNumber = 1,
            actualValue = 6,
            plan = repetitions,
            completedSetsAfter = 1
        )

        assertNotNull(result)
        assertEquals(WorkoutSetResultCoach.Comparison.BELOW_TARGET, result!!.comparison)
        assertTrue(result.message.contains("6 lần"))
        assertTrue(result.message.contains("8 đến 12 lần"))
        assertTrue(result.message.contains("không cần cố bù ngay"))
        assertTrue(result.message.contains("còn 1 hiệp bắt buộc"))
    }

    @Test fun valueInsideTargetUsesNeutralFeedbackAndKeepsThePrescription() {
        val result = WorkoutSetResultCoach.feedback(
            "Squat không tạ", 1, 10, repetitions, 1
        )!!

        assertEquals(WorkoutSetResultCoach.Comparison.IN_TARGET, result.comparison)
        assertTrue(result.message.contains("nằm trong khoảng mục tiêu"))
        assertTrue(result.message.contains("Không cần tăng mục tiêu"))
        assertFalse(result.message.contains("tập thêm động tác này"))
    }

    @Test fun aboveTargetDoesNotEncourageIncreasingTheNextTarget() {
        val result = WorkoutSetResultCoach.feedback(
            "Squat không tạ", 2, 15, repetitions, 2
        )!!

        assertEquals(WorkoutSetResultCoach.Comparison.ABOVE_TARGET, result.comparison)
        assertTrue(result.message.contains("cao hơn khoảng mục tiêu"))
        assertTrue(result.message.contains("không tự nâng mục tiêu"))
        assertTrue(result.message.contains("hiệp tiếp theo chỉ là tùy chọn"))
    }

    @Test fun finalOptionalSetIsClearlyOptionalAndDoesNotInviteMoreSets() {
        val result = WorkoutSetResultCoach.feedback(
            "Squat không tạ", 3, 10, repetitions, 3
        )!!

        assertTrue(result.message.contains("hiệp 3/3"))
        assertTrue(result.message.contains("Đã đủ số hiệp tối đa"))
        assertTrue(result.message.contains("không cần tập thêm động tác này"))
    }

    @Test fun timedHoldsAreDescribedAsSecondsHeldRatherThanRepetitions() {
        val timed = WorkoutSetTracker.Plan(
            requiredSets = 2,
            maximumSets = 2,
            targetMin = 15,
            targetMax = 30,
            unit = "giây"
        )
        val result = WorkoutSetResultCoach.feedback(
            "Plank", 1, 20, timed, 1
        )!!

        assertTrue(result.message.contains("20 giây giữ"))
        assertTrue(result.message.contains("15 đến 30 giây"))
    }

    @Test fun rejectsMismatchedSetIndexesAndOutOfRangeValues() {
        assertNull(WorkoutSetResultCoach.feedback("Squat", 2, 10, repetitions, 1))
        assertNull(WorkoutSetResultCoach.feedback("Squat", 1, 0, repetitions, 1))
        assertNull(WorkoutSetResultCoach.feedback("Squat", 1, 201, repetitions, 1))
        assertNull(WorkoutSetResultCoach.feedback("   ", 1, 10, repetitions, 1))
    }

    @Test fun removesControlCharactersFromCatalogLabelsBeforeSpeech() {
        val result = WorkoutSetResultCoach.feedback(
            "Squat\nkhông tạ", 1, 10, repetitions, 1
        )!!

        assertFalse(result.message.contains("\n"))
        assertTrue(result.message.contains("Squat không tạ"))
    }
}
