package com.chibialarm.app

/**
 * One deterministic duration plan shared by the preview and guided workout.
 *
 * The selected duration is a target, not a promise: the minimum warm-up,
 * exercise and cool-down sequence can exceed very short targets. Estimates
 * use catalog timer hints plus prescribed rest between required sets.
 * Actual reps, transitions and any extra rest remain user-paced.
 */
object WorkoutDurationPlanner {
    private const val MAX_ROUNDS = 4

    private fun plannedMoveSeconds(move: TrainingMove): Int {
        val activeSeconds = move.seconds.coerceAtLeast(0)
        val restSeconds = WorkoutSetTracker.parse(move.dose, move.restSeconds)
            ?.let { (it.requiredSets - 1) * it.defaultRestSeconds } ?: 0
        return activeSeconds + restSeconds
    }

    data class Plan(
        val moves: List<TrainingMove>,
        val targetMinutes: Int,
        val estimatedSeconds: Int,
        val rounds: Int
    ) {
        val estimatedMinutes: Int
            get() = ((estimatedSeconds.toLong() + 59L) / 60L).toInt()

        val exceedsTarget: Boolean
            get() = estimatedSeconds > targetMinutes * 60
    }

    fun build(program: TrainingProgram, level: Int, targetMinutes: Int): Plan {
        require(level in 0..2)
        require(targetMinutes in 5..120)

        val base = TrainingCatalog.adjusted(program, level, targetMinutes)
        if (base.size <= 2) {
            val seconds = base.sumOf(::plannedMoveSeconds)
            return Plan(base, targetMinutes, seconds, 1)
        }

        val warmUp = base.first()
        val coolDown = base.last()
        val middle = base.drop(1).dropLast(1)
        val cycleSeconds = middle.sumOf(::plannedMoveSeconds)
        if (middle.isEmpty() || cycleSeconds <= 0) {
            val seconds = base.sumOf(::plannedMoveSeconds)
            return Plan(base, targetMinutes, seconds, 1)
        }

        val baseSeconds = base.sumOf(::plannedMoveSeconds)
        val additionalRounds = ((targetMinutes * 60 - baseSeconds).coerceAtLeast(0) /
            cycleSeconds).coerceAtMost(MAX_ROUNDS - 1)
        val rounds = (additionalRounds + 1).coerceIn(1, MAX_ROUNDS)
        val plannedMoves = listOf(warmUp) +
            (0 until rounds).flatMap { middle } +
            listOf(coolDown)
        val estimatedSeconds = plannedMoves.sumOf(::plannedMoveSeconds)
        return Plan(plannedMoves, targetMinutes, estimatedSeconds, rounds)
    }
}
