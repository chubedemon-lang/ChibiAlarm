package com.chibialarm.app

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WorkoutSteadyProgressAdvisorTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun workout(
        date: LocalDate,
        effortLog: String,
        sessionId: String = UUID.randomUUID().toString(),
        minutes: Int = 20,
        note: String = "private health note",
        selfReportedSets: Int = Regex("""hiệp\\s+\\d+\\s*:""").findAll(effortLog).count()
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = minutes,
        note = note,
        sessionId = sessionId,
        completedMoves = 2,
        selfReportedSets = selfReportedSets,
        effortLog = effortLog
    )

    @Test fun threeRecentManageableSessionsOfferAnOptInProgressionDiscussion() {
        val suggestion = WorkoutSteadyProgressAdvisor.suggest(
            listOf(
                workout(today.minusDays(4), "Squat không tạ: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức"),
                workout(today.minusDays(2), "Squat không tạ: hiệp 1: Vừa sức, hiệp 2: Nhẹ, còn dư sức"),
                workout(today, "Squat không tạ: hiệp 1: Nhẹ, còn dư sức")
            ),
            today
        )

        assertNotNull(suggestion)
        assertEquals("Squat không tạ", suggestion!!.exerciseName)
        assertEquals(3, suggestion.consistentSessions)
        assertEquals(3, suggestion.easySets)
        assertEquals(2, suggestion.moderateSets)
        assertTrue(suggestion.message.contains("tự chọn mức nhẹ"))
        assertTrue(suggestion.prompt.contains("xem trước"))
        assertTrue(suggestion.prompt.contains("không tự áp dụng"))
        assertFalse(suggestion.message.contains("private"))
        assertFalse(suggestion.prompt.contains("private"))
    }

    @Test fun aHardOrVeryHardRatingInAnyOfTheLatestThreeSessionsBlocksProgression() {
        val workouts = listOf(
            workout(today.minusDays(4), "Squat: hiệp 1: Nhẹ, còn dư sức"),
            workout(today.minusDays(2), "Squat: hiệp 1: Vừa sức"),
            workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm")
        )
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today))

        val veryHard = workouts.dropLast(1) +
            workout(today, "Squat: hiệp 1: Quá sức, tạm dừng")
        assertNull(WorkoutSteadyProgressAdvisor.suggest(veryHard, today))
    }

    @Test fun requiresThreeDistinctSessionsForTheSameExercise() {
        val workouts = listOf(
            workout(today.minusDays(2), "Squat: hiệp 1: Nhẹ, còn dư sức"),
            workout(today, "Squat: hiệp 1: Vừa sức")
        )
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today))
    }

    @Test fun duplicateSessionIdsCannotManufactureThreeManageableSessions() {
        val id = "550e8400-e29b-41d4-a716-446655440001"
        val workouts = listOf(
            workout(today.minusDays(2), "Squat: hiệp 1: Nhẹ, còn dư sức", sessionId = id),
            workout(today.minusDays(1), "Squat: hiệp 1: Nhẹ, còn dư sức", sessionId = id),
            workout(today, "Squat: hiệp 1: Nhẹ, còn dư sức", sessionId = id)
        )
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today))
    }

    @Test fun malformedFutureOldAndOversizedRecordsAreIgnored() {
        val workouts = listOf(
            workout(today, "Squat: hiệp 1: Nhẹ, còn dư sức", sessionId = "not-a-uuid"),
            workout(today.plusDays(1), "Squat: hiệp 1: Nhẹ, còn dư sức"),
            workout(today.minusDays(31), "Squat: hiệp 1: Nhẹ, còn dư sức"),
            workout(today, "x".repeat(1_201)),
            workout(today.minusDays(2), "Squat: hiệp 1: Nhẹ, còn dư sức")
        )
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today))
    }

    @Test fun differentExercisesAreNeverCombinedIntoOnePattern() {
        val workouts = listOf(
            workout(today.minusDays(4), "Squat: hiệp 1: Nhẹ, còn dư sức"),
            workout(today.minusDays(2), "Plank: hiệp 1: Nhẹ, còn dư sức"),
            workout(today, "Chống đẩy tường: hiệp 1: Nhẹ, còn dư sức")
        )
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today))
    }

    @Test fun freeTextNotesAreNotEvidenceAndLookbackIsBounded() {
        val workouts = listOf(
            workout(today.minusDays(4), "Squat: hiệp 1: Nhẹ, còn dư sức", note = "private note"),
            workout(today.minusDays(2), "Squat: hiệp 1: Vừa sức", note = "private note"),
            workout(today, "Squat: hiệp 1: Nhẹ, còn dư sức", note = "private note")
        )
        val suggestion = WorkoutSteadyProgressAdvisor.suggest(workouts, today, lookbackDays = 5)
        assertNotNull(suggestion)
        assertFalse(suggestion!!.toString().contains("private"))
        assertNull(WorkoutSteadyProgressAdvisor.suggest(workouts, today, lookbackDays = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedLookback() {
        WorkoutSteadyProgressAdvisor.suggest(emptyList(), today, lookbackDays = 365)
    }
}
