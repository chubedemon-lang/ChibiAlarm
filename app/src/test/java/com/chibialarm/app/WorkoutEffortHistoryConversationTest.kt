package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortHistoryConversationTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun guided(
        date: LocalDate,
        effortLog: String,
        id: String,
        note: String = "private health note"
    ) = Workout(
        date = date.toString(),
        name = "Toàn thân",
        minutes = 30,
        note = note,
        sessionId = id,
        completedMoves = 2,
        selfReportedSets = 2,
        effortLog = effortLog
    )

    @Test fun answersLatestEffortQuestionForNamedExerciseWithoutReadingNotes() {
        val older = guided(
            today.minusDays(2),
            "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440041",
            "private older knee note"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Vừa sức; Plank: hiệp 1: Nhẹ, còn dư sức",
            "550e8400-e29b-41d4-a716-446655440042",
            "private current health note"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Squat lần trước tao cảm thấy thế nào?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Check-in gần nhất cho Squat không tạ"))
        assertTrue(answer.contains("hiệp 1: Khó, cần nghỉ thêm"))
        assertTrue(answer.contains("hiệp 2: Vừa sức"))
        assertFalse(answer.contains("private"))
        assertFalse(answer.contains("knee note"))
    }

    @Test fun comparesTwoLatestEffortLogsForNamedExercise() {
        val older = guided(
            today.minusDays(3),
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440043"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440044"
        )

        val answer = WorkoutHistoryConversation.answer(
            "So sánh cảm nhận squat giữa hai buổi gần nhất",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Đối chiếu cảm nhận tự khai báo"))
        assertTrue(answer.contains("Buổi mới nhất"))
        assertTrue(answer.contains("Khó, cần nghỉ thêm"))
        assertTrue(answer.contains("Buổi trước"))
        assertTrue(answer.contains("Vừa sức"))
    }

    @Test fun findsOnlyExplicitlySelfReportedVeryHardSets() {
        val workout = guided(
            today,
            "Squat: hiệp 1: Quá sức, tạm dừng; Plank: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440045"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tao đã quá sức ở bài nào?",
            listOf(workout),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat, hiệp 1"))
        assertFalse(answer.contains("Plank, hiệp 1"))
        assertTrue(answer.contains("tự đánh dấu"))
        assertFalse(answer.contains("chẩn đoán"))
    }

    @Test fun ignoresLegacyMalformedFutureAndOversizedEffortRecords() {
        val validButFuture = guided(
            today.plusDays(1),
            "Squat: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440046"
        )
        val invalidId = guided(today, "Squat: hiệp 1: Quá sức, tạm dừng", "not-a-uuid")
        val legacy = Workout(today.toString(), "Buổi cũ", 20, "private note")
        val oversized = guided(
            today,
            "x".repeat(1_201),
            "550e8400-e29b-41d4-a716-446655440047"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Mức độ gắng sức buổi gần nhất thế nào?",
            listOf(validButFuture, invalidId, legacy, oversized),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa có check-in cảm nhận hợp lệ"))
        assertFalse(answer.contains("private"))
    }

    @Test fun ordinaryWorkoutPlanRequestIsNotConsumedAsHistory() {
        assertNull(
            WorkoutHistoryConversation.answer(
                "Hôm nay mệt, muốn giãn cơ 10 phút không dụng cụ",
                emptyList(),
                today
            )
        )
        assertNull(
            WorkoutEffortHistoryConversation.answer(
                "Bài này khó quá, cho tao nghỉ thêm",
                emptyList(),
                today
            )
        )
    }
}
