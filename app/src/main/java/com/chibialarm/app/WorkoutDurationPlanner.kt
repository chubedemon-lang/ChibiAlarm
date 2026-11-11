package com.chibialarm.app

/**
 * One deterministic, offline duration estimate shared by preview and guided workout.
 *
 * Each catalog prescription is included once. The planner deliberately does not duplicate
 * a complete circuit to fill a target: repeating a move also repeats its full set prescription
 * and can silently multiply training volume. A shortfall is reported so a future curated
 * extension block can be selected explicitly by goal/equipment instead.
 *
 * Estimates use catalog active-time hints, prescribed between-set rest and a small
 * level-sensitive transition allowance. Actual reps, pace and extra rest remain user-paced.
 */
object WorkoutDurationPlanner {
    private val minuteOnlyDose = Regex(
        """^\s*(\d+)(?:\s*[–-]\s*\d+)?\s*phút\b""",
        RegexOption.IGNORE_CASE
    )

    private fun activeSeconds(move: TrainingMove): Int {
        val minimumMinutes = minuteOnlyDose.find(move.dose)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (minimumMinutes != null && minimumMinutes in 1..60) {
            minimumMinutes * 60
        } else {
            move.seconds.coerceIn(0, 3_600)
        }
    }

    private fun prescribedRestSeconds(move: TrainingMove): Int =
        WorkoutSetTracker.parse(move.dose, move.restSeconds)
            ?.let { (it.requiredSets - 1) * it.defaultRestSeconds }
            ?: 0

    data class Plan(
        val moves: List<TrainingMove>,
        val targetMinutes: Int,
        val activeSeconds: Int,
        val prescribedRestSeconds: Int,
        val transitionSeconds: Int,
        val rounds: Int = 1
    ) {
        val estimatedSeconds: Int
            get() = (activeSeconds.toLong() + prescribedRestSeconds + transitionSeconds)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val estimatedMinutes: Int
            get() = ((estimatedSeconds.toLong() + 59L) / 60L).toInt()

        val exceedsTarget: Boolean
            get() = estimatedSeconds > targetMinutes * 60

        val unallocatedSeconds: Int
            get() = (targetMinutes.toLong() * 60L - estimatedSeconds)
                .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun build(program: TrainingProgram, level: Int, targetMinutes: Int): Plan {
        require(level in 0..2)
        require(targetMinutes in 5..120)

        // Keep the catalog sequence and its set prescriptions intact. Do not clone rounds.
        val moves = program.moves.toList()
        val activeSeconds = moves.sumOf(::activeSeconds)
        val restSeconds = moves.sumOf(::prescribedRestSeconds)
        val transitionAllowance = when (level) {
            0 -> 20
            1 -> 15
            else -> 10
        }
        val transitions = (moves.size - 1).coerceAtLeast(0) * transitionAllowance

        return Plan(
            moves = moves,
            targetMinutes = targetMinutes,
            activeSeconds = activeSeconds,
            prescribedRestSeconds = restSeconds,
            transitionSeconds = transitions,
            rounds = 1
        )
    }
}
