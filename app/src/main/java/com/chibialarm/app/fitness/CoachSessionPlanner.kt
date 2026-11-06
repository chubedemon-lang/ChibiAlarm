package com.chibialarm.app.fitness

/**
 * Offline-first workout adjustments. This engine never sends health information to a server.
 * The UI can use it while an AI response is unavailable or the device has no connection.
 */
object CoachSessionPlanner {
    enum class Request { EASIER, HARDER, SHORTER, MORE_REST, PAIN, RESUME }
    data class SetPlan(val exerciseId: String, val reps: Int, val restSeconds: Int) {
        init {
            require(exerciseId.isNotBlank())
            require(reps in 1..100)
            require(restSeconds in 0..600)
        }
    }
    data class Session(val sets: List<SetPlan>, val pausedForSafety: Boolean = false) {
        init { require(sets.size <= 100) }
    }

    /**
     * A pain report always pauses the session. No subsequent voice command may
     * automatically resume it: explicit user acknowledgement is required in UI.
     */
    fun adjust(session: Session, request: Request): Session {
        if (request == Request.PAIN) return session.copy(pausedForSafety = true)
        if (session.pausedForSafety) return session
        return when (request) {
            Request.EASIER -> session.copy(sets = session.sets.map {
                it.copy(reps = (it.reps * 3 / 4).coerceAtLeast(1), restSeconds = (it.restSeconds + 20).coerceAtMost(600))
            })
            Request.HARDER -> session.copy(sets = session.sets.map {
                it.copy(reps = (it.reps + 2).coerceAtMost(100))
            })
            Request.SHORTER -> session.copy(sets = session.sets.filterIndexed { index, _ -> index % 2 == 0 })
            Request.MORE_REST -> session.copy(sets = session.sets.map {
                it.copy(restSeconds = (it.restSeconds + 30).coerceAtMost(600))
            })
            Request.RESUME, Request.PAIN -> session
        }
    }
}
