package com.chibialarm.app.fitness

import com.chibialarm.app.WorkoutEffortFeedback
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

    @Test fun veryHardFeedbackMustUseTheStopOrHoldFlowNotAnEasierContinuation() {
        assertNull(WorkoutEffortPlanChange.requestFor(WorkoutEffortFeedback.Level.VERY_HARD))
    }
}
