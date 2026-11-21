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

    @Test fun latestSetQuestionReportsOnlyTheLastSelfReportedValue() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Hiệp vừa rồi tao làm bao nhiêu lần?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = listOf(10, 8)
        )
        assertEquals(
            "Hiệp gần nhất đã ghi nhận (hiệp 2): bạn tự khai báo 8 lần. Chibi không tự đếm.",
            answer
        )
    }

    @Test fun totalQuestionSumsOnlySelfReportedValues() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Tôi đã làm bao nhiêu lần?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = listOf(10, 8)
        )
        assertEquals(
            "Tổng số liệu bạn tự khai báo là 18 lần trong 2 hiệp đã ghi. Chibi không tự đếm.",
            answer
        )
    }

    @Test fun totalQuestionDoesNotInventValuesForManuallyConfirmedSets() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Tổng số lần đã làm là bao nhiêu?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = listOf(10, 0)
        )
        assertEquals(
            "Tổng số liệu bạn tự khai báo là 10 lần trong 1 hiệp có số liệu; 1 hiệp khác chưa có số liệu. Chibi không suy ra số lần từ mục tiêu giáo án.",
            answer
        )
    }

    @Test fun latestQuestionDoesNotBorrowAnOlderSetValueWhenTheLastSetWasUnreported() {
        val answer = WorkoutSetStatusConversation.answer(
            input = "Hiệp gần nhất làm bao nhiêu lần?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = listOf(10, 0)
        )
        assertTrue(answer!!.contains("Hiệp gần nhất đã ghi chưa có số liệu thực tế"))
    }

    @Test fun missingOrInvalidActualValuesAreNeverGuessed() {
        val missing = WorkoutSetStatusConversation.answer(
            input = "Tôi đã làm bao nhiêu lần?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = null
        )
        val invalid = WorkoutSetStatusConversation.answer(
            input = "Tôi đã làm bao nhiêu lần?",
            currentDose = squat.dose,
            fallbackDose = null,
            completedSets = 2,
            completedSetValues = listOf(10, 999)
        )
        assertTrue(missing!!.contains("Chibi chưa có số liệu thực tế"))
        assertTrue(invalid!!.contains("chưa đọc được số liệu thực tế hợp lệ"))
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
