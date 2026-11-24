package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortScoreTrendConversationTest {
    private val today = LocalDate.of(2026, 11, 24)

    private fun guided(
        date: LocalDate,
        scoreLog: String,
        id: String,
        selfReportedSets: Int = 3,
        note: String = "private health note"
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = 25,
        note = note,
        sessionId = id,
        completedMoves = 2,
        selfReportedSets = selfReportedSets,
        effortScoreLog = scoreLog
    )

    @Test fun comparesSessionAveragesFromExactScoresAndIgnoresPrivateNotes() {
        val previous = guided(
            today.minusDays(1), "0:4.6,1:5.-.7", "550e8400-e29b-41d4-a716-446655440101",
            selfReportedSets = 4
        )
        val latest = guided(
            today, "0:8.9,1:7", "550e8400-e29b-41d4-a716-446655440102",
            selfReportedSets = 3
        )

        val answer = WorkoutEffortScoreTrendConversation.answer(
            "Điểm gắng sức trung bình gần đây tăng hay giảm?",
            listOf(previous, latest), today
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("24/11/2026: 8,0/10 (3 hiệp có điểm)"))
        assertTrue(answer.contains("23/11/2026: 5,5/10 (4 hiệp có điểm)"))
        assertTrue(answer.contains("cao hơn 2,5 điểm"))
        assertTrue(answer.contains("tự chấm"))
        assertFalse(answer.contains("private"))
    }

    @Test fun nullPlaceholdersAreExcludedFromTheAverage() {
        val workout = guided(
            today, "0:8.-.6,1:-.4", "550e8400-e29b-41d4-a716-446655440103",
            selfReportedSets = 3
        )
        val answer = WorkoutEffortScoreTrendConversation.answer(
            "RPE buổi gần nhất?", listOf(workout), today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("6,0/10 (3 hiệp có điểm)"))
    }

    @Test fun duplicateSessionIdsCannotCreateAFakeTrend() {
        val id = "550e8400-e29b-41d4-a716-446655440104"
        val first = guided(today.minusDays(1), "0:4.5", id, selfReportedSets = 2)
        val duplicate = guided(today, "0:9.10", id, selfReportedSets = 2)
        val answer = WorkoutEffortScoreTrendConversation.answer(
            "So sánh điểm gắng sức hai buổi gần nhất",
            listOf(first, duplicate), today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Mới có một buổi hợp lệ"))
        assertFalse(answer.contains("cao hơn"))
    }

    @Test fun malformedFutureOldAndOutOfRangeLogsAreIgnored() {
        val valid = guided(today, "0:6.7", "550e8400-e29b-41d4-a716-446655440105", selfReportedSets = 2)
        val invalid = listOf(
            guided(today, "0:0.11", "550e8400-e29b-41d4-a716-446655440106", selfReportedSets = 2),
            guided(today, "0:6,0:8", "550e8400-e29b-41d4-a716-446655440107", selfReportedSets = 2),
            guided(today, "0:6.bad", "550e8400-e29b-41d4-a716-446655440108", selfReportedSets = 2),
            guided(today.plusDays(1), "0:9", "550e8400-e29b-41d4-a716-446655440109", selfReportedSets = 1),
            guided(today.minusDays(31), "0:9", "550e8400-e29b-41d4-a716-446655440110", selfReportedSets = 1),
            guided(today, "0:9", "not-a-uuid", selfReportedSets = 1),
            guided(today, "0:-.-", "550e8400-e29b-41d4-a716-446655440111", selfReportedSets = 1)
        )
        val answer = WorkoutEffortScoreTrendConversation.answer(
            "Xu hướng điểm gắng sức gần đây?", invalid + valid, today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("24/11/2026: 6,5/10"))
        assertTrue(answer.contains("Mới có một buổi hợp lệ"))
    }

    @Test fun legacyCategoricalCheckInsAreNeverConvertedIntoNumericScores() {
        val legacy = guided(
            today, "", "550e8400-e29b-41d4-a716-446655440112",
            selfReportedSets = 1
        ).copy(effortLog = "Squat: hiệp 1: Khó, cần nghỉ thêm")
        val answer = WorkoutEffortScoreTrendConversation.answer(
            "Điểm gắng sức buổi gần nhất?", listOf(legacy), today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Chưa có điểm gắng sức số 1–10 hợp lệ"))
    }

    @Test fun unrelatedRpeDefinitionAndOrdinaryChatAreNotConsumed() {
        assertNull(WorkoutEffortScoreTrendConversation.answer("RPE là gì?", emptyList(), today))
        assertNull(WorkoutEffortScoreTrendConversation.answer("Tao hơi mệt, nghỉ thêm nhé", emptyList(), today))
    }

    @Test fun historyRouterUsesNumericTrendBeforeCategoricalEffortRoute() {
        val previous = guided(
            today.minusDays(1), "0:4.6", "550e8400-e29b-41d4-a716-446655440113",
            selfReportedSets = 2
        )
        val latest = guided(
            today, "0:8.9", "550e8400-e29b-41d4-a716-446655440114",
            selfReportedSets = 2
        )
        val answer = WorkoutHistoryConversation.answer(
            "Điểm gắng sức trung bình gần đây tăng hay giảm?",
            listOf(previous, latest), today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("8,5/10"))
        assertTrue(answer.contains("cao hơn 3,5 điểm"))
    }
}
