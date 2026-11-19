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
}
