package com.chibialarm.app

/**
 * Stores the value a person reports for each completed set.
 * Values are self-reported; this helper does not infer repetitions from time, audio or camera.
 */
object WorkoutSetRepetitionLog {
    private const val MAX_MOVES = 500
    private const val MAX_CHECKPOINT_CHARS = 4_096
    private const val MAX_SUMMARY_CHARS = 1_200

    fun append(progress: Map<Int, List<Int>>, moveIndex: Int, setIndex: Int, actualValue: Int,
               plan: WorkoutSetTracker.Plan): Map<Int, List<Int>> {
        require(moveIndex in 0 until MAX_MOVES)
        require(setIndex in 0 until plan.maximumSets)
        require(actualValue in 1..maximumValue(plan))
        val existing = progress[moveIndex].orEmpty().toMutableList()
        require(existing.size <= setIndex)
        require(existing.all { it == 0 || it in 1..maximumValue(plan) })
        while (existing.size < setIndex) existing.add(0)
        existing.add(actualValue)
        return progress.toSortedMap().toMutableMap().apply { put(moveIndex, existing) }.toSortedMap()
    }

    fun encode(progress: Map<Int, List<Int>>, moves: List<TrainingMove>): String {
        require(moves.size <= MAX_MOVES)
        val output = StringBuilder()
        progress.entries.asSequence()
            .filter { (index, values) ->
                val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                plan != null && values.size in 1..plan.maximumSets &&
                    values.all { it == 0 || it in 1..maximumValue(plan) }
            }
            .sortedBy { it.key }
            .forEach { (index, values) ->
                val entry = "$index:${values.joinToString(".") { value -> if (value == 0) "-" else value.toString() }}"
                val separatorLength = if (output.isEmpty()) 0 else 1
                if (output.length + separatorLength + entry.length > MAX_CHECKPOINT_CHARS) return@forEach
                if (separatorLength == 1) output.append(',')
                output.append(entry)
            }
        return output.toString()
    }

    /** Rebuilds unknown legacy values as zero placeholders; only confirmed sets are accepted. */
    fun decode(raw: String?, moves: List<TrainingMove>, confirmedSets: Map<Int, Int>): Map<Int, List<Int>> {
        if (moves.isEmpty() || moves.size > MAX_MOVES) return emptyMap()
        val result = sortedMapOf<Int, List<Int>>()
        val seenIndices = mutableSetOf<Int>()
        confirmedSets.forEach { (index, count) ->
            val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
            if (plan != null && count in 1..plan.maximumSets) result[index] = List(count) { 0 }
        }
        if (raw.isNullOrBlank()) return result
        raw.take(MAX_CHECKPOINT_CHARS).split(',').take(MAX_MOVES).forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator != entry.lastIndexOf(':')) return@forEach
            val index = entry.substring(0, separator).toIntOrNull() ?: return@forEach
            if (index !in moves.indices || index in seenIndices) return@forEach
            val plan = WorkoutSetTracker.parse(moves[index].dose) ?: return@forEach
            val confirmed = confirmedSets[index] ?: 0
            if (confirmed !in 1..plan.maximumSets) return@forEach
            val encodedValues = entry.substring(separator + 1).split('.')
            if (encodedValues.isEmpty() || encodedValues.size > confirmed) return@forEach
            val values = encodedValues.mapNotNull { token ->
                if (token == "-") 0 else token.toIntOrNull()
            }
            if (values.size != encodedValues.size || values.any { it !in 0..maximumValue(plan) }) return@forEach
            result[index] = values + List(confirmed - values.size) { 0 }
            seenIndices.add(index)
        }
        return result
    }

    fun summary(progress: Map<Int, List<Int>>, moves: List<TrainingMove>): String =
        progress.entries.asSequence()
            .mapNotNull { (index, values) ->
                val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                    ?: return@mapNotNull null
                if (values.size !in 1..plan.maximumSets ||
                    values.any { it != 0 && it !in 1..maximumValue(plan) }) return@mapNotNull null
                val sets = values.mapIndexedNotNull { setIndex, value ->
                    if (value == 0) null else "hiệp ${setIndex + 1}: $value ${plan.unit}"
                }.joinToString(", ")
                if (sets.isBlank()) null else "${moves[index].name}: $sets"
            }
            .take(MAX_MOVES)
            .joinToString("; ")
            .take(MAX_SUMMARY_CHARS)

    private fun maximumValue(plan: WorkoutSetTracker.Plan): Int =
        if (plan.unit == "giây") 600 else 200
}
