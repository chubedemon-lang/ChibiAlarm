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

    /**
     * Offers a one-rep lower ceiling only after two consecutive HARD self-reports
     * for this exercise. The required minimum, confirmed sets and XP evidence stay
     * unchanged. The caller must show a preview and obtain explicit user approval.
     */
    fun repReductionPreviewRequest(
        level: WorkoutEffortFeedback.Level,
        plan: com.chibialarm.app.WorkoutSetTracker.Plan,
        completedSets: Int,
        previousLevelsForSameMove: List<WorkoutEffortFeedback.Level?>
    ): WorkoutMidSessionRequest.Request? {
        if (level != WorkoutEffortFeedback.Level.HARD) return null
        if (previousLevelsForSameMove.size > 20) return null
        if (previousLevelsForSameMove.lastOrNull() != WorkoutEffortFeedback.Level.HARD) return null
        if (plan.unit == "giây" || completedSets !in 1 until plan.maximumSets) return null
        val reducedCeiling = plan.targetMax - 1
        if (reducedCeiling < plan.targetMin) return null
        return WorkoutMidSessionRequest.Request(currentRepLimit = reducedCeiling)
    }


    /**
     * Offers a five-second lower ceiling only after two consecutive HARD self-reports
     * for a timed exercise. The catalog minimum, completed sets and XP evidence stay
     * unchanged; the caller must preview and obtain explicit approval.
     */
    fun durationReductionPreviewRequest(
        level: WorkoutEffortFeedback.Level,
        plan: com.chibialarm.app.WorkoutSetTracker.Plan,
        completedSets: Int,
        previousLevelsForSameMove: List<WorkoutEffortFeedback.Level?>
    ): WorkoutMidSessionRequest.Request? {
        if (level != WorkoutEffortFeedback.Level.HARD) return null
        if (previousLevelsForSameMove.size > 20) return null
        if (previousLevelsForSameMove.lastOrNull() != WorkoutEffortFeedback.Level.HARD) return null
        if (plan.unit != "giây" || completedSets !in 1 until plan.maximumSets) return null
        val reducedCeiling = plan.targetMax - 5
        if (reducedCeiling < plan.targetMin) return null
        return WorkoutMidSessionRequest.Request(currentDurationLimit = reducedCeiling)
    }

}
