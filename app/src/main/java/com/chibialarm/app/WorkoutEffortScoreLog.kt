package com.chibialarm.app

/**
 * Bounded exact 1–10 perceived-effort scores, aligned to confirmed set indexes.
 *
 * Scores are optional self-reports, not sensor measurements or medical assessments.
 * The compact log contains only move indexes and integer scores; no speech or free text.
 */
object WorkoutEffortScoreLog {
    private const val MAX_MOVES = 500
    private const val MAX_ENCODED_CHARS = 1_200
    private const val MAX_SUMMARY_CHARS = 1_200

    fun append(
        current: Map<Int, List<Int?>>,
        moveIndex: Int,
        setIndex: Int,
        score: Int?,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): Map<Int, List<Int?>> {
        require(moves.size <= MAX_MOVES)
        require(moveIndex in moves.indices)
        require(score == null || score in 1..10)
        val plan = WorkoutSetTracker.parse(moves[moveIndex].dose)
            ?: throw IllegalArgumentException("Effort scores require a set-based exercise")
        val confirmed = confirmedSets[moveIndex] ?: 0
        require(confirmed in 1..plan.maximumSets && setIndex in 0 until confirmed)

        val existing = current[moveIndex].orEmpty()
        require(existing.size <= confirmed)
        val values = MutableList<Int?>(confirmed) { existing.getOrNull(it) }
        val previous = values[setIndex]
        require(previous == null || previous == score) {
            "A confirmed numeric effort score cannot be silently replaced"
        }
        values[setIndex] = score
        return current.toSortedMap().toMutableMap().apply {
            put(moveIndex, values)
        }.toSortedMap()
    }

    /** A category correction invalidates the old numeric score for that same set. */
    fun correctLatest(
        current: Map<Int, List<Int?>>,
        moveIndex: Int,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): Map<Int, List<Int?>> {
        require(moves.size <= MAX_MOVES)
        require(moveIndex in moves.indices)
        val plan = WorkoutSetTracker.parse(moves[moveIndex].dose)
            ?: throw IllegalArgumentException("Effort score correction requires a set-based exercise")
        val confirmed = confirmedSets[moveIndex] ?: 0
        require(confirmed in 1..plan.maximumSets)
        val existing = current[moveIndex].orEmpty()
        require(existing.size <= confirmed)
        if (existing.getOrNull(confirmed - 1) == null) return current.toSortedMap()

        val corrected = MutableList<Int?>(confirmed) { existing.getOrNull(it) }
        corrected[confirmed - 1] = null
        return current.toSortedMap().toMutableMap().apply {
            if (corrected.any { it != null }) put(moveIndex, corrected) else remove(moveIndex)
        }.toSortedMap()
    }

    fun encode(
        scores: Map<Int, List<Int?>>,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): String {
        require(moves.size <= MAX_MOVES)
        val output = StringBuilder()
        scores.entries.asSequence().sortedBy { it.key }.forEach { (index, values) ->
            val plan = moves.getOrNull(index)?.let { WorkoutSetTracker.parse(it.dose) }
                ?: return@forEach
            val confirmed = confirmedSets[index] ?: return@forEach
            if (confirmed !in 1..plan.maximumSets || values.isEmpty() || values.size > confirmed ||
                values.any { it != null && it !in 1..10 } || values.none { it != null }
            ) return@forEach
            val entry = "$index:" + values.joinToString(".") { it?.toString() ?: "-" }
            val separatorLength = if (output.isEmpty()) 0 else 1
            if (output.length + separatorLength + entry.length > MAX_ENCODED_CHARS) return@forEach
            if (separatorLength == 1) output.append(',')
            output.append(entry)
        }
        return output.toString()
    }

    /** Malformed, duplicate, stale, unconfirmed and out-of-range scores are ignored. */
    fun decode(
        raw: String?,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): Map<Int, List<Int?>> {
        if (moves.isEmpty() || moves.size > MAX_MOVES || raw.isNullOrBlank()) return emptyMap()
        val result = sortedMapOf<Int, List<Int?>>()
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
                else token.toIntOrNull()?.takeIf { it in 1..10 } ?: run {
                    valid = false
                    null
                }
            }
            if (!valid || values.none { it != null }) return@forEach
            result[index] = values + List(confirmed - values.size) { null }
        }
        return result
    }

    fun summary(
        scores: Map<Int, List<Int?>>,
        moves: List<TrainingMove>,
        confirmedSets: Map<Int, Int>
    ): String = scores.entries.asSequence()
        .sortedBy { it.key }
        .mapNotNull { (index, values) ->
            val move = moves.getOrNull(index) ?: return@mapNotNull null
            val plan = WorkoutSetTracker.parse(move.dose) ?: return@mapNotNull null
            val confirmed = confirmedSets[index] ?: return@mapNotNull null
            if (confirmed !in 1..plan.maximumSets || values.size !in 1..confirmed) {
                return@mapNotNull null
            }
            val rated = values.mapIndexedNotNull { setIndex, score ->
                score?.let { "hiệp ${setIndex + 1}: ${it}/10" }
            }.joinToString(", ")
            if (rated.isBlank()) null else "${move.name}: $rated"
        }
        .joinToString("; ")
        .take(MAX_SUMMARY_CHARS)
}
