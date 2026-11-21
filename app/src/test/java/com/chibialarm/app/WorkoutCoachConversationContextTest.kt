package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutCoachConversationContextTest {
    private val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
    private val walk = TrainingCatalog.allMoves.single { it.name == "Đi bộ nhanh tại chỗ" }
    private val chairSquat = TrainingCatalog.allMoves.single { it.name == "Đứng lên – ngồi xuống" }

    @Test fun followUpAboutThatExerciseUsesTheLastExplicitlyNamedCatalogMove() {
        val context = WorkoutCoachConversationContext()

        val first = context.answer(
            input = "Cách tập Đi bộ nhanh tại chỗ?",
            activeMove = squat,
            activeDose = "10 lần (mục tiêu đã chỉnh)"
        )
        assertNotNull(first)
        assertTrue(first!!.contains("Đi bộ nhanh tại chỗ"))

        val followUp = context.answer(
            input = "Bài đó cần dụng cụ gì?",
            activeMove = squat,
            activeDose = "10 lần (mục tiêu đã chỉnh)"
        )
        assertNotNull(followUp)
        assertTrue(followUp!!.contains(walk.name))
        assertTrue(followUp.contains("không yêu cầu dụng cụ riêng"))
        assertFalse(followUp.contains(squat.name))
    }

    @Test fun ambiguousExerciseQuestionDoesNotReplaceThePreviousReference() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập Đi bộ nhanh tại chỗ?", activeMove = squat)

        val ambiguous = context.answer("Cách tập squat?", activeMove = squat)
        assertNotNull(ambiguous)
        assertTrue(ambiguous!!.contains("vài động tác gần giống"))

        val followUp = context.answer("Bài đó cần dụng cụ gì?", activeMove = squat)
        assertNotNull(followUp)
        assertTrue(followUp!!.contains(walk.name))
        assertFalse(followUp.contains(squat.name))
    }

    @Test fun nextMoveQuestionUsesTheUpcomingCatalogMoveWithoutChangingPreviousReference() {
        val context = WorkoutCoachConversationContext()
        val answer = context.answer(
            input = "Bài tiếp theo cần dụng cụ gì?",
            activeMove = squat,
            nextMove = walk
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains(walk.name))
        assertTrue(answer.contains("không yêu cầu dụng cụ riêng"))
        assertFalse(answer.contains(squat.name))

        val ordinaryFollowUp = context.answer("Nghỉ bao lâu?", activeMove = squat, nextMove = walk)
        assertNotNull(ordinaryFollowUp)
        assertTrue(ordinaryFollowUp!!.contains(squat.name))
        assertTrue(ordinaryFollowUp.contains("60 giây"))
    }

    @Test fun ordinaryGenericFollowUpStillUsesTheCurrentlyOpenMove() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập Đi bộ nhanh tại chỗ?", activeMove = squat)

        val rest = context.answer("Nghỉ bao lâu?", activeMove = squat)
        assertNotNull(rest)
        assertTrue(rest!!.contains(squat.name))
        assertTrue(rest.contains("60 giây"))
    }

    @Test fun previousExerciseReferenceWithoutConversationMemoryDoesNotGuessTheActiveMove() {
        val context = WorkoutCoachConversationContext()

        val answer = context.answer(
            input = "Bài đó cần dụng cụ gì?",
            activeMove = squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa nhận ra tên động tác"))
        assertFalse(answer.contains(squat.name))
    }

    @Test fun followUpBreathingQuestionUsesTheLastExplicitlyNamedExercise() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập Đi bộ nhanh tại chỗ?", activeMove = squat)

        val followUp = context.answer("Bài đó thở thế nào?", activeMove = squat)
        assertNotNull(followUp)
        assertTrue(followUp!!.contains(walk.name))
        assertTrue(followUp.contains("Thở tự nhiên"))
        assertFalse(followUp.contains(squat.name))
    }

    @Test fun resetClearsPreviousExerciseReference() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập Đi bộ nhanh tại chỗ?", activeMove = squat)
        context.reset()

        val answer = context.answer("Bài đó cần dụng cụ gì?", activeMove = squat)
        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa nhận ra tên động tác"))
        assertFalse(answer.contains(walk.name))
    }

    @Test fun safetyPauseNeverProvidesTheRememberedExerciseInstructions() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập Đi bộ nhanh tại chỗ?", activeMove = squat)

        val answer = context.answer(
            input = "Bài đó tập thế nào?",
            activeMove = squat,
            pausedForSafety = true
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("đang dừng vì cảnh báo an toàn"))
        assertFalse(answer.contains("Bước 1:"))
    }
    @Test fun activeMoveActualRepValuesArePassedThroughConversationContext() {
        val context = WorkoutCoachConversationContext()
        val answer = context.answer(
            input = "Tôi đã làm bao nhiêu lần?",
            activeMove = squat,
            activeCompletedSets = 2,
            activeCompletedSetValues = listOf(10, 8)
        )
        assertEquals(
            "Tổng số liệu bạn tự khai báo là 18 lần trong 2 hiệp đã ghi. Chibi không tự đếm.",
            answer
        )
    }

    @Test fun previousExerciseQuestionCannotBorrowTheActiveMoveActualRepValues() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập ${chairSquat.name}?", activeMove = squat)

        val answer = context.answer(
            input = "Bài đó tôi đã làm bao nhiêu lần?",
            activeMove = squat,
            activeCompletedSets = 2,
            activeCompletedSetValues = listOf(10, 8)
        )
        assertTrue(answer!!.contains("chưa có tiến độ xác nhận"))
        assertFalse(answer.contains("18 lần"))
    }

    @Test fun liveRestCountdownIsPassedThroughConversationContext() {
        val context = WorkoutCoachConversationContext()
        val answer = context.answer(
            input = "Còn bao lâu?",
            activeMove = squat,
            restIsActive = true,
            restRemainingMs = 45_001L
        )

        assertEquals(
            "Còn khoảng 46 giây nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án.",
            answer
        )
    }


    @Test fun numberedStepFollowUpUsesTheLastExplicitlyNamedExercise() {
        val context = WorkoutCoachConversationContext()
        context.answer("Cách tập ${walk.name}?", activeMove = squat)

        val answer = context.answer(
            input = "Bước thứ hai của bài đó là gì?",
            activeMove = squat
        )

        val expectedStep = WorkoutStepGuide.split(walk.steps)[1]
        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/"))
        assertTrue(answer.contains(walk.name))
        assertTrue(answer.contains(expectedStep.take(50)))
        assertFalse(answer.contains(squat.name))
    }

}
