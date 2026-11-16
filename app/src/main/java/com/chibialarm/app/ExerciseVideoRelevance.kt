package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative title-only relevance screening for exercise tutorial links.
 *
 * A match only means the public title contains a curated exercise alias (or
 * enough distinctive terms for an uncatalogued move). It never verifies video
 * frames, coaching credentials, movement technique, or exercise safety.
 */
object ExerciseVideoRelevance {
    private val curatedAliases = mapOf(
        "squat khong ta" to listOf("bodyweight squat", "air squat", "squat"),
        "squat cham 3 giay" to listOf("tempo squat", "slow squat", "squat"),
        "dung len ngoi xuong" to listOf("sit to stand", "sit stand", "chair squat"),
        "chong day tuong" to listOf("wall push up", "wall pushup"),
        "chong day san" to listOf("floor push up", "push up", "pushup"),
        "chong day goi" to listOf("knee push up", "kneeling push up"),
        "plank khuyu hoac plank goi" to listOf("forearm plank", "knee plank", "plank"),
        "nang hong tren san" to listOf("glute bridge", "floor bridge"),
        "cat cow nhe" to listOf("cat cow", "cat camel"),
        "keo ta don hoac day khang luc" to listOf("dumbbell row", "resistance band row", "bent over row"),
        "gap hong voi ta don hoac day" to listOf("dumbbell hip hinge", "resistance band hip hinge", "romanian deadlift"),
        "di bo nhanh tai cho" to listOf("marching in place", "march in place"),
        "dang chan sang ben co diem tua" to listOf("standing hip abduction", "supported side leg raise"),
        "gian co vai" to listOf("gian co vai", "shoulder stretch", "shoulder mobility", "shoulder stretches")
    )

    private val genericTokens = setOf(
        "bai", "tap", "dong", "tac", "voi", "hoac", "tren", "san", "nhe",
        "tai", "nha", "cho", "mot", "cac", "the", "and", "with", "for",
        "exercise", "workout", "tutorial", "how", "beginner", "beginners",
        "proper", "form", "training", "video"
    )

    fun matches(moveName: String, videoTitle: String): Boolean {
        if (moveName.isBlank() || videoTitle.isBlank()) return false
        val title = normalize(videoTitle)
        if (title.isBlank()) return false

        val move = normalize(moveName)
        val aliases = curatedAliases[move]
        if (!aliases.isNullOrEmpty()) {
            return aliases.any { alias -> containsPhrase(title, normalize(alias)) }
        }

        // Unknown catalog entries require two distinctive terms when available.
        // Generic words such as "exercise" or "at home" cannot pass this gate.
        val moveTokens = move.split(' ')
            .filter { it.length >= 3 && it !in genericTokens }
            .toSet()
        if (moveTokens.isEmpty()) return false
        val titleTokens = title.split(' ').toSet()
        val matchedTerms = moveTokens.count { it in titleTokens }
        return matchedTerms >= minOf(2, moveTokens.size)
    }

    private fun containsPhrase(normalizedTitle: String, normalizedPhrase: String): Boolean {
        if (normalizedPhrase.isBlank()) return false
        return " $normalizedTitle ".contains(" $normalizedPhrase ")
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
