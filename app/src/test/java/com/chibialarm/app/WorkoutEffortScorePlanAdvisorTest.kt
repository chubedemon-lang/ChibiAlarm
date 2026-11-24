package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortScorePlanAdvisorTest {
    private val today = LocalDate.of(2026, 11, 24)

    private fun guided(
        daysAgo: Long,
        scoreLog: String,
        id: String,
        selfReportedSets: Int = 2,
        note: String = "private health note",
        completedMoves: Int = 2,
        minutes: Int = 20
    ) = Workout(
        date = today.minusDays(daysAgo).toString(),
        name = "Guided Workout",
        minutes = minutes,
        note = note,
        sessionId = id,
        completedMoves = completedMoves,
        selfReportedSets = selfReportedSets,
        effortScoreLog = scoreLog
    )

    @Test fun twoMostRecentHighScoreSessionsOfferOnlyAnOptInLighterSessionPrompt() {
        val previous = guided(1, "0:7.8", "550e8400-e29b-41d4-a716-446655440101")
        val latest = guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440102")

        val suggestion = WorkoutEffortScorePlanAdvisor.suggest(listOf(previous, latest), today)

        assertNotNull(suggestion)
        assertEquals(8.5, suggestion!!.latestAverage, 0.001)
        assertEquals(7.5, suggestion.previousAverage, 0.001)
        assertEquals(2, suggestion.latestRatedSets)
        assertEquals(2, suggestion.previousRatedSets)
        assertTrue(suggestion.message.contains("8,5/10"))
        assertTrue(suggestion.message.contains("7,5/10"))
        assertTrue(suggestion.message.contains("không tự thay đổi giáo án"))
        assertTrue(suggestion.prompt.contains("giữ nguyên hiệp đã hoàn thành"))
        assertTrue(suggestion.prompt.contains("không tự tăng"))
        assertFalse(suggestion.message.contains("private health note"))
    }

    @Test fun exactSevenPointFiveAverageMeetsTheDocumentedThreshold() {
        val previous = guided(1, "0:7.8", "550e8400-e29b-41d4-a716-446655440103")
        val latest = guided(0, "0:7.8", "550e8400-e29b-41d4-a716-446655440104")

        val suggestion = WorkoutEffortScorePlanAdvisor.suggest(listOf(previous, latest), today)

        assertNotNull(suggestion)
        assertEquals(7.5, suggestion!!.latestAverage, 0.001)
    }

    @Test fun aLowerLatestSessionBreaksTheRepeatedHighScorePattern() {
        val previous = guided(1, "0:8.9", "550e8400-e29b-41d4-a716-446655440105")
        val latest = guided(0, "0:5.6", "550e8400-e29b-41d4-a716-446655440106")

        assertNull(WorkoutEffortScorePlanAdvisor.suggest(listOf(previous, latest), today))
    }

    @Test fun oneHighSessionOrOnlyOneRatedSetPerSessionIsNotEnough() {
        val high = guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440107")
        assertNull(WorkoutEffortScorePlanAdvisor.suggest(listOf(high), today))

        val oneScorePrevious = guided(1, "0:8", "550e8400-e29b-41d4-a716-446655440108", selfReportedSets = 1)
        val oneScoreLatest = guided(0, "0:9", "550e8400-e29b-41d4-a716-446655440109", selfReportedSets = 1)
        assertNull(WorkoutEffortScorePlanAdvisor.suggest(listOf(oneScorePrevious, oneScoreLatest), today))
    }

    @Test fun duplicateSessionIdsCannotManufactureTwoIndependentSessions() {
        val id = "550e8400-e29b-41d4-a716-446655440110"
        val older = guided(1, "0:7.8", id)
        val duplicate = guided(0, "0:9.10", id)

        assertNull(WorkoutEffortScorePlanAdvisor.suggest(listOf(older, duplicate), today))
    }

    @Test fun malformedFutureOldAndOutOfRangeSessionsAreDiscarded() {
        val invalid = listOf(
            guided(-1, "0:9.10", "550e8400-e29b-41d4-a716-446655440111"),
            guided(8, "0:9.10", "550e8400-e29b-41d4-a716-446655440112"),
            guided(0, "0:8.11", "550e8400-e29b-41d4-a716-446655440113"),
            guided(0, "0:8.bad", "550e8400-e29b-41d4-a716-446655440114"),
            guided(0, "0:8.9,0:8.9", "550e8400-e29b-41d4-a716-446655440115"),
            guided(0, "0:8.9", "not-a-uuid"),
            guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440116", selfReportedSets = 1)
        )
        val previous = guided(1, "0:7.8", "550e8400-e29b-41d4-a716-446655440117")
        val latest = guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440118")

        val suggestion = WorkoutEffortScorePlanAdvisor.suggest(invalid + listOf(previous, latest), today)

        assertNotNull(suggestion)
        assertEquals(8.5, suggestion!!.latestAverage, 0.001)
    }

    @Test fun categoricalLegacyEffortAndPrivateNotesAreNeverUsedAsNumericEvidence() {
        val legacy = guided(1, "", "550e8400-e29b-41d4-a716-446655440119")
            .copy(effortLog = "Squat: hiệp 1: Quá sức, tạm dừng")
        val latest = guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440120")

        assertNull(WorkoutEffortScorePlanAdvisor.suggest(listOf(legacy, latest), today))
    }

    @Test fun conversationRequiresAnExplicitNumericScoreBasedLighterPlanRequest() {
        val previous = guided(1, "0:7.8", "550e8400-e29b-41d4-a716-446655440121")
        val latest = guided(0, "0:8.9", "550e8400-e29b-41d4-a716-446655440122")
        val workouts = listOf(previous, latest)

        val answer = WorkoutEffortScorePlanAdvisor.answer(
            "Dựa trên điểm gắng sức, gợi ý buổi tập nhẹ hơn",
            workouts,
            today
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("bản nháp"))
        assertTrue(answer.contains("xem trước và xác nhận"))
        assertNull(WorkoutEffortScorePlanAdvisor.answer("Tao muốn tập nhẹ hơn", workouts, today))

        val insufficient = WorkoutEffortScorePlanAdvisor.answer(
            "Dựa trên RPE, gợi ý giảm tải",
            listOf(latest),
            today
        )
        assertNotNull(insufficient)
        assertTrue(insufficient!!.contains("Chưa đủ dữ liệu"))
    }
}
