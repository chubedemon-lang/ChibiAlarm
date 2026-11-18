package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutSetProgressRankingTest {
    private val today = LocalDate.of(2026, 11, 19)

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
        completedMoves = 3,
        selfReportedSets = 6,
        setLog = setLog
    )

    @Test
    fun ranksTheLargestLoggedRepIncreaseAndExcludesTimedExercises() {
        val previous = guided(
            today.minusDays(3),
            "Squat: hiệp 1: 8 lần, hiệp 2: 8 lần; Chống đẩy tường: hiệp 1: 8 lần, hiệp 2: 8 lần; Plank: hiệp 1: 20 giây",
            "550e8400-e29b-41d4-a716-446655440081",
            "private previous note"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: 10 lần, hiệp 2: 10 lần; Chống đẩy tường: hiệp 1: 9 lần, hiệp 2: 8 lần; Plank: hiệp 1: 30 giây",
            "550e8400-e29b-41d4-a716-446655440082",
            "private latest note"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào tăng số lần nhiều nhất giữa hai buổi gần nhất?",
            listOf(previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat: 20 so với 16 lần (+4 lần qua 2 hiệp"))
        assertFalse(answer.contains("Chống đẩy tường"))
        assertFalse(answer.contains("Plank"))
        assertFalse(answer.contains("private"))
        assertTrue(answer.contains("tự khai báo"))
    }

    @Test
    fun reportsAllExercisesTiedForTheLargestRepIncrease() {
        val previous = guided(
            today.minusDays(2),
            "Squat: hiệp 1: 8 lần; Chống đẩy tường: hiệp 1: 6 lần",
            "550e8400-e29b-41d4-a716-446655440083"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: 10 lần; Chống đẩy tường: hiệp 1: 8 lần",
            "550e8400-e29b-41d4-a716-446655440084"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Động tác nào tiến bộ nhất?",
            listOf(previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat: 10 so với 8 lần (+2 lần"))
        assertTrue(answer.contains("Chống đẩy tường: 8 so với 6 lần (+2 lần"))
        assertFalse(answer.contains("private"))
    }

    @Test
    fun refusesToCompareWhenSetIndexesDifferOrValuesAreTimed() {
        val previous = guided(
            today.minusDays(2),
            "Squat: hiệp 1: 8 lần, hiệp 2: 8 lần; Plank: hiệp 1: 20 giây",
            "550e8400-e29b-41d4-a716-446655440085"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: 12 lần; Plank: hiệp 1: 30 giây",
            "550e8400-e29b-41d4-a716-446655440086"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào tăng số lần nhiều nhất giữa hai buổi gần nhất?",
            listOf(previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa đủ hai buổi"))
        assertFalse(answer.contains("Squat: 12"))
        assertFalse(answer.contains("Plank"))
    }

    @Test
    fun reportsNoLoggedIncreaseInsteadOfCallingADecreaseProgress() {
        val previous = guided(
            today.minusDays(2),
            "Squat: hiệp 1: 12 lần; Chống đẩy tường: hiệp 1: 8 lần",
            "550e8400-e29b-41d4-a716-446655440087"
        )
        val latest = guided(
            today,
            "Squat: hiệp 1: 10 lần; Chống đẩy tường: hiệp 1: 8 lần",
            "550e8400-e29b-41d4-a716-446655440088"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào tăng số lần nhiều nhất?",
            listOf(previous, latest),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa ghi nhận động tác nào tăng tổng số lần"))
        assertFalse(answer.contains("+"))
    }

    @Test
    fun unrelatedWorkoutQuestionIsNotCapturedAsARepProgressQuery() {
        assertNull(
            WorkoutSetHistoryConversation.answer(
                "Hôm nay tao muốn tập giãn cơ 10 phút",
                emptyList(),
                today
            )
        )
    }
}
