package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetTrendConversationTest {
    private val today = LocalDate.of(2026, 11, 23)

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

    @Test fun historyRouterAnswersThreeSessionTrendBeforeTheSingleExerciseProgressionGate() {
        val oldest = guided(
            today.minusDays(4),
            "Squat không tạ: hiệp 1: 8 lần, hiệp 2: 8 lần",
            "550e8400-e29b-41d4-a716-446655440071"
        )
        val middle = guided(
            today.minusDays(2),
            "Squat không tạ: hiệp 1: 9 lần, hiệp 2: 8 lần",
            "550e8400-e29b-41d4-a716-446655440072"
        )
        val latest = guided(
            today,
            "Squat không tạ: hiệp 1: 10 lần, hiệp 2: 9 lần",
            "550e8400-e29b-41d4-a716-446655440073"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tao muốn xem xu hướng tăng số lần của Squat không tạ qua 3 buổi gần nhất",
            listOf(latest, oldest, middle),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Xu hướng số liệu tự khai báo"))
        assertTrue(answer.contains("16 lần → 17 lần → 19 lần"))
        assertTrue(answer.contains("tăng đều"))
        assertTrue(answer.contains("19/11/2026"))
        assertTrue(answer.contains("21/11/2026"))
        assertTrue(answer.contains("23/11/2026"))
        assertTrue(answer.contains("không tự tăng mục tiêu"))
        assertFalse(answer.contains("private"))
    }

    @Test fun mixedUnitsAreReportedSeparatelyAndNeverAddedTogether() {
        val workouts = listOf(
            guided(today.minusDays(4), "Plank: hiệp 1: 20 giây, hiệp 2: 8 lần",
                "550e8400-e29b-41d4-a716-446655440074"),
            guided(today.minusDays(2), "Plank: hiệp 1: 25 giây, hiệp 2: 9 lần",
                "550e8400-e29b-41d4-a716-446655440075"),
            guided(today, "Plank: hiệp 1: 30 giây, hiệp 2: 10 lần",
                "550e8400-e29b-41d4-a716-446655440076")
        )

        val answer = WorkoutSetTrendConversation.answer(
            "Xu hướng Plank qua 3 buổi gần nhất",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("20 giây → 25 giây → 30 giây"))
        assertTrue(answer.contains("8 lần → 9 lần → 10 lần"))
        assertFalse(answer.contains("28"))
        assertFalse(answer.contains("44"))
    }

    @Test fun onlySetIndexesPresentInAllThreeSessionsAreCompared() {
        val workouts = listOf(
            guided(today.minusDays(4), "Squat không tạ: hiệp 1: 8 lần, hiệp 2: 10 lần",
                "550e8400-e29b-41d4-a716-446655440077"),
            guided(today.minusDays(2), "Squat không tạ: hiệp 1: 9 lần, hiệp 3: 11 lần",
                "550e8400-e29b-41d4-a716-446655440078"),
            guided(today, "Squat không tạ: hiệp 1: 10 lần, hiệp 2: 12 lần",
                "550e8400-e29b-41d4-a716-446655440079")
        )

        val answer = WorkoutSetTrendConversation.answer(
            "Xu hướng Squat không tạ qua 3 buổi gần nhất",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("so sánh hiệp 1"))
        assertTrue(answer.contains("8 lần → 9 lần → 10 lần"))
        assertFalse(answer.contains("10 lần → 11 lần → 12 lần"))
    }

    @Test fun twoSessionsAreNotPresentedAsAThreeSessionTrend() {
        val workouts = listOf(
            guided(today.minusDays(1), "Squat không tạ: hiệp 1: 8 lần",
                "550e8400-e29b-41d4-a716-446655440080"),
            guided(today, "Squat không tạ: hiệp 1: 10 lần",
                "550e8400-e29b-41d4-a716-446655440081")
        )

        val answer = WorkoutSetTrendConversation.answer(
            "Xu hướng Squat không tạ qua 3 buổi gần nhất",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Mới tìm thấy 2 buổi"))
        assertFalse(answer.contains("tăng đều"))
    }

    @Test fun similarExerciseNamesRequireTheUserToDisambiguate() {
        val workouts = listOf(
            guided(today.minusDays(4),
                "Chống đẩy tường: hiệp 1: 8 lần; Chống đẩy gối: hiệp 1: 6 lần",
                "550e8400-e29b-41d4-a716-446655440082"),
            guided(today.minusDays(2),
                "Chống đẩy tường: hiệp 1: 9 lần; Chống đẩy gối: hiệp 1: 7 lần",
                "550e8400-e29b-41d4-a716-446655440083"),
            guided(today,
                "Chống đẩy tường: hiệp 1: 10 lần; Chống đẩy gối: hiệp 1: 8 lần",
                "550e8400-e29b-41d4-a716-446655440084")
        )

        val answer = WorkoutSetTrendConversation.answer(
            "Xu hướng chống đẩy qua 3 buổi gần nhất",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertTrue(answer.contains("Chống đẩy tường"))
        assertTrue(answer.contains("Chống đẩy gối"))
    }

    @Test fun mismatchedUnitsAreExcludedAndPrivateNotesNeverBecomeEvidence() {
        val workouts = listOf(
            guided(today.minusDays(4), "Plank: hiệp 1: 20 giây, hiệp 2: 8 lần",
                "550e8400-e29b-41d4-a716-446655440085", "SECRET_OLDER_NOTE"),
            guided(today.minusDays(2), "Plank: hiệp 1: 8 lần, hiệp 2: 9 lần",
                "550e8400-e29b-41d4-a716-446655440086", "SECRET_MIDDLE_NOTE"),
            guided(today, "Plank: hiệp 1: 30 giây, hiệp 2: 10 lần",
                "550e8400-e29b-41d4-a716-446655440087", "SECRET_LATEST_NOTE")
        )

        val answer = WorkoutSetTrendConversation.answer(
            "Xu hướng Plank qua 3 buổi gần nhất",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("8 lần → 9 lần → 10 lần"))
        assertTrue(answer.contains("đơn vị không nhất quán"))
        assertFalse(answer.contains("20 giây"))
        assertFalse(answer.contains("30 giây"))
        assertFalse(answer.contains("SECRET_"))
    }
}
