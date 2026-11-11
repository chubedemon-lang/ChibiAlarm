package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutDurationPlanner
import com.chibialarm.app.WorkoutSetTracker

/**
 * Conservative local estimate for the unfinished portion of a guided session.
 *
 * Uses the active step's real remaining timer, self-confirmed set counts,
 * catalog durations for upcoming moves, prescribed between-set rests and
 * experience-based transitions. Optional extra sets are not promised.
 */
object WorkoutSessionTimeEstimate {
    private const val MAX_ESTIMATE_SECONDS = 86_400

    fun seconds(
        moves: List<TrainingMove>,
        currentIndex: Int,
        completedMoves: Set<Int>,
        completedSets: Map<Int, Int>,
        currentExerciseRemainingSeconds: Int,
        resting: Boolean,
        restSecondsRemaining: Int,
        level: Int
    ): Int {
        require(moves.size in 1..500)
        require(currentIndex in moves.indices)
        require(currentExerciseRemainingSeconds in 0..600)
        require(restSecondsRemaining in 0..600)
        require(level in 0..2)

        // A set-based move may be marked complete at its minimum dose while the user
        // takes an optional rest. Keep that active rest visible even though the required
        // move has already been recorded as complete.
        val includeCurrent = currentIndex !in completedMoves ||
            resting || currentExerciseRemainingSeconds > 0
        val remainingIndices = buildList {
            if (includeCurrent) add(currentIndex)
            for (index in currentIndex + 1 until moves.size) {
                if (index !in completedMoves) add(index)
            }
        }
        if (remainingIndices.isEmpty()) return 0

        var total = 0L
        remainingIndices.forEach { index ->
            val move = moves[index]
            if (index == currentIndex) {
                // The exercise timer is paused, not discarded, during a separate rest countdown.
                // Count both clocks so the ETA includes the remaining active block after rest.
                total += currentExerciseRemainingSeconds.toLong()
                if (resting) total += restSecondsRemaining.toLong()

                val plan = WorkoutSetTracker.parse(move.dose, move.restSeconds)
                if (plan != null) {
                    val done = (completedSets[index] ?: 0).coerceIn(0, plan.maximumSets)
                    val setsLeft = (plan.requiredSets - done).coerceAtLeast(0)
                    // The current rest countdown, if any, is added separately above.
                    total += (setsLeft - 1).coerceAtLeast(0).toLong() *
                        plan.defaultRestSeconds.toLong()
                }
            } else {
                total += WorkoutDurationPlanner.activeSeconds(move).toLong()
                total += WorkoutDurationPlanner.prescribedRestSeconds(move).toLong()
            }
            total = total.coerceAtMost(MAX_ESTIMATE_SECONDS.toLong())
        }

        // If the current step is already complete, the first upcoming move still
        // requires one transition from the screen's current position.
        val transitions = if (includeCurrent) {
            (remainingIndices.size - 1).coerceAtLeast(0)
        } else {
            remainingIndices.size
        }
        total += transitions.toLong() * WorkoutDurationPlanner.transitionAllowance(level)
        return total.coerceAtMost(MAX_ESTIMATE_SECONDS.toLong()).toInt()
    }
}
