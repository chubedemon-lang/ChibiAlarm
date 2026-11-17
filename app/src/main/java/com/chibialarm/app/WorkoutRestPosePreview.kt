package com.chibialarm.app

/**
 * Chooses the catalog move illustrated during a rest interval.
 *
 * If another set (including an optional set) remains, keep the current move.
 * Otherwise show the following move. At the end of a program, retain the
 * current move rather than inventing an exercise.
 */
internal object WorkoutRestPosePreview {
    fun nextMoveIndex(
        currentIndex: Int,
        moveCount: Int,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan?
    ): Int? {
        if (moveCount <= 0 || currentIndex !in 0 until moveCount) return null

        val anotherSetRemains = plan != null &&
            completedSets.coerceIn(0, plan.maximumSets) < plan.maximumSets

        if (anotherSetRemains) return currentIndex
        return (currentIndex + 1).takeIf { it < moveCount } ?: currentIndex
    }
}
