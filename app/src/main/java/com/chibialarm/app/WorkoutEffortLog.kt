package com.chibialarm.app

/**
 * Bounded, offline record of a user's optional perceived-effort answer for each confirmed set.
 * This is a subjective check-in, not a sensor measurement, diagnosis, or training-load estimate.
 */
object WorkoutEffortLog {
    private const val MAX_MOVES = 500
    private const val MAX_ENCODED_CHARS = 4_096
    private const val MAX_SUMMARY_CHARS = 1_200

    fun append(
        current: Map<Int, List<WorkoutEffortFeedback.Level?>>,
        moveIndex: Int,
        setIndex: Int,
        level: WorkoutEffortFeedback.Level,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): Map<Int, List<WorkoutEffortFeedback.Level?>> {
        require(moves.size <= MAX_MOVES)
        require(moveIndex in moves.indices)
        val plan = WorkoutSetTracker.parse(moves[moveIndex].dose)
            ?: throw IllegalArgumentException("Effort check-ins require a set-based exercise")
        val confirmed = confirmedSets[moveIndex] ?: 0
        require(confirmed in 1..plan.maximumSets && setIndex in 0 until confirmed)

        val existing = current[moveIndex].orEmpty()
        require(existing.size <= confirmed)
        val values = MutableList<WorkoutEffortFeedback.Level?>(confirmed) { existing.getOrNull(it) }
        val previous = values[setIndex]
        require(previous == null || previous == level) {
            "A set effort check-in cannot be silently replaced"
        }
        values[setIndex] = level
        return current.toSortedMap().toMutableMap().apply {
            put(moveIndex, values)
        }.toSortedMap()
    }

    fun encode(
        progress: Map<Int, List<WorkoutEffortFeedback.Level?>>,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): String {
        require(moves.size <= MAX_MOVES)
        val output = StringBuilder()
        progress.entries.asSequence().sortedBy { it.key }.forEach { (index, values) ->
            val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                ?: return@forEach
            val confirmed = confirmedSets[index] ?: return@forEach
            if (confirmed !in 1..plan.maximumSets || values.isEmpty() || values.size > confirmed) {
                return@forEach
            }
            val entry = "$index:" + values.joinToString(".") { it?.name ?: "-" }
            val separatorLength = if (output.isEmpty()) 0 else 1
            if (output.length + separatorLength + entry.length > MAX_ENCODED_CHARS) return@forEach
            if (separatorLength == 1) output.append(',')
            output.append(entry)
        }
        return output.toString()
    }

    /** Invalid, duplicate, stale, or unconfirmed entries are ignored; old sets remain unrated. */
    fun decode(
        raw: String?,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): Map<Int, List<WorkoutEffortFeedback.Level?>> {
        if (moves.isEmpty() || moves.size > MAX_MOVES) return emptyMap()
        val result = sortedMapOf<Int, List<WorkoutEffortFeedback.Level?>>()
        confirmedSets.forEach { (index, count) ->
            val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
            if (plan != null && count in 1..plan.maximumSets) {
                result[index] = List(count) { null }
            }
        }
        if (raw.isNullOrBlank()) return result

        val seen = mutableSetOf<Int>()
        raw.take(MAX_ENCODED_CHARS).split(',').take(MAX_MOVES).forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator != entry.lastIndexOf(':')) return@forEach
            val index = entry.substring(0, separator).toIntOrNull() ?: return@forEach
            if (index !in moves.indices || !seen.add(index)) return@forEach
            val plan = WorkoutSetTracker.parse(moves[index].dose) ?: return@forEach
            val confirmed = confirmedSets[index] ?: return@forEach
            if (confirmed !in 1..plan.maximumSets) return@forEach

            val tokens = entry.substring(separator + 1).split('.')
            if (tokens.isEmpty() || tokens.size > confirmed) return@forEach
            var valid = true
            val values = tokens.map { token ->
                if (token == "-") null
                else try {
                    WorkoutEffortFeedback.Level.valueOf(token)
                } catch (_: IllegalArgumentException) {
                    valid = false
                    null
                }
            }
            if (!valid) return@forEach
            result[index] = values + List(confirmed - values.size) { null }
        }
        return result
    }

    fun summary(
        progress: Map<Int, List<WorkoutEffortFeedback.Level?>>,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): String = progress.entries.asSequence()
        .sortedBy { it.key }
        .mapNotNull { (index, values) ->
            val move = moves.getOrNull(index) ?: return@mapNotNull null
            val plan = WorkoutSetTracker.parse(move.dose) ?: return@mapNotNull null
            val confirmed = confirmedSets[index] ?: return@mapNotNull null
            if (confirmed !in 1..plan.maximumSets || values.size !in 1..confirmed) {
                return@mapNotNull null
            }
            val rated = values.mapIndexedNotNull { setIndex, level ->
                level?.let { "hiệp ${setIndex + 1}: ${it.label}" }
            }.joinToString(", ")
            if (rated.isBlank()) null else "${move.name}: $rated"
        }
        .joinToString("; ")
        .take(MAX_SUMMARY_CHARS)
}
}
