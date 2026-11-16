package com.chibialarm.app.fitness

/**
 * A confirmation boundary for mid-workout edits. Preview is pure: showing a dialog
 * cannot change the current session or award XP. The caller must explicitly accept.
 *
 * The expected snapshot is compared at acceptance time, so a delayed dialog cannot
 * overwrite newer progress, an updated plan, or a safety stop.
 */
object WorkoutChangeConfirmation {
    data class Preview(
        val before: CoachSessionPlanner.Session,
        val completedSets: Int,
        val request: CoachSessionPlanner.Request,
        val after: CoachSessionPlanner.Session
    ) {
        val changed: Boolean get() = before != after
    }

    fun preview(
        current: CoachSessionPlanner.Session,
        completedSets: Int,
        request: CoachSessionPlanner.Request
    ): Preview {
        val proposed = CoachSessionPlanner.adjustRemaining(current, completedSets, request)
        return Preview(current, completedSets, request, proposed)
    }

    /** Returns null when the preview is stale; never silently applies it. */
    fun accept(
        proposal: Preview,
        current: CoachSessionPlanner.Session,
        completedSets: Int
    ): CoachSessionPlanner.Session? {
        if (current != proposal.before || completedSets != proposal.completedSets) return null
        // Safety reports are immediate; all other edits must respect an active safety stop.
        if (current.pausedForSafety && proposal.request != CoachSessionPlanner.Request.PAIN) return null
        return CoachSessionPlanner.adjustRemaining(current, completedSets, proposal.request)
    }
}
