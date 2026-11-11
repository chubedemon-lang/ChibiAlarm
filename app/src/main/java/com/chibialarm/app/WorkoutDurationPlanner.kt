package com.chibialarm.app

/**
 * One deterministic, offline duration estimate shared by preview and guided workout.
 * A small curated extension block may be added only when there is time and a matching
 * goal/equipment/impact choice. The base prescription is never cloned.
 */
object WorkoutDurationPlanner {
    private val minuteOnlyDose = Regex(
        """^\s*(\d+)(?:\s*[–-]\s*\d+)?\s*phút\b""",
        RegexOption.IGNORE_CASE
    )

    internal fun activeSeconds(move: TrainingMove): Int {
        val minimumMinutes = minuteOnlyDose.find(move.dose)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (minimumMinutes != null && minimumMinutes in 1..60) minimumMinutes * 60
        else move.seconds.coerceIn(0, 3_600)
    }

    internal fun prescribedRestSeconds(move: TrainingMove): Int =
        WorkoutSetTracker.parse(move.dose, move.restSeconds)
            ?.let { (it.requiredSets - 1) * it.defaultRestSeconds }
            ?: 0

    internal fun transitionAllowance(level: Int): Int = when (level) {
        0 -> 20
        1 -> 15
        2 -> 10
        else -> throw IllegalArgumentException("level must be 0..2")
    }

    private fun estimateSeconds(moves: List<TrainingMove>, level: Int): Int {
        val active = moves.sumOf(::activeSeconds).toLong()
        val rest = moves.sumOf(::prescribedRestSeconds).toLong()
        val transitions = (moves.size - 1).coerceAtLeast(0).toLong() * transitionAllowance(level)
        return (active + rest + transitions).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    data class Plan(
        val moves: List<TrainingMove>,
        val targetMinutes: Int,
        val activeSeconds: Int,
        val prescribedRestSeconds: Int,
        val transitionSeconds: Int,
        val baseMoveCount: Int,
        val extensionMoves: List<TrainingMove>,
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

    fun build(
        program: TrainingProgram,
        level: Int,
        targetMinutes: Int,
        preferences: WorkoutExtensionPreferences = WorkoutExtensionPreferences()
    ): Plan {
        require(level in 0..2)
        require(targetMinutes in 5..120)

        val baseMoves = program.moves.toList()
        val baseEstimate = estimateSeconds(baseMoves, level)
        val availableSeconds = (targetMinutes.toLong() * 60L - baseEstimate)
            .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val extensions = WorkoutExtensionSelector.select(
            baseMoves, level, availableSeconds, preferences
        )

        // Keep the cool-down/final stretch last even when an extension block is inserted.
        val finishIndex = baseMoves.indexOfLast {
            it.name == "Thả lỏng" || it.name == "Giãn cơ và thở chậm"
        }
        val moves = if (finishIndex >= 0) {
            baseMoves.take(finishIndex) + extensions + baseMoves.drop(finishIndex)
        } else {
            baseMoves + extensions
        }
        val activeSeconds = moves.sumOf(::activeSeconds)
        val restSeconds = moves.sumOf(::prescribedRestSeconds)
        val transitions = (moves.size - 1).coerceAtLeast(0) * transitionAllowance(level)

        return Plan(
            moves = moves,
            targetMinutes = targetMinutes,
            activeSeconds = activeSeconds,
            prescribedRestSeconds = restSeconds,
            transitionSeconds = transitions,
            baseMoveCount = baseMoves.size,
            extensionMoves = extensions,
            rounds = 1
        )
    }
}