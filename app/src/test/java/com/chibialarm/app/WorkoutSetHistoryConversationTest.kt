package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutSetHistoryConversationTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun guided(
        date: LocalDate,
        setLog: String,
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
        setLog = setLog
    )

    @Test fun comparesLatestTwoSessionsForTheNamedExerciseAndNeverReadsNotes() {
        val older = guided(
            today.minusDays(3),
            "Squat không tạ: hiệp 1: 8 lần, hiệp 2: 8 lần",
            "550e8400-e29b-41d4-a716-446655440021",
            "private older knee note"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: 10 lần, hiệp 2: 9 lần",
            "550e8400-e29b-41d4-a716-446655440022",
            "private current health note"
        )

        val answer = WorkoutHistoryConversation.answer(
            "So sánh squat giữa hai buổi gần nhất",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Đối chiếu Squat không tạ"))
        assertTrue(answer.contains("Hiệp 1: 10 lần so với 8 lần (+2 lần)"))
        assertTrue(answer.contains("Hiệp 2: 9 lần so với 8 lần (+1 lần)"))
        assertTrue(answer.contains("12/11/2026"))
        assertTrue(answer.contains("09/11/2026"))
        assertTrue(answer.contains("tự khai báo"))
        assertFalse(answer.contains("private"))
        assertFalse(answer.contains("knee note"))
    }

    @Test fun recognizesNaturalVietnameseQuestionWithoutDiacritics() {
        val workout = guided(
            today,
            "Squat không tạ: hiệp 1: 9 lần",
            "550e8400-e29b-41d4-a716-446655440023"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "Squat lan truoc tao lam bao nhieu lan?",
            listOf(workout),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Hiệp 1: 9 lần"))
        assertTrue(answer.contains("Chưa đủ hai buổi"))
    }

    @Test fun ambiguousExerciseNameAsksForTheFullNameInsteadOfGuessing() {
        val wall = guided(
            today,
            "Chống đẩy tường: hiệp 1: 8 lần",
            "550e8400-e29b-41d4-a716-446655440024"
        )
        val knee = guided(
            today.minusDays(1),
            "Chống đẩy gối: hiệp 1: 6 lần",
            "550e8400-e29b-41d4-a716-446655440025"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "So sánh chống đẩy giữa hai buổi",
            listOf(wall, knee),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertTrue(answer.contains("Chống đẩy tường"))
        assertTrue(answer.contains("Chống đẩy gối"))
    }

    @Test fun futureMalformedAndNonGuidedRecordsAreIgnored() {
        val valid = guided(
            today.minusDays(2),
            "Squat không tạ: hiệp 1: 7 lần",
            "550e8400-e29b-41d4-a716-446655440026"
        )
        val future = guided(
            today.plusDays(1),
            "Squat không tạ: hiệp 1: 99 lần",
            "550e8400-e29b-41d4-a716-446655440027"
        )
        val malformedDate = guided(
            today,
            "Squat không tạ: hiệp 1: 99 lần",
            "550e8400-e29b-41d4-a716-446655440028"
        ).copy(date = "yesterday")
        val manual = Workout(
            date = today.toString(),
            name = "Tập thủ công",
            minutes = 30,
            note = "private manual note",
            setLog = "Squat không tạ: hiệp 1: 99 lần"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "So sánh squat giữa hai buổi",
            listOf(valid, future, malformedDate, manual),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Hiệp 1: 7 lần"))
        assertTrue(answer.contains("Chưa đủ hai buổi"))
        assertFalse(answer.contains("99 lần"))
        assertFalse(answer.contains("private"))
    }

    @Test fun rejectsMalformedOrOversizedSetLogs() {
        val malformed = guided(
            today,
            "Squat không tạ: hiệp 1: 999 lần",
            "550e8400-e29b-41d4-a716-446655440029"
        )
        val oversized = guided(
            today.minusDays(1),
            "x".repeat(1_201),
            "550e8400-e29b-41d4-a716-446655440030"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "So sánh squat giữa hai buổi",
            listOf(malformed, oversized),
            today
        )

        assertTrue(answer!!.contains("Chưa có chi tiết hiệp"))
    }

    @Test fun reportsMissingSetsAndDoesNotCompareDifferentUnits() {
        val older = guided(
            today.minusDays(1),
            "Plank: hiệp 1: 20 giây, hiệp 2: 25 giây",
            "550e8400-e29b-41d4-a716-446655440031"
        )
        val latest = guided(
            today,
            "Plank: hiệp 1: 30 lần, hiệp 3: 8 lần",
            "550e8400-e29b-41d4-a716-446655440032"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "So sánh plank giữa hai buổi",
            listOf(older, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("không so sánh vì khác đơn vị"))
        assertTrue(answer.contains("Hiệp 2: 25 giây (buổi mới nhất chưa ghi)"))
        assertTrue(answer.contains("Hiệp 3: 8 lần (buổi trước chưa ghi)"))
    }

    @Test fun unrelatedPlanQuestionIsNotConsumed() {
        assertNull(
            WorkoutSetHistoryConversation.answer(
                "Hôm nay tao muốn tập squat 20 phút không dụng cụ",
                emptyList(),
                today
            )
        )
    }

    @Test fun asksForExerciseNameWhenASetComparisonHasNoMatchingExercise() {
        val workout = guided(
            today,
            "Squat không tạ: hiệp 1: 9 lần",
            "550e8400-e29b-41d4-a716-446655440033"
        )

        val answer = WorkoutSetHistoryConversation.answer(
            "So sánh số hiệp giữa hai buổi gần nhất",
            listOf(workout),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("nhắc tên động tác"))
    }
}
