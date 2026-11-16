package com.chibialarm.app.fitness

/**
 * Preview-only repetition target edits for the unfinished part of one exercise.
 * Completed sets are represented by the caller's immutable self-reported log and are never rewritten.
 */
object WorkoutRemainingRepTarget {
    data class Preview(
        val previousTarget: Int?,
        val requestedTarget: Int,
        val completedSets: Int,
        val maximumSets: Int
    ) {
        val remainingSets: Int
            get() = (maximumSets - completedSets).coerceAtLeast(0)
    }

    /**
     * Use the exercise's own prescription as the editor default. Never inject a
     * generic "8" into a plank/time-based or higher-repetition prescription.
     */
    fun initialValue(currentTarget: Int?, prescribedMinimum: Int): Int? {
        if (currentTarget != null) return currentTarget.takeIf { it in 1..100 }
        return prescribedMinimum.takeIf { it in 1..100 }
    }

    fun preview(
        currentTarget: Int?,
        completedSets: Int,
        maximumSets: Int,
        requestedReps: Int
    ): Preview? {
        if (maximumSets !in 1..20 || completedSets !in 0 until maximumSets ||
            requestedReps !in 1..100 ||
            (currentTarget != null && currentTarget !in 1..100) ||
            currentTarget == requestedReps
        ) return null
        return Preview(currentTarget, requestedReps, completedSets, maximumSets)
    }

    /** Reject stale dialogs if progress, the current target or the set plan changed. */
    fun accept(
        proposal: Preview,
        currentTarget: Int?,
        completedSets: Int,
        maximumSets: Int
    ): Int? {
        if (currentTarget != proposal.previousTarget ||
            completedSets != proposal.completedSets ||
            maximumSets != proposal.maximumSets ||
            maximumSets !in 1..20 ||
            completedSets !in 0 until maximumSets ||
            proposal.requestedTarget !in 1..100 ||
            (proposal.previousTarget != null && proposal.previousTarget !in 1..100) ||
            proposal.requestedTarget == proposal.previousTarget ||
            proposal.remainingSets <= 0
        ) return null
        return proposal.requestedTarget
    }
}
