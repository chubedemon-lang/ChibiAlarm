package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Bounded, offline search across program names and the reviewed exercise catalog.
 * Search results only locate content; normal equipment and movement filters still run
 * before a program can be previewed or started.
 */
internal object WorkoutLibrarySearch {
    const val MAX_QUERY_CHARS = 80
    private const val MAX_PROGRAMS = 200
    private const val MAX_RESULTS = 100

    data class Match(
        val program: TrainingProgram,
        val matchingMoves: List<TrainingMove>
    )

    fun filter(
        programs: List<TrainingProgram>,
        category: String,
        query: String
    ): List<Match> {
        val safePrograms = programs.take(MAX_PROGRAMS)
        val terms = normalize(query.take(MAX_QUERY_CHARS))
            .split(' ')
            .filter { it.isNotBlank() }
            .distinct()
        return safePrograms.asSequence()
            .filter { category == "Tất cả" || it.category == category }
            .mapNotNull { program ->
                if (terms.isEmpty()) return@mapNotNull Match(program, emptyList())
                val muscleFocusQuery = isMuscleFocusQuery(terms)
                val matchingMoves = program.moves.filter { move ->
                    if (muscleFocusQuery) {
                        val focus = WorkoutExerciseFocus.descriptionFor(move.name)
                        focus != null && matchesFocusPhrase(focus, focusTerms(terms))
                    } else {
                        matches(moveSearchText(move), terms)
                    }
                }
                // A muscle-intent query must not fall back to program titles/subtitles:
                // those can contain unrelated anatomy words and create speculative matches.
                val programMatches = !muscleFocusQuery && matches(
                    listOf(program.name, program.subtitle, program.category).joinToString(" "),
                    terms
                )
                if (programMatches || matchingMoves.isNotEmpty()) {
                    Match(program, matchingMoves)
                } else {
                    null
                }
            }
            .take(MAX_RESULTS)
            .toList()
    }

    /**
     * Focus the library preview on the first searched exercise that survived the current
     * duration/equipment/constraint plan. If the search match was filtered out, start safely
     * at the first remaining move instead of opening an invalid index.
     */
    fun previewIndex(match: Match, plannedMoves: List<TrainingMove>): Int {
        if (plannedMoves.isEmpty() || match.matchingMoves.isEmpty()) return 0
        val matchedNames = match.matchingMoves.map { it.name }.toSet()
        return plannedMoves.indexOfFirst { it.name in matchedNames }.coerceAtLeast(0)
    }

    private fun moveSearchText(move: TrainingMove): String = buildString {
        append(move.name).append(' ')
        append(move.dose).append(' ')
        append(move.steps).append(' ')
        append(move.easier).append(' ')
        append(move.avoid).append(' ')
        WorkoutExerciseFocus.descriptionFor(move.name)?.let { append(it).append(' ') }
        move.equipmentOptions.forEach { equipment ->
            append(
                when (equipment) {
                    TrainingEquipment.DUMBBELL -> "tạ đơn "
                    TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực "
                    TrainingEquipment.STABLE_CHAIR -> "ghế vững chắc "
                    TrainingEquipment.MAT -> "thảm tập "
                }
            )
        }
        if (move.requiresJumping) append("bật nhảy ")
        if (move.requiresOverheadReach) append("đưa tay quá đầu ")
    }

    // Muscle intent is searched only inside reviewed focus notes. This prevents
    // unrelated tokens from exercise steps (e.g. "cơ trung tâm" + "đầu gối")
    // from being combined into a false muscle-group match.
    private val muscleFocusMarkers = setOf(
        "dui", "mong", "nguc", "vai", "lung", "chan", "bung", "hong"
    )
    private val focusQueryFillers = setOf(
        "co", "bai", "tap", "nhom", "chinh", "thuong", "tham", "gia"
    )

    private fun isMuscleFocusQuery(terms: List<String>): Boolean =
        terms.any { it in muscleFocusMarkers } ||
            ("tay" in terms && terms.any { it == "sau" || it == "truoc" }) ||
            ("co" in terms && terms.any { it == "trung" || it == "dau" })

    private fun focusTerms(terms: List<String>): List<String> =
        terms.filterNot { it in focusQueryFillers }.ifEmpty { terms }

    /** Muscle-focus terms must be a contiguous phrase in a curated note, not scattered tokens. */
    private fun matchesFocusPhrase(value: String, terms: List<String>): Boolean {
        val phrase = normalize(terms.joinToString(" "))
        if (phrase.isBlank()) return false
        val normalized = normalize(value)
        return " $normalized ".contains(" $phrase ")
    }

    private fun matches(value: String, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val tokens = normalize(value).split(' ').filter { it.isNotBlank() }.toSet()
        return terms.all { it in tokens }
    }

    private fun normalize(value: String): String {
        val latinD = value.replace('đ', 'd').replace('Đ', 'D')
        return Normalizer.normalize(latinD, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
