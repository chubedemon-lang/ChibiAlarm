package com.chibialarm.app

/**
 * Parses the set/rep dose already shown in the exercise library and tracks
 * user-confirmed sets. This is self-report, not sensor-verified performance.
 */
object WorkoutSetTracker {
    data class Plan(
        val requiredSets: Int,
        val maximumSets: Int,
        val targetMin: Int,
        val targetMax: Int,
        val unit: String
    ) {
        init {
            require(requiredSets in 1..20)
            require(maximumSets in requiredSets..20)
            require(targetMin in 1..200)
            require(targetMax in targetMin..200)
            require(unit in setOf("lần", "nhịp", "giây"))
        }

        val defaultRestSeconds: Int
            get() = if (unit == "giây") 30 else 45
    }

    private const val MAX_MOVES = 500
    private const val MAX_CHECKPOINT_CHARS = 4_096
    private val prescription = Regex(
        """^\s*(\d+)(?:\s*[–-]\s*(\d+))?\s*(?:hiệp)?\s*[×x]\s*(\d+)(?:\s*[–-]\s*(\d+))?\s*(lần|nhịp|giây)\b.*$""",
        RegexOption.IGNORE_CASE
    )

    /** Returns null for time-only guidance such as "3–5 phút" or "6–8 nhịp". */
    fun parse(dose: String): Plan? {
        val match = prescription.matchEntire(dose.trim()) ?: return null
        val requiredSets = match.groupValues[1].toIntOrNull() ?: return null
        val maximumSets = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: requiredSets
        val targetMin = match.groupValues[3].toIntOrNull() ?: return null
        val targetMax = match.groupValues[4].takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: targetMin
        val unit = match.groupValues[5].lowercase()
        return runCatching {
            Plan(requiredSets, maximumSets, targetMin, targetMax, unit)
        }.getOrNull()
    }

    /** Adds one self-confirmed set, including an optional set when the dose gives a range. */
    fun complete(completedSets: Int, plan: Plan): Int {
        require(completedSets in 0..plan.maximumSets)
        return (completedSets + 1).coerceAtMost(plan.maximumSets)
    }

    fun isComplete(completedSets: Int, plan: Plan): Boolean =
        completedSets in plan.requiredSets..plan.maximumSets

    fun encode(progress: Map<Int, Int>, moves: List<TrainingMove>): String {
        require(moves.size in 1..MAX_MOVES)
        return progress.entries.asSequence()
            .filter { (index, count) ->
                val plan = moves.getOrNull(index)?.let { parse(it.dose) }
                plan != null && count in 1..plan.maximumSets
            }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }
            .take(MAX_CHECKPOINT_CHARS)
    }

    /** Invalid, duplicate, stale and out-of-range checkpoint entries are ignored. */
    fun decode(raw: String?, moves: List<TrainingMove>): Map<Int, Int> {
        if (moves.isEmpty() || moves.size > MAX_MOVES || raw.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<Int, Int>()
        raw.take(MAX_CHECKPOINT_CHARS).split(',').take(MAX_MOVES).forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@forEach
            val index = parts[0].toIntOrNull() ?: return@forEach
            val count = parts[1].toIntOrNull() ?: return@forEach
            if (index !in moves.indices || index in result) return@forEach
            val plan = parse(moves[index].dose) ?: return@forEach
            if (count in 1..plan.maximumSets) result[index] = count
        }
        return result.toSortedMap()
    }
}
