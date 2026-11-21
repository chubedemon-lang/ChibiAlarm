package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetWeeklySummaryConversationTest {
    private val today = LocalDate.of(2026, 11, 21)

    private fun guided(
        date: LocalDate,
        setLog: String,
        id: String,
        selfReportedSets: Int = Regex("""hiệp \\d+\\s*:""").findAll(setLog).count()
    ) = Workout(
        date = date.toString(),
        name = "Toàn thân",
        minutes = 30,
        note = "private note must not be read",
        sessionId = id,
        completedMoves = 2,
        selfReportedSets = selfReportedSets,
        setLog = setLog
    )

    @Test fun namedExerciseTotalsOnlyTheLatestSevenCalendarDays() {
        val history = listOf(
            guided(today.minusDays(2), "Squat không tạ: hiệp 1: 9 lần, hiệp 2: 7 lần",
                "550e8400-e29b-41d4-a716-446655440051"),
            guided(today.minusDays(8), "Squat không tạ: hiệp 1: 100 lần",
                "550e8400-e29b-41d4-a716-446655440052"),
            guided(today, "Squat không tạ: hiệp 1: 10 lần, hiệp 2: 8 lần",
                "550e8400-e29b-41d4-a716-446655440053"),
            guided(today.plusDays(1), "Squat không tạ: hiệp 1: 90 lần",
                "550e8400-e29b-41d4-a716-446655440054"),
            Workout(today.toString(), "Tập thủ công", 30, "private",
                setLog = "Squat không tạ: hiệp 1: 50 lần")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tao làm tổng bao nhiêu lần Squat không tạ?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("34 lần (4 hiệp, 2 buổi)"))
        assertFalse(answer.contains("100 lần"))
        assertFalse(answer.contains("90 lần"))
        assertFalse(answer.contains("50 lần"))
        assertFalse(answer.contains("private"))
    }

    @Test fun mixedUnitsAreReportedSeparatelyAndNeverConverted() {
        val history = listOf(
            guided(today.minusDays(1), "Plank: hiệp 1: 20 giây",
                "550e8400-e29b-41d4-a716-446655440055"),
            guided(today, "Plank: hiệp 1: 30 lần",
                "550e8400-e29b-41d4-a716-446655440056")
        )

        val answer = WorkoutHistoryConversation.answer(
            "7 ngày gần nhất tổng số lần Plank là bao nhiêu?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("30 lần (1 hiệp, 1 buổi)"))
        assertTrue(answer.contains("20 giây (1 hiệp, 1 buổi)"))
        assertFalse(answer.contains("50 lần"))
        assertTrue(answer.contains("không quy đổi"))
    }

    @Test fun unreportedConfirmedSetsAreNotFilledFromThePlanTarget() {
        val history = listOf(
            guided(today, "Squat không tạ: hiệp 1: 10 lần",
                "550e8400-e29b-41d4-a716-446655440057", selfReportedSets = 2)
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tổng số lần Squat không tạ?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("10 lần (1 hiệp, 1 buổi)"))
        assertTrue(answer.contains("hiệp chưa ghi số lần không được ước tính"))
        assertFalse(answer.contains("20 lần"))
    }

    @Test fun genericWeeklyQuestionListsExercisesSeparatelyInsteadOfCombiningDifferentMoves() {
        val history = listOf(
            guided(today, "Squat không tạ: hiệp 1: 10 lần; Chống đẩy tường: hiệp 1: 8 lần",
                "550e8400-e29b-41d4-a716-446655440058")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tổng số lần đã ghi là bao nhiêu?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ: 10 lần"))
        assertTrue(answer.contains("Chống đẩy tường: 8 lần"))
        assertFalse(answer.contains("18 lần"))
    }

    @Test fun ambiguousExerciseNamesRequireTheFullCatalogName() {
        val history = listOf(
            guided(today, "Chống đẩy tường: hiệp 1: 8 lần",
                "550e8400-e29b-41d4-a716-446655440059"),
            guided(today.minusDays(1), "Chống đẩy gối: hiệp 1: 6 lần",
                "550e8400-e29b-41d4-a716-446655440060")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này chống đẩy tổng số lần là bao nhiêu?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertTrue(answer.contains("Chống đẩy tường"))
        assertTrue(answer.contains("Chống đẩy gối"))
    }

    @Test fun noRecentSetValuesDoesNotFallBackToOlderSessions() {
        val history = listOf(
            guided(today.minusDays(8), "Squat không tạ: hiệp 1: 10 lần",
                "550e8400-e29b-41d4-a716-446655440061")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tao làm tổng bao nhiêu lần Squat không tạ?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("7 ngày gần nhất"))
        assertFalse(answer.contains("10 lần"))
    }
}
