package com.chibialarm.app.fitness

import com.chibialarm.app.WorkoutEffortFeedback

/**
 * Converts an explicit HARD self-report into an optional preview request.
 *
 * VERY_HARD remains a stop/hold flow; EASY and MODERATE keep the prescribed plan.
 * This object never applies a plan or increases workload by itself.
 */
object WorkoutEffortPlanChange {
    /**
     * A very-hard check-in may inspect a lighter catalog plan while paused.
     * This is preview input only: it does not resume, add volume, or apply a plan.
     */
    fun pausedAlternativePreviewRequest(): WorkoutMidSessionRequest.Request =
        WorkoutMidSessionRequest.Request(lowEnergy = true, preferEasier = true)

    fun requestFor(level: WorkoutEffortFeedback.Level): WorkoutMidSessionRequest.Request? =
        when (level) {
            WorkoutEffortFeedback.Level.HARD -> WorkoutMidSessionRequest.Request(
                lowEnergy = true,
                preferEasier = true
            )
            WorkoutEffortFeedback.Level.EASY,
            WorkoutEffortFeedback.Level.MODERATE,
            WorkoutEffortFeedback.Level.VERY_HARD -> null
        }
}
