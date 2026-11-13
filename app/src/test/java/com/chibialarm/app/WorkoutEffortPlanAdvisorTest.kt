package com.chibialarm.app

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortPlanAdvisorTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun workout(
        date: LocalDate,
        effortLog: String,
        sessionId: String = UUID.randomUUID().toString(),
        minutes: Int = 20,
        note: String = "private health note"
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = minutes,
        note = note,
        sessionId = sessionId,
        completedMoves = 2,
        selfReportedSets = 2,
        effortLog = effortLog
    )

    @Test fun repeatedHardCheckInsOfferOnlyAnOptInLighterSessionPrompt() {
        val suggestion = WorkoutEffortPlanAdvisor.suggest(
            listOf(
                workout(today.minusDays(2), "Squat: hiệp 1: Khó, cần nghỉ thêm"),
                workout(today.minusDays(1), "Squat: hiệp 1: Quá sức, tạm dừng")
            ),
            today
        )

        assertNotNull(suggestion)
        assertEquals("Squat", suggestion!!.exerciseName)
        assertEquals(2, suggestion.repeatedSessions)
        assertEquals(1, suggestion.hardSets)
        assertEquals(1, suggestion.veryHardSets)
        assertTrue(suggestion.message.contains("tự đánh dấu"))
        assertTrue(suggestion.prompt.contains("buổi tập nhẹ hơn"))
        assertTrue(suggestion.prompt.contains("không tự tăng số hiệp"))
        assertFalse(suggestion.message.contains("private health note"))
    }

    @Test fun multipleHardSetsInOnlyOneSessionDoNotTriggerARepeatedPattern() {
        val suggestion = WorkoutEffortPlanAdvisor.suggest(
            listOf(
                workout(
                    today,
                    "Squat: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Quá sức, tạm dừng"
                )
            ),
            today
        )
        assertNull(suggestion)
    }

    @Test fun repeatedVeryHardExerciseRanksAheadOfMoreFrequentHardOnlyExercise() {
        val workouts = listOf(
            workout(today.minusDays(3), "Squat: hiệp 1: Khó, cần nghỉ thêm"),
            workout(today.minusDays(2), "Squat: hiệp 1: Khó, cần nghỉ thêm; Burpee: hiệp 1: Quá sức, tạm dừng"),
            workout(today.minusDays(1), "Squat: hiệp 1: Khó, cần nghỉ thêm; Burpee: hiệp 1: Quá sức, tạm dừng")
        )

        val suggestion = WorkoutEffortPlanAdvisor.suggest(workouts, today)
        assertNotNull(suggestion)
        assertEquals("Burpee", suggestion!!.exerciseName)
        assertEquals(2, suggestion.veryHardSets)
    }

    @Test fun duplicateSessionIdsCannotManufactureARepeatedPattern() {
        val id = UUID.randomUUID().toString()
        val workouts = listOf(
            workout(today.minusDays(1), "Squat: hiệp 1: Khó, cần nghỉ thêm", sessionId = id),
            workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", sessionId = id)
        )
        assertNull(WorkoutEffortPlanAdvisor.suggest(workouts, today))
    }

    @Test fun malformedManualFutureOldAndOutOfRangeRecordsAreIgnored() {
        val valid = workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm")
        val ignored = listOf(
            workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", sessionId = ""),
            workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", sessionId = "not-a-uuid"),
            workout(today.plusDays(1), "Squat: hiệp 1: Khó, cần nghỉ thêm"),
            workout(today.minusDays(31), "Squat: hiệp 1: Khó, cần nghỉ thêm"),
            workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", minutes = 601)
        )

        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(valid) + ignored, today))
    }

    @Test fun malformedEffortTextAndPrivateNotesAreNotUsedAsEvidence() {
        val workouts = listOf(
            workout(today.minusDays(1), "private health note says this was hard"),
            workout(today, "Squat: hiệp 1: unknown effort")
        )
        assertNull(WorkoutEffortPlanAdvisor.suggest(workouts, today))
    }
}
