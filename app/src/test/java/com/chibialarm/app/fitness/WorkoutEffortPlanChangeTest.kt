package com.chibialarm.app.fitness

import com.chibialarm.app.WorkoutEffortFeedback
import com.chibialarm.app.WorkoutSetTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEffortPlanChangeTest {
    @Test fun hardSelfReportOffersOnlyAnEasierPreviewRequest() {
        val request = WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.HARD)

        assertEquals(
            WorkoutMidSessionRequest.Request(lowEnergy = true, preferEasier = true),
            request
        )
        assertTrue(request!!.lowEnergy)
        assertTrue(request.preferEasier)
        assertNull(request.restSeconds)
        assertFalse(request.addOptionalExercise)
    }

    @Test fun easyAndModerateFeedbackKeepTheExistingPlan() {
        assertNull(WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.EASY))
        assertNull(WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.MODERATE))
    }

    @Test fun veryHardFeedbackCanPreviewAnEasierPlanWithoutResumingOrAddingVolume() {
        val preview = WorkoutEffortPlanChange.pausedAlternativePreviewRequest()

        assertEquals(
            WorkoutMidSessionRequest.Request(lowEnergy = true, preferEasier = true),
            preview
        )
        assertNull(preview.restSeconds)
        assertFalse(preview.addOptionalExercise)
        // The ordinary automatic request path still cannot turn VERY_HARD into continuation.
        assertNull(WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.VERY_HARD))
    }

    @Test fun veryHardFeedbackMustUseTheStopOrHoldFlowNotAnEasierContinuation() {
        assertNull(WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.VERY_HARD))
    }

    @Test fun repeatedHardReportsOfferOnlyAPreviewThatPreservesRequiredMinimum() {
        val plan = WorkoutSetTracker.Plan(
            requiredSets = 2,
            maximumSets = 3,
            targetMin = 8,
            targetMax = 12,
            unit = "lần",
            restSeconds = 60
        )
        val request = WorkoutEffortPlanChange.repReductionPreviewRequest(
            WorkoutEffortFeedback.Level.HARD,
            plan,
            completedSets = 2,
            previousLevelsForSameMove = listOf(WorkoutEffortFeedback.Level.HARD)
        )
        assertEquals(WorkoutMidSessionRequest.Request(currentRepLimit = 11), request)
        assertTrue(request!!.currentRepLimit == 11)
        assertFalse(request.lowEnergy)
        assertFalse(request.addOptionalExercise)
    }

    @Test fun firstHardReportDoesNotTriggerRepReduction() {
        val plan = WorkoutSetTracker.Plan(2, 3, 8, 12, "lần", 60)
        assertNull(
            WorkoutEffortPlanChange.repReductionPreviewRequest(
                WorkoutEffortFeedback.Level.HARD, plan, 1, emptyList()
            )
        )
    }

    @Test fun repReductionNeverDropsBelowRequiredMinimumOrTouchesCompletedSets() {
        val fixedTarget = WorkoutSetTracker.Plan(2, 3, 8, 8, "lần", 60)
        val allSetsDone = WorkoutSetTracker.Plan(2, 2, 8, 12, "lần", 60)
        val previousHard = listOf(WorkoutEffortFeedback.Level.HARD)
        assertNull(WorkoutEffortPlanChange.repReductionPreviewRequest(
            WorkoutEffortFeedback.Level.HARD, fixedTarget, 1, previousHard
        ))
        assertNull(WorkoutEffortPlanChange.repReductionPreviewRequest(
            WorkoutEffortFeedback.Level.HARD, allSetsDone, 2, previousHard
        ))
    }

    @Test fun veryHardAndTimedMovesStayOnTheSafetyHoldPath() {
        val reps = WorkoutSetTracker.Plan(2, 3, 8, 12, "lần", 60)
        val timed = WorkoutSetTracker.Plan(2, 3, 15, 30, "giây", 60)
        val previousHard = listOf(WorkoutEffortFeedback.Level.HARD)
        assertNull(WorkoutEffortPlanChange.repReductionPreviewRequest(
            WorkoutEffortFeedback.Level.VERY_HARD, reps, 1, previousHard
        ))
        assertNull(WorkoutEffortPlanChange.repReductionPreviewRequest(
            WorkoutEffortFeedback.Level.HARD, timed, 1, previousHard
        ))
    }

}
