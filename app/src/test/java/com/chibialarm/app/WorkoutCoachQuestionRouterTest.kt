package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutCoachQuestionRouterTest {
    private val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }

    @Test fun genericFollowUpUsesTheOpenMoveAndCurrentEditedDose() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Bao nhiêu lần?",
            currentMove = squat,
            currentDose = "10 lần (mục tiêu đã chỉnh)"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("10 lần (mục tiêu đã chỉnh)"))
        assertFalse(answer.contains("8–12 lần"))
    }

    @Test fun explicitDifferentExerciseIsAnsweredFromCatalogInsteadOfOpenMove() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Cách tập Đi bộ nhanh tại chỗ?",
            currentMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Đi bộ nhanh tại chỗ"))
        assertFalse(answer.contains("Squat không tạ"))
    }

    @Test fun unknownNamedExerciseIsNotMisattributedToTheOpenMove() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Cách tập Burpee biến thể lạ?",
            currentMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa nhận ra tên động tác"))
        assertFalse(answer.contains("Squat không tạ"))
    }

    @Test fun ambiguousExerciseNameStillRequiresDisambiguation() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Cách tập squat?",
            currentMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertFalse(answer.contains("Mục tiêu hiện tại của Squat không tạ"))
    }

    @Test fun safetyPauseSuppressesMovementInstructionsEvenForContextualQuestions() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Cách tập bài này?",
            currentMove = squat,
            pausedForSafety = true
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertTrue(answer.contains("không cung cấp hướng dẫn vận động"))
        assertFalse(answer.contains("Bước 1:"))
        assertFalse(answer.contains("Mục tiêu tham khảo"))
    }

    @Test fun safetyPauseSuppressesBreathingAdviceAsWellAsTechniqueAdvice() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Thở thế nào?",
            currentMove = squat,
            pausedForSafety = true
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertFalse(answer.contains("thở ra nhẹ ở pha dùng lực"))
    }

    @Test fun unrelatedPlanRequestRemainsForThePlanConversation() {
        assertNull(
            WorkoutCoachQuestionRouter.answer(
                input = "Hôm nay tao muốn tập squat 20 phút không dụng cụ",
                currentMove = squat
            )
        )
    }

    @Test fun genericRestFollowUpUsesTheCurrentExercise() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Nghỉ bao lâu?",
            currentMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("60 giây"))
    }
    @Test fun actualRepQuestionUsesOnlyConfirmedSelfReportedSetValues() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Tao đã làm bao nhiêu lần?",
            currentMove = squat,
            completedSets = 2,
            completedSetValues = listOf(10, 8)
        )
        assertEquals(
            "Tổng số liệu bạn tự khai báo là 18 lần trong 2 hiệp đã ghi. Chibi không tự đếm.",
            answer
        )
    }

    @Test fun remainingRestQuestionUsesTheLiveCountdownInsteadOfRecommendedRest() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Còn bao lâu nghỉ?",
            currentMove = squat,
            restIsActive = true,
            restRemainingMs = 31_200L
        )

        assertEquals(
            "Còn khoảng 32 giây nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án.",
            answer
        )
        assertFalse(answer!!.contains("60 giây"))
    }

    @Test fun pausedRestQuestionReportsTheFrozenCountdownWithoutRestartingIt() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Nghỉ còn lại bao lâu?",
            currentMove = squat,
            restIsActive = true,
            restRemainingMs = 90_001L,
            restIsPaused = true
        )

        assertEquals(
            "Đồng hồ nghỉ đang tạm dừng; còn khoảng 1 phút 31 giây. Chibi chưa tự chạy lại.",
            answer
        )
    }

    @Test fun explicitLiveRestQuestionDoesNotFallBackToRecommendedRestWhenNoTimerRuns() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Còn mấy giây nghỉ?",
            currentMove = squat,
            restIsActive = false,
            restRemainingMs = null
        )

        assertTrue(answer!!.contains("không có đồng hồ nghỉ đang chạy"))
        assertFalse(answer.contains("60 giây"))
    }


    @Test fun numberedStepQuestionUsesTheOpenMoveContext() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Bước 2 của bài này là gì?",
            currentMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/5 của Squat không tạ"))
        assertTrue(answer.contains("đẩy hông nhẹ về sau"))
        assertFalse(answer.contains("Bước 1/5"))
    }

    @Test fun safetyPauseSuppressesNumberedStepGuidance() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Bước 2 của bài này là gì?",
            currentMove = squat,
            pausedForSafety = true
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertFalse(answer.contains("Bước 2/5"))
    }

}
