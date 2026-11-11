package com.chibialarm.app

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Curated, offline substitutions for a few common exercise patterns after user confirmation.
 * This is a convenience for changing an unstarted step, not injury treatment.
 */
object WorkoutMoveSubstitution {
    private const val MAX_MOVES = 500
    private const val MAX_CHECKPOINT_CHARS = 4_096

    private val easierNames = mapOf(
        "Squat không tạ" to listOf("Đứng lên – ngồi xuống"),
        "Squat chậm 3 giây" to listOf("Đứng lên – ngồi xuống"),
        "Bước lùi – lunge" to listOf("Đứng lên – ngồi xuống"),
        // Offer a forearm-supported option as well as hand-supported regressions.
        // The constraint filter below will remove the latter when wrist loading is avoided.
        "Chống đẩy sàn" to listOf("Chống đẩy gối", "Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Chống đẩy tường" to listOf("Ép ngực tường bằng cẳng tay"),
        "Cat-cow nhẹ" to listOf("Mở vai và ngực"),
        "Chống đẩy gối" to listOf("Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Chống đẩy trên bề mặt cao" to listOf("Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Mountain climber chậm" to listOf("Đi bộ nhanh tại chỗ"),
        "Plank khuỷu hoặc plank gối" to listOf("Bird-dog", "Dead bug cơ bụng"),
        "Side plank gối" to listOf("Bird-dog", "Dead bug cơ bụng"),
        "Gập hông với tạ đơn hoặc dây" to listOf("Nâng hông trên sàn"),
        "Đẩy vai với tạ đơn hoặc dây" to listOf("Ép ngực tường bằng cẳng tay")
    )

    private val catalogMoves: List<TrainingMove> by lazy {
        TrainingCatalog.allMoves
    }

    fun alternativesFor(
        move: TrainingMove,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): List<TrainingMove> =
        easierNames[move.name].orEmpty()
            .mapNotNull { name -> catalogMoves.firstOrNull { it.name == name } }
            .filter { WorkoutConstraintFilter.isAllowed(it, constraints) }
            .distinctBy { it.name }

    /** Returns a copied list only when the selected replacement is explicitly allowed. */
    fun replace(
        currentMoves: List<TrainingMove>,
        index: Int,
        replacement: TrainingMove,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): List<TrainingMove>? {
        val current = currentMoves.getOrNull(index) ?: return null
        val approved = alternativesFor(current, constraints).firstOrNull { it.name == replacement.name } ?: return null
        // Insert the canonical catalog object, never caller-supplied exercise instructions.
        return currentMoves.toMutableList().also { it[index] = approved }
    }

    /** Store only bounded index/name pairs; no free-form transcript or health text is accepted. */
    fun encode(
        substitutions: Map<Int, TrainingMove>,
        originalMoves: List<TrainingMove>,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): String {
        if (originalMoves.isEmpty() || originalMoves.size > MAX_MOVES) return ""
        return substitutions.entries.asSequence()
            .filter { (index, replacement) ->
                index in originalMoves.indices &&
                    alternativesFor(originalMoves[index], constraints).any { it.name == replacement.name }
            }
            .sortedBy { it.key }
            .take(MAX_MOVES)
            .joinToString(";") { (index, replacement) ->
                "$index:${URLEncoder.encode(replacement.name, "UTF-8")}"
            }
            .take(MAX_CHECKPOINT_CHARS)
    }

    /** Invalid, duplicate, stale and unapproved substitutions are ignored on restore. */
    fun decode(
        raw: String?,
        originalMoves: List<TrainingMove>,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): Map<Int, TrainingMove> {
        if (raw.isNullOrBlank() || raw.length > MAX_CHECKPOINT_CHARS ||
            originalMoves.isEmpty() || originalMoves.size > MAX_MOVES
        ) return emptyMap()

        val result = linkedMapOf<Int, TrainingMove>()
        raw.split(';').take(MAX_MOVES).forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return@forEach
            val index = entry.substring(0, separator).toIntOrNull() ?: return@forEach
            if (index !in originalMoves.indices || index in result) return@forEach
            val name = runCatching {
                URLDecoder.decode(entry.substring(separator + 1), "UTF-8")
            }.getOrNull() ?: return@forEach
            val replacement = alternativesFor(originalMoves[index], constraints)
                .firstOrNull { it.name == name } ?: return@forEach
            result[index] = replacement
        }
        return result.toSortedMap()
    }

    fun apply(
        originalMoves: List<TrainingMove>,
        raw: String?,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): List<TrainingMove> {
        val result = originalMoves.toMutableList()
        decode(raw, originalMoves, constraints).forEach { (index, replacement) ->
            result[index] = replacement
        }
        return result
    }
}
