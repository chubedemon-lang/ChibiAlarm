package com.chibialarm.app

import com.chibialarm.app.fitness.WorkoutMidSessionRequest

/**
 * Decides whether an utterance from the step-by-step guide should be handed to the
 * existing mid-session preview/confirmation flow. It never applies a plan itself.
 */
object WorkoutStepPlanHandoff {
    enum class Decision {
        PREVIEW_CHANGE,
        SAFETY_STOP,
        NOT_A_CHANGE
    }

    fun decide(utterance: String?): Decision {
        if (utterance.isNullOrBlank()) return Decision.NOT_A_CHANGE
        return when (val parsed = WorkoutMidSessionRequest.parse(utterance)) {
            WorkoutMidSessionRequest.Result.SafetyStop -> Decision.SAFETY_STOP
            is WorkoutMidSessionRequest.Result.Proposed ->
                if (parsed.request.isRestOnly) Decision.NOT_A_CHANGE else Decision.PREVIEW_CHANGE
            WorkoutMidSessionRequest.Result.Unsupported -> Decision.NOT_A_CHANGE
        }
    }
}
