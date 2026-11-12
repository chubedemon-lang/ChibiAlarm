package com.chibialarm.app

/**
 * Stores the value a person reports for each completed set.
 * Values are self-reported; this helper does not infer repetitions from time, audio or camera.
 */
object WorkoutSetRepetitionLog {
    private const val MAX_MOVES = 500
    private const val MAX_CHECKPOINT_CHARS = 4_096
    private const val MAX_SUMMARY_CHARS = 1_200

    fun append(progress: Map<Int, List<Int>>, moveIndex: Int, actualValue: Int,
               plan: WorkoutSetTracker.Plan): Map<Int, List<Int>> {
        require(moveIndex in 0 until MAX_MOVES)
        require(actualValue in 1..maximumValue(plan))
        val existing = progress[moveIndex].orEmpty()
        require(existing.size < plan.maximumSets)
        require(existing.all { it in 1..maximumValue(plan) })
        return progress.toSortedMap().toMutableMap().apply {
            put(moveIndex, existing + actualValue)
        }.toSortedMap()
    }

    fun encode(progress: Map<Int, List<Int>>, moves: List<TrainingMove>): String {
        require(moves.size <= MAX_MOVES)
        val output = StringBuilder()
        progress.entries.asSequence()
            .filter { (index, values) ->
                val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                plan != null && values.size in 1..plan.maximumSets &&
                    values.all { it in 1..maximumValue(plan) }
            }
            .sortedBy { it.key }
            .forEach { (index, values) ->
                val entry = "$index:${values.joinToString(".")}"
                val separatorLength = if (output.isEmpty()) 0 else 1
                if (output.length + separatorLength + entry.length > MAX_CHECKPOINT_CHARS) return@forEach
                if (separatorLength == 1) output.append(',')
                output.append(entry)
            }
        return output.toString()
    }

    /** Rejects values for unconfirmed sets, stale moves and malformed duplicate entries. */
    fun decode(raw: String?, moves: List<TrainingMove>, confirmedSets: Map<Int, Int>): Map<Int, List<Int>> {
        if (moves.isEmpty() || moves.size > MAX_MOVES || raw.isNullOrBlank()) return emptyMap()
        val result = sortedMapOf<Int, List<Int>>()
        raw.take(MAX_CHECKPOINT_CHARS).split(',').take(MAX_MOVES).forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator != entry.lastIndexOf(':')) return@forEach
            val index = entry.substring(0, separator).toIntOrNull() ?: return@forEach
            if (index !in moves.indices || index in result) return@forEach
            val plan = WorkoutSetTracker.parse(moves[index].dose) ?: return@forEach
            val confirmed = confirmedSets[index] ?: 0
            if (confirmed !in 1..plan.maximumSets) return@forEach
            val encodedValues = entry.substring(separator + 1).split('.')
            if (encodedValues.isEmpty() || encodedValues.size > confirmed) return@forEach
            val values = encodedValues.mapNotNull { it.toIntOrNull() }
            if (values.size != encodedValues.size || values.any { it !in 1..maximumValue(plan) }) return@forEach
            result[index] = values
        }
        return result
    }

    fun summary(progress: Map<Int, List<Int>>, moves: List<TrainingMove>): String =
        progress.entries.asSequence()
            .filter { (index, values) ->
                val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                plan != null && values.size in 1..plan.maximumSets &&
                    values.all { it in 1..maximumValue(plan) }
            }
            .sortedBy { it.key }
            .joinToString("; ") { (index, values) ->
                val plan = WorkoutSetTracker.parse(moves[index].dose)!!
                val sets = values.mapIndexed { setIndex, value ->
                    "hiệp ${setIndex + 1}: $value ${plan.unit}"
                }.joinToString(", ")
                "${moves[index].name}: $sets"
            }
            .take(MAX_SUMMARY_CHARS)

    private fun maximumValue(plan: WorkoutSetTracker.Plan): Int =
        if (plan.unit == "giây") 600 else 200
}
