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
        note: String = "private health note",
        completedMoves: Int = 2,
        selfReportedSets: Int = 2
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = minutes,
        note = note,
        sessionId = sessionId,
        completedMoves = completedMoves,
        selfReportedSets = selfReportedSets,
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
        assertTrue(suggestion.prompt.contains("động tác: Squat"))
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

    @Test fun effortCheckInsCannotExceedTheNumberOfConfirmedSets() {
        val overReported = workout(
            today.minusDays(1),
            "Squat: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Quá sức, tạm dừng",
            selfReportedSets = 1
        )
        val valid = workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", selfReportedSets = 1)

        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(overReported, valid), today))
    }

    @Test fun effortLogCannotNameMoreMovesThanTheSessionCompleted() {
        val impossible = workout(
            today.minusDays(1),
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Burpee: hiệp 1: Quá sức, tạm dừng",
            completedMoves = 1,
            selfReportedSets = 2
        )
        val valid = workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm", selfReportedSets = 1)

        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(impossible, valid), today))
    }

    @Test fun partialEffortCheckInsRemainValidForOtherConfirmedButUnratedSets() {
        val suggestion = WorkoutEffortPlanAdvisor.suggest(
            listOf(
                workout(today.minusDays(1), "Squat: hiệp 1: Khó, cần nghỉ thêm", selfReportedSets = 3),
                workout(today, "Squat: hiệp 2: Quá sức, tạm dừng", selfReportedSets = 3)
            ),
            today
        )

        assertNotNull(suggestion)
        assertEquals(2, suggestion!!.repeatedSessions)
        assertEquals(1, suggestion.hardSets)
        assertEquals(1, suggestion.veryHardSets)
    }

    @Test fun duplicateMoveSegmentsAndDuplicateSetIndexesAreRejected() {
        val duplicateMoves = workout(
            today.minusDays(1),
            "Squat: hiệp 1: Khó, cần nghỉ thêm; Squat: hiệp 2: Quá sức, tạm dừng"
        )
        val duplicateSet = workout(
            today.minusDays(1),
            "Squat: hiệp 1: Khó, cần nghỉ thêm, hiệp 1: Quá sức, tạm dừng"
        )
        val valid = workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm")

        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(duplicateMoves, valid), today))
        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(duplicateSet, valid), today))
    }

    @Test fun trailingUnparsedEffortTextCannotBecomeEvidence() {
        val malformed = workout(
            today.minusDays(1),
            "Squat: hiệp 1: Khó, cần nghỉ thêm; unexpected"
        )
        val valid = workout(today, "Squat: hiệp 1: Khó, cần nghỉ thêm")

        assertNull(WorkoutEffortPlanAdvisor.suggest(listOf(malformed, valid), today))
    }

    @Test fun malformedEffortTextAndPrivateNotesAreNotUsedAsEvidence() {
        val workouts = listOf(
            workout(today.minusDays(1), "private health note says this was hard"),
            workout(today, "Squat: hiệp 1: unknown effort")
        )
        assertNull(WorkoutEffortPlanAdvisor.suggest(workouts, today))
    }
}
