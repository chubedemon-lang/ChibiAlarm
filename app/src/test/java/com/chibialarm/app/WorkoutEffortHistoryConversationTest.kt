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

    @Test fun ranksHardestExercisesFromRecentSelfReportedSessions() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Burpee: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440051"
        )
        val previous = guided(
            today.minusDays(1),
            "Burpee: hiệp 1: Khó, cần nghỉ thêm; Plank: hiệp 1: Nhẹ, còn dư sức",
            "550e8400-e29b-41d4-a716-446655440052"
        )
        val older = guided(
            today.minusDays(2),
            "Burpee: hiệp 1: Vừa sức; Squat: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440053"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào làm tao đuối nhất?",
            listOf(older, previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("các bài bạn tự đánh dấu khó nhất"))
        assertTrue(answer.indexOf("Burpee") < answer.indexOf("Squat"))
        assertTrue(answer.contains("1 hiệp khó"))
        assertTrue(answer.contains("1 hiệp quá sức"))
        assertTrue(answer.contains("cảm nhận tự khai báo"))
        assertFalse(answer.contains("private health note"))
    }

    @Test fun hardestExerciseAnswerUsesOnlyFiveMostRecentSessions() {
        val recent = (0..4).map { day ->
            guided(
                today.minusDays(day.toLong()),
                "Squat: hiệp 1: Vừa sức",
                "550e8400-e29b-41d4-a716-4466554400${60 + day}"
            )
        }
        val oldHardSession = guided(
            today.minusDays(6),
            "Burpee: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440066"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào khó nhất?",
            listOf(oldHardSession) + recent,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa có hiệp nào"))
        assertFalse(answer.contains("Burpee"))
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
    @Test fun findsTheHardestSetForANamedExerciseInTheLatestSessionOnly() {
        val older = guided(
            today.minusDays(2),
            "Squat: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440071"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Khó, cần nghỉ thêm, hiệp 3: Quá sức, tạm dừng; Plank: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440072"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào khó nhất của Squat hôm nay?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ — hiệp 3: Quá sức, tạm dừng"))
        assertFalse(answer.contains("Plank"))
        assertTrue(answer.contains("cảm nhận tự khai báo"))
    }

    @Test fun unqualifiedHardestSetQuestionReportsTiesAcrossExercises() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Plank: hiệp 2: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440073"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào khó nhất trong buổi gần nhất?",
            listOf(latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat — hiệp 1: Khó, cần nghỉ thêm"))
        assertTrue(answer.contains("Plank — hiệp 2: Khó, cần nghỉ thêm"))
    }

    @Test fun hardestSetAnswerDoesNotCallEasyOrModerateSetsHard() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440074"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào khó nhất của Squat?",
            listOf(latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa có hiệp nào"))
        assertTrue(answer.contains("Mức cao nhất đã ghi là"))
        assertTrue(answer.contains("hiệp 2: Vừa sức"))
        assertFalse(answer.contains("Khó, cần nghỉ thêm"))
    }

    @Test fun hardestSetAnswerNeverFallsBackToAnOlderSessionForAMissingExercise() {
        val older = guided(
            today.minusDays(1),
            "Squat: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440075"
        )
        val latest = guided(
            today,
            "Plank: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440076"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào khó nhất của Squat?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("không có dữ liệu cho Squat"))
        assertFalse(answer.contains("Quá sức"))
    }

    @Test fun ambiguousExerciseNamesRequireClarificationBeforeRankingSets() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Squat jump: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440077"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào khó nhất của Squat?",
            listOf(latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertFalse(answer.contains("hiệp 1: Quá sức"))
    }


    @Test fun restQuestionListsOnlyHardAndVeryHardSetsFromLatestCheckIn() {
        val older = guided(
            today.minusDays(1),
            "Squat: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440081"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: Vừa sức, hiệp 2: Khó, cần nghỉ thêm; Plank: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440082"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào cần nghỉ thêm trong buổi gần nhất?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat — hiệp 2: Khó, cần nghỉ thêm"))
        assertTrue(answer.contains("Plank — hiệp 1: Quá sức, tạm dừng"))
        assertFalse(answer.contains("Squat — hiệp 1: Quá sức"))
        assertTrue(answer.contains("cảm nhận tự khai báo"))
    }

    @Test fun restQuestionForNamedMoveUsesLatestSessionVariantBeforeOlderName() {
        val older = guided(
            today.minusDays(1),
            "Squat: hiệp 1: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440083"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm; Plank: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440084"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào cần nghỉ thêm của Squat hôm nay?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ — hiệp 1: Khó, cần nghỉ thêm"))
        assertFalse(answer.contains("Quá sức"))
        assertFalse(answer.contains("Plank"))
    }

    @Test fun restQuestionWithNoHardCheckInDoesNotClaimTheSessionWasSafe() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Nhẹ, còn dư sức; Plank: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440085"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Hiệp nào cần nghỉ thêm?",
            listOf(latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa có hiệp nào"))
        assertTrue(answer.contains("không chứng minh buổi tập nhẹ hay an toàn"))
        assertFalse(answer.contains("• Squat — hiệp 1:"))
    }


    @Test fun latestSummaryShowsOnlyTheNewestStructuredCheckInsAndNeverPrivateNotes() {
        val older = guided(
            today.minusDays(2),
            "Squat: hiệp 1: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440061"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: Vừa sức; Plank: hiệp 1: Nhẹ, còn dư sức",
            "550e8400-e29b-41d4-a716-446655440062",
            "private latest health note"
        )

        val summary = WorkoutEffortHistoryConversation.latestSummary(listOf(older, latest), today)

        assertNotNull(summary)
        assertTrue(summary!!.contains("Check-in cảm nhận gần nhất"))
        assertTrue(summary.contains("Vừa sức"))
        assertTrue(summary.contains("Nhẹ, còn dư sức"))
        assertFalse(summary.contains("Khó, cần nghỉ thêm"))
        assertFalse(summary.contains("private"))
    }

    @Test fun latestSummaryIsAbsentWhenOnlyLegacyOrInvalidCheckInsExist() {
        val legacy = Workout(today.toString(), "Buổi cũ", 20, "private legacy note")
        val invalid = guided(
            today,
            "Squat: hiệp 1: Vừa sức",
            "not-a-uuid"
        )

        assertNull(WorkoutEffortHistoryConversation.latestSummary(listOf(legacy, invalid), today))
    }

    @Test fun nextSessionAdvicePrioritizesLatestVeryHardAndNeverReadsPrivateNotes() {
        val older = guided(
            today.minusDays(1),
            "Squat: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440091"
        )
        val latest = guided(
            today,
            "Plank: hiệp 1: Quá sức, tạm dừng; Squat: hiệp 1: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440092",
            "private shoulder and breathing note"
        )

        val answer = WorkoutEffortHistoryConversation.answer(
            "Buổi sau tao nên tập thế nào?",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("bạn tự đánh dấu quá sức"))
        assertTrue(answer.contains("Plank — hiệp 1"))
        assertTrue(answer.contains("không nên tăng tải"))
        assertTrue(answer.contains("không phải phép đo hồi phục"))
        assertFalse(answer.contains("private"))
        assertFalse(answer.contains("shoulder"))
    }

    @Test fun nextSessionAdviceFlagsRepeatedHardEffortAcrossDistinctSessions() {
        val older = guided(
            today.minusDays(2),
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440093"
        )
        val previous = guided(
            today.minusDays(1),
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm",
            "550e8400-e29b-41d4-a716-446655440094"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440095"
        )

        val answer = WorkoutEffortHistoryConversation.answer(
            "Buổi sau nên tập thế nào?",
            listOf(older, previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("2/3 buổi"))
        assertTrue(answer.contains("giữ hoặc giảm tải"))
        assertFalse(answer.contains("tự động tăng"))
    }

    @Test fun easyCheckInsDoNotBecomeAnAutomaticProgressionRecommendation() {
        val latest = guided(
            today,
            "Squat: hiệp 1: Nhẹ, còn dư sức; Plank: hiệp 1: Vừa sức",
            "550e8400-e29b-41d4-a716-446655440096"
        )

        val answer = WorkoutEffortHistoryConversation.answer(
            "Lần sau nên tập thế nào?",
            listOf(latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa đủ căn cứ để đề xuất tăng tải"))
        assertTrue(answer.contains("không chứng minh"))
        assertFalse(answer.contains("private"))
    }

}
