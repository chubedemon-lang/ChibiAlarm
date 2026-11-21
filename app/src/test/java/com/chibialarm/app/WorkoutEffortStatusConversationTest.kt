package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortStatusConversationTest {
    private val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }

    @Test fun latestSetQuestionUsesTheLastConfirmedSubjectiveRating() {
        val answer = WorkoutEffortStatusConversation.answer(
            "Hiệp vừa rồi tao thấy khó mấy?",
            2,
            listOf(WorkoutEffortFeedback.Level.EASY, WorkoutEffortFeedback.Level.HARD)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("hiệp 2"))
        assertTrue(answer.contains("Khó, cần nghỉ thêm"))
        assertTrue(answer.contains("tự đánh giá"))
    }

    @Test fun unratedLatestSetDoesNotBorrowThePreviousSetRating() {
        val answer = WorkoutEffortStatusConversation.answer(
            "Mức độ gắng sức hiệp gần nhất?",
            2,
            listOf(WorkoutEffortFeedback.Level.EASY, null)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Hiệp 2 chưa có đánh giá"))
        assertFalse(answer.contains("Nhẹ, còn dư sức"))
    }

    @Test fun summaryIncludesOnlyRatedConfirmedSets() {
        val answer = WorkoutEffortStatusConversation.answer(
            "Tổng kết cảm nhận các hiệp",
            2,
            listOf(
                WorkoutEffortFeedback.Level.MODERATE,
                WorkoutEffortFeedback.Level.HARD,
                WorkoutEffortFeedback.Level.VERY_HARD
            )
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("hiệp 1: Vừa sức"))
        assertTrue(answer.contains("hiệp 2: Khó, cần nghỉ thêm"))
        assertFalse(answer.contains("hiệp 3"))
    }

    @Test fun noConfirmedSetsNeverInventsAnEffortRating() {
        val answer = WorkoutEffortStatusConversation.answer(
            "Mức độ gắng sức của tao?",
            0,
            listOf(WorkoutEffortFeedback.Level.HARD)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa có hiệp nào được xác nhận"))
        assertFalse(answer.contains("Khó, cần nghỉ thêm"))
    }

    @Test fun unrelatedRestQuestionIsNotCapturedAsAnEffortQuestion() {
        assertNull(
            WorkoutEffortStatusConversation.answer(
                "Nghỉ bao lâu?",
                1,
                listOf(WorkoutEffortFeedback.Level.HARD)
            )
        )
    }

    @Test fun routerAnswersEffortQuestionsFromTheSuppliedConfirmedLog() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Hiệp vừa rồi tao thấy khó thế nào?",
            currentMove = squat,
            completedSets = 1,
            completedEffortLevels = listOf(WorkoutEffortFeedback.Level.VERY_HARD)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Quá sức, tạm dừng"))
        assertTrue(answer.contains("tự đánh giá"))
    }

    @Test fun safetyPauseStillOverridesEffortConversation() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Hiệp vừa rồi tao thấy khó mấy?",
            currentMove = squat,
            pausedForSafety = true,
            completedSets = 1,
            completedEffortLevels = listOf(WorkoutEffortFeedback.Level.HARD)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertFalse(answer.contains("Khó, cần nghỉ thêm"))
    }

    @Test fun conversationContextPassesEffortOnlyForTheActiveExercise() {
        val context = WorkoutCoachConversationContext()
        val answer = context.answer(
            input = "Hiệp vừa rồi tao thấy khó mấy?",
            activeMove = squat,
            activeCompletedSets = 1,
            activeEffortLevels = listOf(WorkoutEffortFeedback.Level.HARD)
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Khó, cần nghỉ thêm"))
    }

    @Test fun previousExerciseCannotBorrowTheActiveExerciseEffortRatings() {
        val previous = TrainingCatalog.allMoves.single { it.name == "Đứng lên – ngồi xuống" }
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập ${previous.name}?", activeMove = squat)

        val answer = context.answer(
            input = "Bài đó hiệp vừa rồi tao thấy khó mấy?",
            activeMove = squat,
            activeCompletedSets = 2,
            activeEffortLevels = listOf(
                WorkoutEffortFeedback.Level.EASY,
                WorkoutEffortFeedback.Level.HARD
            )
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa có hiệp nào được xác nhận"))
        assertFalse(answer.contains("Khó, cần nghỉ thêm"))
    }
}
