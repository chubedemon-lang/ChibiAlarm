package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortRestAdviceConversationTest {
    private val hard = WorkoutEffortFeedback.Level.HARD
    private val veryHard = WorkoutEffortFeedback.Level.VERY_HARD
    private val easy = WorkoutEffortFeedback.Level.EASY

    @Test fun repeatedHardCheckInsExplainTheBoundedRestSuggestion() {
        val answer = WorkoutEffortRestAdviceConversation.answer(
            input = "Nghỉ thêm bao lâu?",
            prescribedRestSeconds = 45,
            completedSets = 2,
            effortLevels = listOf(hard, hard)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("hiệp 2"))
        assertTrue(answer.contains("75 giây"))
        assertTrue(answer.contains("giáo án: 45 giây"))
        assertTrue(answer.contains("không tăng số lần"))
    }

    @Test fun latestUnratedSetNeverBorrowsThePreviousHardRating() {
        val answer = WorkoutEffortRestAdviceConversation.answer(
            input = "Chibi đề xuất nghỉ bao lâu?",
            prescribedRestSeconds = 45,
            completedSets = 2,
            effortLevels = listOf(hard, null)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Hiệp 2 chưa có check-in"))
        assertTrue(answer.contains("45 giây"))
        assertFalse(answer.contains("75 giây"))
        assertFalse(answer.contains("Khó, cần nghỉ thêm"))
    }

    @Test fun shortEffortLogDoesNotTreatAnEarlierSetAsTheLatestSet() {
        val answer = WorkoutEffortRestAdviceConversation.answer(
            input = "Nghỉ thêm bao lâu?",
            prescribedRestSeconds = 45,
            completedSets = 2,
            effortLevels = listOf(hard)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Hiệp 2 chưa có check-in"))
        assertFalse(answer.contains("75 giây"))
    }

    @Test fun veryHardCheckInKeepsTheSessionPausedInsteadOfTreatingRestAsClearance() {
        val answer = WorkoutEffortRestAdviceConversation.answer(
            input = "Vì sao phải nghỉ thêm?",
            prescribedRestSeconds = 45,
            completedSets = 1,
            effortLevels = listOf(veryHard)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("giữ buổi tập tạm dừng"))
        assertTrue(answer.contains("không tự động cho phép tiếp tục"))
    }

    @Test fun easyCheckInKeepsPrescribedRestAndDoesNotEncourageMoreWork() {
        val answer = WorkoutEffortRestAdviceConversation.answer(
            input = "Nghỉ thêm bao lâu?",
            prescribedRestSeconds = 60,
            completedSets = 1,
            effortLevels = listOf(easy)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("60 giây theo giáo án"))
        assertFalse(answer.contains("tăng số lần"))
    }

    @Test fun genericRestQuestionIsLeftForTheCatalogRestAnswer() {
        assertNull(
            WorkoutEffortRestAdviceConversation.answer(
                input = "Nghỉ bao lâu?",
                prescribedRestSeconds = 45,
                completedSets = 1,
                effortLevels = listOf(hard)
            )
        )
    }

    @Test fun invalidCountsAndRestValuesAreNotUsedForAdvice() {
        assertNull(
            WorkoutEffortRestAdviceConversation.answer(
                input = "Nghỉ thêm bao lâu?",
                prescribedRestSeconds = 10,
                completedSets = 1,
                effortLevels = listOf(hard)
            )
        )
        assertNull(
            WorkoutEffortRestAdviceConversation.answer(
                input = "Nghỉ thêm bao lâu?",
                prescribedRestSeconds = 45,
                completedSets = 21,
                effortLevels = listOf(hard)
            )
        )
    }

    @Test fun routerUsesOnlyTheActiveExerciseEffortAndNeverStartsTheTimer() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }.copy(restSeconds = 45)
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Nghỉ thêm bao lâu?",
            currentMove = squat,
            completedSets = 2,
            completedEffortLevels = listOf(hard, hard)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("75 giây"))
        assertTrue(answer.contains("đồng hồ không tự chạy"))
    }

    @Test fun safetyPauseOverridesAnEffortBasedRestAnswer() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }.copy(restSeconds = 45)
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Nghỉ thêm bao lâu?",
            currentMove = squat,
            pausedForSafety = true,
            completedSets = 2,
            completedEffortLevels = listOf(hard, hard)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertFalse(answer.contains("75 giây"))
    }
}
