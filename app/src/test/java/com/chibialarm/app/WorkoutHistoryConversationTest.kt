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

    private fun guided(
        date: LocalDate,
        effortLog: String,
        id: String,
        note: String = "private health note"
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = 30,
        note = note,
        sessionId = id,
        completedMoves = 2,
        selfReportedSets = 2,
        effortLog = effortLog
    )

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

    @Test fun comparesRecentSevenDaysWithThePreviousSevenDaysWithoutReadingNotes() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần này so với tuần trước thế nào?",
            listOf(
                workout(today, 30, "Toàn thân", "private current note"),
                workout(today.minusDays(1), 20, "Giãn cơ", "private second note"),
                workout(today.minusDays(7), 15, "Đi bộ", "private previous note"),
                workout(today.minusDays(9), 30, "Sức mạnh", "private older note")
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Buổi tập: 2 so với 2 (không đổi)"))
        assertTrue(answer.contains("\\n• Buổi tập:").not())
        assertTrue(answer.contains("\n• Buổi tập:"))
        assertTrue(answer.contains("Ngày có vận động: 2 so với 2 (không đổi)"))
        assertTrue(answer.contains("Thời lượng: 50 so với 45 phút (+5 phút)"))
        assertTrue(answer.contains("không đo cường độ"))
        assertFalse(answer.contains("private"))
    }

    @Test fun comparisonReportsDecreasesWithoutCallingThemFailure() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tao tập ít hơn tuần trước không?",
            listOf(
                workout(today, 30, "Đi bộ"),
                workout(today.minusDays(7), 20, "Sức mạnh"),
                workout(today.minusDays(9), 25, "Giãn cơ")
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Buổi tập: 1 so với 2 (-1 buổi)"))
        assertTrue(answer.contains("Thời lượng: 30 so với 45 phút (-15 phút)"))
        assertFalse(answer.contains("thất bại"))
    }

    @Test fun comparisonWithNoValidEntriesExplainsTheLocalJournalLimit() {
        val answer = WorkoutHistoryConversation.answer(
            "So sánh tuần này với tuần trước",
            emptyList(),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa có buổi tập hợp lệ"))
        assertTrue(answer.contains("trên thiết bị"))
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

    @Test fun comparisonQuestionRecognizesNaturalSoVoiPhrasing() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần này so với tuần trước thế nào?",
            listOf(
                workout(today, 30),
                workout(today.minusDays(7), 20)
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("So sánh 7 ngày gần nhất"))
        assertTrue(answer.contains("Buổi tập: 1 so với 1"))
    }

    @Test fun nextWeekPlanUsesRecentFrequencyWithoutAskingUserToCatchUp() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần này tao tập ít hơn tuần trước, tuần tới điều chỉnh lịch thế nào?",
            listOf(
                workout(today, 25),
                workout(today.minusDays(2), 20),
                workout(today.minusDays(7), 30),
                workout(today.minusDays(9), 25),
                workout(today.minusDays(11), 20)
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Gợi ý lịch tuần tới"))
        assertTrue(answer.contains("2 ngày vận động, xen kẽ ngày nghỉ"))
        assertTrue(answer.contains("chưa tự thay đổi lịch"))
        assertTrue(answer.contains("Không cần tập bù"))
    }

    @Test fun nextWeekPlanWithNoCurrentWeekActivitySuggestsOnlyOneOptionalLightDay() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên tập mấy buổi?",
            listOf(
                workout(today.minusDays(8), 25),
                workout(today.minusDays(10), 20)
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("1 ngày vận động nhẹ, nếu phù hợp"))
        assertTrue(answer.contains("Không cần tập bù"))
        assertFalse(answer.contains("3 ngày vận động"))
    }

    @Test fun nextWeekPlanDoesNotInventFrequencyWithoutAnyRecentHistory() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên tập mấy buổi?",
            emptyList(),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa đủ nhật ký"))
        assertFalse(answer.contains("Mục tiêu khởi đầu"))
    }


    @Test fun nextWeekPlanShowsConcreteDatesWithoutSavingOrLeakingNotes() {
        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên sắp xếp lịch tập thế nào?",
            listOf(
                workout(today.minusDays(5), 25, "Đi bộ", "private note"),
                workout(today.minusDays(3), 30, "Toàn thân", "private note"),
                workout(today.minusDays(1), 20, "Giãn cơ", "private note")
            ),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("bản nháp, chưa tự thay đổi lịch"))
        assertTrue(answer.contains("Thứ Hai 16/11"))
        assertTrue(answer.contains("Thứ Tư 18/11"))
        assertTrue(answer.contains("Thứ Bảy 21/11"))
        assertTrue(answer.contains("tự xác nhận"))
        assertFalse(answer.contains("private note"))
    }

    @Test fun nextWeekPlanCapsStructuredDaysAtFiveWhenLastWeekHadSixActiveDays() {
        val history = (0..5).map { offset ->
            workout(today.minusDays(offset.toLong()), 20, "Đi bộ")
        }
        val answer = WorkoutHistoryConversation.answer(
            "Tuần sau cho tao kế hoạch lịch tập thế nào?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("tối đa 5 ngày tập có cấu trúc"))
        assertTrue(answer.contains("chừa ít nhất hai ngày"))
        assertTrue(answer.contains("Chủ nhật 22/11"))
    }

    @Test fun nextWeekPlanAfterAnInactiveWeekOffersOnlyOneOptionalLightDay() {
        val history = listOf(workout(today.minusDays(9), 20, "Đi bộ"))
        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên tập thế nào?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("1 ngày vận động nhẹ"))
        assertTrue(answer.contains("Thứ Tư 18/11"))
        assertTrue(answer.contains("có thể bỏ qua"))
    }


    @Test fun nextWeekPlanUsesMedianDurationInsteadOfBeingPulledByOneLongSession() {
        val history = listOf(
            workout(today, 20, "Đi bộ"),
            workout(today.minusDays(2), 30, "Toàn thân"),
            workout(today.minusDays(4), 90, "Đạp xe")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên sắp xếp lịch tập thế nào?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("trung vị các buổi gần đây là 30 phút/buổi"))
        assertTrue(answer.contains("không phải mục tiêu bắt buộc"))
        assertFalse(answer.contains("private health note"))
    }

    @Test fun nextWeekPlanUsesPreviousWeekMedianWhenCurrentWeekHasNoSessions() {
        val history = listOf(
            workout(today.minusDays(9), 25, "Đi bộ"),
            workout(today.minusDays(10), 45, "Giãn cơ")
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tuần tới tao nên sắp xếp lịch tập thế nào?",
            history,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("1 ngày vận động nhẹ"))
        assertTrue(answer.contains("trung vị các buổi gần đây là 35 phút/buổi"))
        assertTrue(answer.contains("có thể bỏ qua"))
    }


    @Test fun repeatedHardCheckInsOfferAnOptInLighterPlanPreview() {
        val workouts = listOf(
            guided(
                today.minusDays(2),
                "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm",
                "550e8400-e29b-41d4-a716-446655440081",
                "private note from older session"
            ),
            guided(
                today,
                "Squat không tạ: hiệp 1: Quá sức, tạm dừng",
                "550e8400-e29b-41d4-a716-446655440082",
                "private note from latest session"
            )
        )

        val answer = WorkoutHistoryConversation.answer(
            "Có bài nào tao nên giảm tải không?",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("2 buổi"))
        assertTrue(answer.contains("bản nháp"))
        assertTrue(answer.contains("Tao muốn xem một buổi tập nhẹ hơn"))
        assertTrue(answer.contains("chưa thay đổi giáo án"))
        assertFalse(answer.contains("private note"))
    }

    @Test fun oneHardSessionDoesNotTriggerAnEffortBasedLoadChangeSuggestion() {
        val workout = guided(
            today,
            "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Quá sức, tạm dừng",
            "550e8400-e29b-41d4-a716-446655440083",
            "private note says to reduce load"
        )

        val answer = WorkoutHistoryConversation.answer(
            "Có bài nào tao nên giảm tải không?",
            listOf(workout),
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa đủ dữ liệu"))
        assertFalse(answer.contains("Squat không tạ"))
        assertFalse(answer.contains("private note"))
    }

    @Test fun progressionEligibilityIsAnswerableThroughTheCoachConversation() {
        val workouts = listOf(
            guided(
                today.minusDays(4),
                "Squat không tạ: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức",
                "550e8400-e29b-41d4-a716-446655440091",
                "private note from first session"
            ),
            guided(
                today.minusDays(2),
                "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Nhẹ, còn dư sức",
                "550e8400-e29b-41d4-a716-446655440092",
                "private note from second session"
            ),
            guided(
                today,
                "Squat không tạ: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức",
                "550e8400-e29b-41d4-a716-446655440093",
                "private note from latest session"
            )
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào tao có thể tăng nhẹ?",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Trong 3 ngày tập riêng biệt"))
        assertTrue(answer.contains("Xem bước tiến triển có kiểm soát"))
        assertTrue(answer.contains("không tự tăng số lần"))
        assertFalse(answer.contains("private note"))
    }

    @Test fun progressionQuestionExplainsTheThreeDistinctDayEvidenceGate() {
        val workouts = listOf(
            guided(
                today.minusDays(2),
                "Squat không tạ: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức",
                "550e8400-e29b-41d4-a716-446655440094"
            ),
            guided(
                today,
                "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Nhẹ, còn dư sức",
                "550e8400-e29b-41d4-a716-446655440095"
            )
        )

        val answer = WorkoutHistoryConversation.answer(
            "Tao có đủ điều kiện tăng số lần chưa?",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa đủ dữ liệu"))
        assertTrue(answer.contains("ít nhất 3 ngày riêng biệt"))
        assertFalse(answer.contains("Squat không tạ"))
        assertFalse(answer.contains("private health note"))
    }

    @Test fun aHardCheckInBlocksTheConversationalProgressionSuggestion() {
        val workouts = listOf(
            guided(
                today.minusDays(4),
                "Squat không tạ: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức",
                "550e8400-e29b-41d4-a716-446655440096"
            ),
            guided(
                today.minusDays(2),
                "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Nhẹ, còn dư sức",
                "550e8400-e29b-41d4-a716-446655440097"
            ),
            guided(
                today,
                "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Vừa sức",
                "550e8400-e29b-41d4-a716-446655440098"
            )
        )

        val answer = WorkoutHistoryConversation.answer(
            "Bài nào tao có thể tăng nhẹ?",
            workouts,
            today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa đủ dữ liệu"))
        assertFalse(answer.contains("Xem bước tiến triển có kiểm soát"))
    }

    @Test fun restDurationQuestionIsNotMistakenForProgressionIntent() {
        assertNull(
            WorkoutHistoryConversation.answer(
                "Tao muốn tăng thời gian nghỉ 15 giây",
                emptyList(),
                today
            )
        )
    }

}
