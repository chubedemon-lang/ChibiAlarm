package com.chibialarm.app

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WorkoutProgressionPreviewTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun workout(
        date: LocalDate,
        exercise: String = "Squat không tạ",
        reps: List<Int> = listOf(8, 8),
        unit: String = "lần",
        levels: List<WorkoutEffortFeedback.Level> = listOf(
            WorkoutEffortFeedback.Level.EASY,
            WorkoutEffortFeedback.Level.MODERATE
        ),
        selfReportedSets: Int = reps.size,
        note: String = "private health note"
    ) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = 20,
        note = note,
        sessionId = UUID.randomUUID().toString(),
        completedMoves = 2,
        selfReportedSets = selfReportedSets,
        setLog = "$exercise: " + reps.mapIndexed { index, value ->
            "hiệp ${index + 1}: $value $unit"
        }.joinToString(", "),
        effortLog = "$exercise: " + levels.mapIndexed { index, level ->
            "hiệp ${index + 1}: ${level.label}"
        }.joinToString(", ")
    )

    private fun history(
        exercise: String = "Squat không tạ",
        reps: List<Int> = listOf(8, 8),
        unit: String = "lần",
        levels: List<WorkoutEffortFeedback.Level> = listOf(
            WorkoutEffortFeedback.Level.EASY,
            WorkoutEffortFeedback.Level.MODERATE
        )
    ) = listOf(
        workout(today.minusDays(4), exercise, reps, unit, levels),
        workout(today.minusDays(2), exercise, reps, unit, levels),
        workout(today, exercise, reps, unit, levels)
    )

    private fun suggestion(workouts: List<Workout>) =
        WorkoutSteadyProgressAdvisor.suggest(workouts, today)

    @Test fun proposesOneRepAboveConsistentLatestSetsWithinTheCatalogRange() {
        val workouts = history()
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)

        val proposal = WorkoutProgressionPreview.propose(
            suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
        )

        assertNotNull(proposal)
        assertEquals("Squat không tạ", proposal!!.exerciseName)
        assertEquals(8, proposal.previousMinimumReps)
        assertEquals(9, proposal.targetReps)
        assertEquals(8, proposal.prescribedMinReps)
        assertEquals(12, proposal.prescribedMaxReps)
        assertEquals(2, proposal.requiredSets)
        assertEquals(3, proposal.maximumSets)
        assertEquals(60, proposal.restSeconds)
        assertEquals(today, proposal.latestDate)
        assertFalse(proposal.toString().contains("private"))
    }

    @Test fun missingActualRepValuesNeverProduceAGuessedTarget() {
        val workouts = history().map { it.copy(setLog = "") }
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }

    @Test fun incompleteActualRepValuesAreRejectedEvenWhenEffortCheckInsAreComplete() {
        val workouts = history().map {
            it.copy(setLog = "Squat không tạ: hiệp 1: 8 lần")
        }
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }

    @Test fun aHardOrVeryHardLatestSetCannotReceiveAProgressionPreview() {
        val workouts = history().toMutableList()
        val latest = workouts.last()
        workouts[workouts.lastIndex] = latest.copy(
            effortLog = "Squat không tạ: hiệp 1: Khó, cần nghỉ thêm, hiệp 2: Vừa sức"
        )
        assertNull(suggestion(workouts))
    }

    @Test fun differentRepCountsAcrossSetsDoNotReceiveOneExactTarget() {
        val workouts = history(reps = listOf(8, 10))
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }

    @Test fun targetAtTheCatalogMaximumDoesNotIncreasePastThePrescription() {
        val workouts = history(reps = listOf(12, 12))
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }

    @Test fun exerciseMustRemainInTheFilteredPlan() {
        val workouts = history()
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        val planWithoutSquat = TrainingCatalog.byId("mobility").moves
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, planWithoutSquat, today
            )
        )
    }

    @Test fun timeBasedExercisesReceiveFiveSecondPreviewWhenEverySetIsManageable() {
        val workouts = history(
            exercise = "Plank khuỷu hoặc plank gối",
            reps = listOf(18, 18),
            unit = "giây"
        )
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        val proposal = WorkoutProgressionPreview.propose(
            suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
        )
        assertNotNull(proposal)
        assertEquals(18, proposal!!.previousValue)
        assertEquals(23, proposal.targetValue)
        assertEquals("giây", proposal.unit)
        assertEquals(15, proposal.prescribedMinReps)
        assertEquals(30, proposal.prescribedMaxReps)
        assertEquals(2, proposal.requiredSets)
        assertEquals(2, proposal.maximumSets)
        // The curated Plank entry prescribes 45 seconds of rest.
        assertEquals(45, proposal.restSeconds)
    }

    @Test fun timedProgressionNeverExceedsTheCatalogMaximum() {
        val workouts = history(
            exercise = "Plank khuỷu hoặc plank gối",
            reps = listOf(30, 30),
            unit = "giây"
        )
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }

    @Test fun timedProgressionRequiresTheSameSelfReportedDurationForEverySet() {
        val workouts = history(
            exercise = "Plank khuỷu hoặc plank gối",
            reps = listOf(18, 20),
            unit = "giây"
        )
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)
        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("full").moves, today
            )
        )
    }
    @Test fun rhythmCountsReceiveOneStepProgressionInsideTheQuickMobilityCatalogRange() {
        val workouts = history(
            exercise = "Mở vai và ngực",
            reps = listOf(7),
            unit = "nhịp",
            levels = listOf(WorkoutEffortFeedback.Level.EASY)
        )
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)

        val proposal = WorkoutProgressionPreview.propose(
            suggestion!!, workouts, TrainingCatalog.byId("quickmobility").moves, today
        )

        assertNotNull(proposal)
        assertEquals("Mở vai và ngực", proposal!!.exerciseName)
        assertEquals("nhịp", proposal.unit)
        assertEquals(7, proposal.previousValue)
        assertEquals(8, proposal.targetValue)
        assertEquals(6, proposal.prescribedMinReps)
        assertEquals(8, proposal.prescribedMaxReps)
        assertEquals(1, proposal.requiredSets)
        assertEquals(1, proposal.maximumSets)
        assertEquals(30, proposal.restSeconds)
    }

    @Test fun rhythmProgressionNeverExceedsTheCatalogMaximum() {
        val workouts = history(
            exercise = "Mở vai và ngực",
            reps = listOf(8),
            unit = "nhịp",
            levels = listOf(WorkoutEffortFeedback.Level.MODERATE)
        )
        val suggestion = suggestion(workouts)
        assertNotNull(suggestion)

        assertNull(
            WorkoutProgressionPreview.propose(
                suggestion!!, workouts, TrainingCatalog.byId("quickmobility").moves, today
            )
        )
    }

}
