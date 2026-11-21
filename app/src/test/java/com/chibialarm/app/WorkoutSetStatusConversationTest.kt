package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetStatusConversationTest {
    private val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
    private val chairSquat = TrainingCatalog.allMoves.single { it.name == "Đứng lên – ngồi xuống" }

    @Test fun remainingQuestionSeparatesRequiredAndOptionalSets() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Còn mấy hiệp nữa?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 1
        )
        assertEquals(
            "Còn 1 hiệp bắt buộc. Sau đó có tối đa 1 hiệp tùy chọn; phần tùy chọn không bắt buộc. Số hiệp do bạn tự xác nhận.",
            answer
        )
    }

    @Test fun afterRequiredMinimumOnlyOptionalVolumeRemains() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Còn bao nhiêu hiệp?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2
        )
        assertTrue(answer!!.contains("Đã đủ 2/2 hiệp bắt buộc"))
        assertTrue(answer.contains("Còn tối đa 1 hiệp tùy chọn"))
        assertFalse(answer.contains("không bắt buộc"))
    }

    @Test fun completedQuestionReportsRequiredAndOptionalCountsSeparately() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Tao đã làm mấy hiệp?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 3
        )
        assertEquals(
            "Bạn đã tự xác nhận 2/2 hiệp bắt buộc. Hiệp tùy chọn đã ghi: 1/1. Số hiệp do bạn tự xác nhận, không phải cảm biến đo.",
            answer
        )
    }

    @Test fun fixedPrescriptionDoesNotInventOptionalSets() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Còn mấy hiệp nữa?",
            currentDose = chairSquat.dose,
            fallbackDose = null,
            completedSets = 1
        )
        assertEquals("Còn 1 hiệp bắt buộc. Số hiệp do bạn tự xác nhận.", answer)
    }

    @Test fun editedDoseFallsBackToCatalogSetPrescriptionWhenNeeded() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Còn mấy hiệp nữa?",
            currentDose = "8–10 lần (mục tiêu đã chỉnh)",
            fallbackDose = squat.dose,
            completedSets = 1
        )
        assertTrue(answer!!.contains("Còn 1 hiệp bắt buộc"))
    }

    @Test fun timeOnlyExerciseDoesNotPretendToHaveSets() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Còn mấy hiệp nữa?",
            currentDose = "2 phút",
            fallbackDose = "2 phút",
            completedSets = 0
        )
        assertTrue(answer!!.contains("không có mục tiêu theo hiệp"))
    }

    @Test fun unknownProgressAndCorruptCountsAreNeverGuessed() {
        val missing = WorkoutSetStatusConversation.answer(
            "Còn mấy hiệp nữa?", squat.dose, null, null
        )
        val corrupt = WorkoutSetStatusConversation.answer(
            "Còn mấy hiệp nữa?", squat.dose, null, 99
        )
        assertTrue(missing!!.contains("chưa có tiến độ xác nhận"))
        assertTrue(corrupt!!.contains("chưa đọc được tiến độ hiệp hợp lệ"))
    }

    @Test fun unrelatedRepAndRestQuestionsAreNotCaptured() {
        assertNull(WorkoutSetStatusConversation.answer("Bao nhiêu lần?", squat.dose, null, 1))
        assertNull(WorkoutSetStatusConversation.answer("Nghỉ bao lâu?", squat.dose, null, 1))
        assertNull(WorkoutSetStatusConversation.answer("Thêm 30 giây nữa", squat.dose, null, 1))
    }

    @Test fun routerUsesTheLiveConfirmedCountForTheOpenMove() {
        val answer = WorkoutCoachQuestionRouter.answer(
            input = "Còn mấy hiệp nữa?",
            currentMove = squat,
            completedSets = 1
        )
        assertTrue(answer!!.contains("Còn 1 hiệp bắt buộc"))
        assertTrue(answer.contains("1 hiệp tùy chọn"))
    }

    @Test fun conversationContextPassesProgressOnlyForTheActiveMove() {
        val context = WorkoutCoachConversationContext()
        val activeAnswer = context.answer(
            input = "Còn mấy hiệp nữa?",
            activeMove = squat,
            activeCompletedSets = 1
        )
        assertTrue(activeAnswer!!.contains("Còn 1 hiệp bắt buộc"))

        context.answer("Cách tập ${chairSquat.name}?", activeMove = squat)
        val previousMoveAnswer = context.answer(
            input = "Bài đó còn mấy hiệp?",
            activeMove = squat,
            activeCompletedSets = 1
        )
        assertTrue(previousMoveAnswer!!.contains("chưa có tiến độ xác nhận"))
    }
}
