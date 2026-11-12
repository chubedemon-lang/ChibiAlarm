package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryConversationTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun workout(
        date: LocalDate,
        minutes: Int = 30,
        name: String = "Đi bộ",
        note: String = "private health note"
    ) = Workout(date.toString(), name, minutes, note)

    @Test fun weeklyQuestionReportsSessionsActiveDaysAndMinutes() {
        val history = listOf(
            workout(today, 30, "Toàn thân"),
            workout(today, 15, "Giãn cơ"),
            workout(today.minusDays(3), 30, "Đi bộ"),
            workout(today.minusDays(7), 90, "Buổi cũ"),
            workout(today.plusDays(1), 60, "Ngày tương lai")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tao tập bao nhiêu buổi, mấy ngày và bao nhiêu phút?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("3 buổi đã ghi"))
        assertTrue(answer.contains("2 ngày"))
        assertTrue(answer.contains("75 phút"))
    }

    @Test fun todayQuestionUsesOnlyTodayEntries() {
        val answer = WorkoutHistoryConversation.answer(
            "Hôm nay tao tập bao nhiêu phút?",
            listOf(
                workout(today, 18),
                workout(today.minusDays(1), 45)
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("18 phút"))
        assertFalse(answer.contains("63 phút"))
    }

    @Test fun latestWorkoutAnswerUsesStructuredFieldsAndNeverLeaksNotes() {
        val answer = WorkoutHistoryConversation.answer(
            "Lần gần nhất tao tập khi nào?",
            listOf(
                workout(today.minusDays(3), 20, "Đi bộ"),
                workout(today.minusDays(1), 35, "Tập sức mạnh", "private knee pain note")
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Tập sức mạnh"))
        assertTrue(answer.contains("hôm qua"))
        assertTrue(answer.contains("35 phút"))
        assertFalse(answer.contains("private"))
        assertFalse(answer.contains("knee pain"))
    }

    @Test fun elapsedDaysSinceLastLoggedWorkoutAreReportedWithoutDiagnosis() {
        val answer = WorkoutHistoryConversation.answer(
            "Bao lâu rồi tao chưa tập?",
            listOf(workout(today.minusDays(4), 25)),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("4 ngày trước"))
        assertTrue(answer.contains("nhật ký tự ghi"))
        assertFalse(answer.contains("hồi phục tốt"))
    }

    @Test fun emptyHistoryReturnsHelpfulLocalJournalMessage() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tập được bao nhiêu buổi?",
            emptyList(),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Không tìm thấy buổi tập hợp lệ"))
        assertTrue(answer.contains("trên thiết bị"))
    }

    @Test fun unrelatedWorkoutPlanRequestIsNotConsumedAsHistoryQuestion() {
        assertNull(
            WorkoutHistoryConversation.answer(
                "Hôm nay tao muốn tập 20 phút không dụng cụ",
                listOf(workout(today, 20)),
                today
            )
        )
    }

    @Test fun malformedDatesAndInvalidDurationsCannotEnterHistoryAnswer() {
        val history = listOf(
            Workout("yesterday", "Đi bộ", 30, "private"),
            Workout(today.toString(), "Đi bộ", 0, "private"),
            Workout(today.toString(), "Đi bộ", 601, "private"),
            workout(today.plusDays(1), 40)
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tập mấy buổi?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Không tìm thấy buổi tập hợp lệ"))
    }

    @Test fun monthQuestionUsesCalendarMonthRatherThanSevenDays() {
        val answer = WorkoutHistoryConversation.answer(
            "Tháng này tao tập tổng bao nhiêu phút?",
            listOf(
                workout(today, 20),
                workout(today.withDayOfMonth(1).minusDays(1), 90)
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("20 phút"))
        assertFalse(answer.contains("110 phút"))
    }

    @Test fun generalPlanPhraseContainingTongTheIsNotMistakenForAHistoryQuestion() {
        assertNull(
            WorkoutHistoryConversation.answer(
                "Tập toàn thân tổng thể 20 phút không dụng cụ",
                listOf(workout(today, 20)),
                today
            )
        )
    }

    @Test fun recentListReturnsNewestEntriesFirstWithoutPrivateNotes() {
        val answer = WorkoutHistoryConversation.answer(
            "Liệt kê 3 buổi gần nhất",
            listOf(
                workout(today.minusDays(3), 25, "Đi bộ", "private older note"),
                workout(today, 30, "Tập sức mạnh", "private current note"),
                workout(today.minusDays(1), 20, "Giãn cơ", "private middle note")
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Đây là 3 buổi gần nhất"))
        assertTrue(answer.contains("Tập sức mạnh • 30 phút"))
        assertTrue(answer.contains("Giãn cơ • 20 phút"))
        assertTrue(answer.contains("Đi bộ • 25 phút"))
        assertTrue(answer.indexOf("12/11/2026") < answer.indexOf("11/11/2026"))
        assertTrue(answer.indexOf("11/11/2026") < answer.indexOf("09/11/2026"))
        assertFalse(answer.contains("private"))
        assertFalse(answer.contains("older note"))
    }

    @Test fun recentListDefaultsToFiveAndCapsRequestedCountAtTen() {
        val history = (0..11).map { offset ->
            workout(today.minusDays(offset.toLong()), 10 + offset, "Buổi " + offset)
        }

        val defaultAnswer = WorkoutHistoryConversation.answer(
            "Liệt kê các buổi tập gần đây",
            history,
            today
        )
        assertNotNull(defaultAnswer)
        assertTrue(defaultAnswer!!.contains("Đây là 5 buổi gần nhất"))
        assertFalse(defaultAnswer.contains("Buổi 5 • 15 phút"))

        val boundedAnswer = WorkoutHistoryConversation.answer(
            "Cho tao xem 99 buổi gần nhất",
            history,
            today
        )
        assertNotNull(boundedAnswer)
        assertTrue(boundedAnswer!!.contains("Đây là 10 buổi gần nhất"))
        assertFalse(boundedAnswer.contains("Buổi 11 • 21 phút"))
    }

    @Test fun recentListWithNoValidRecordsReturnsLocalJournalMessage() {
        val answer = WorkoutHistoryConversation.answer(
            "Cho xem danh sách các buổi gần đây",
            listOf(workout(today.plusDays(1), 20)),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Không tìm thấy buổi tập hợp lệ"))
        assertFalse(answer.contains("private"))
    }

    @Test fun historyAnswerIsLocaleIndependent() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            val answer = WorkoutHistoryConversation.answer(
                "TUẦN NÀY TẬP BAO NHIÊU BUỔI?",
                listOf(workout(today, 10)),
                today
            )
            assertNotNull(answer)
            assertTrue(answer!!.contains("1 buổi đã ghi"))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
