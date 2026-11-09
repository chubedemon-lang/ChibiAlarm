package com.chibialarm.app.fitness

/**
 * Explicit, offline mid-session edits for the coach UI.
 * A proposal is inert until accepted against the same progress snapshot.
 * Completed sets are never rewritten, even if the requested exercise repeats.
 */
object WorkoutTargetEdit {
    data class Target(val exerciseId: String, val reps: Int? = null, val restSeconds: Int? = null) {
        init {
            require(exerciseId.isNotBlank())
            require(reps != null || restSeconds != null)
            require(reps == null || reps in 1..100)
            require(restSeconds == null || restSeconds in 0..600)
        }
    }

    data class Preview(
        val before: CoachSessionPlanner.Session,
        val completedSets: Int,
        val target: Target,
        val after: CoachSessionPlanner.Session
    ) {
        val changed: Boolean get() = before != after
    }

    fun preview(
        session: CoachSessionPlanner.Session,
        completedSets: Int,
        target: Target
    ): Preview {
        require(completedSets in 0..session.sets.size)
        val after = if (session.pausedForSafety) session else session.copy(
            sets = session.sets.mapIndexed { index, set ->
                if (index < completedSets || set.exerciseId != target.exerciseId) set
                else set.copy(
                    reps = target.reps ?: set.reps,
                    restSeconds = target.restSeconds ?: set.restSeconds
                )
            }
        )
        return Preview(session, completedSets, target, after)
    }

    /** Reject outdated dialogs and safety stops; a no-op does not grant progress or XP. */
    fun accept(
        proposal: Preview,
        current: CoachSessionPlanner.Session,
        completedSets: Int
    ): CoachSessionPlanner.Session? {
        if (current != proposal.before || completedSets != proposal.completedSets ||
            current.pausedForSafety) return null
        return preview(current, completedSets, proposal.target).after
    }
}
