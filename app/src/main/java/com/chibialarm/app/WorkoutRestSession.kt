package com.chibialarm.app

/**
 * Keeps rest time separate from the active exercise countdown.
 * Rest is excluded from active workout minutes and never consumes the exercise timer.
 */
object WorkoutRestSession {
    data class State(
        val exerciseRemainingMs: Long,
        val restRemainingMs: Long
    )

    fun start(exerciseRemainingMs: Long, restSeconds: Int): State {
        require(exerciseRemainingMs in 0L..600_000L)
        require(restSeconds in 1..600)
        return State(
            exerciseRemainingMs = exerciseRemainingMs,
            restRemainingMs = restSeconds.toLong() * 1_000L
        )
    }

    /**
     * Finish only the current rest interval. The active exercise countdown is preserved,
     * and callers must still leave the workout paused until the user explicitly resumes.
     */
    fun skip(state: State): State {
        require(state.exerciseRemainingMs in 0L..600_000L)
        require(state.restRemainingMs in 0L..600_000L)
        return state.copy(restRemainingMs = 0L)
    }
}
