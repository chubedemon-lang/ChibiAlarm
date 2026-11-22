package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Answers one explicitly numbered catalog instruction step.
 *
 * It never guesses what "the next step" means because the live walkthrough owns that index.
 * The returned text is suitable for the existing Coach TTS path and never changes workout state.
 */
internal object WorkoutExerciseStepQuestion {
    private const val MAX_INPUT_CHARS = 160
    private const val MAX_STEPS = 100
    private const val MAX_STEP_CHARS = 180

    private val numericStep = Regex(
        """(?:^|\s)(?:buoc|step)\s*(?:(?:thu|so)\s*)?(\d{1,2})(?:\s|$)"""
    )
    private val ordinalStep = Regex(
        """(?:^|\s)(?:buoc|step)\s+(?:thu\s+)?(nhat|nhi|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)(?:\s|$)"""
    )
    private val ordinalValues = mapOf(
        "nhat" to 1, "mot" to 1, "nhi" to 2, "hai" to 2, "ba" to 3,
        "bon" to 4, "tu" to 4, "nam" to 5, "sau" to 6, "bay" to 7,
        "tam" to 8, "chin" to 9, "muoi" to 10
    )

    fun hasExplicitStepReference(input: String): Boolean {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return false
        val query = normalize(input)
        return numericStep.containsMatchIn(query) ||
            ordinalStep.containsMatchIn(query) ||
            listOf("buoc dau", "buoc cuoi", "first step", "last step").any(query::contains)
    }

    /** True for a spoken question that asks for one explicit catalog step, not navigation. */
    fun isQuestion(input: String): Boolean {
        if (!hasExplicitStepReference(input)) return false
        val query = normalize(input)
        return listOf(
            "la gi", "la sao", "the nao", "nhu the nao", "doc", "noi",
            "giai thich", "huong dan", "what is", "which step", "explain", "read"
        ).any { containsPhrase(query, it) }
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(^|\\\\s)" + Regex.escape(phrase) + "(\\\\s|$)").containsMatchIn(text)

    fun answer(input: String, moveName: String, instructions: String): String? {
        if (!hasExplicitStepReference(input)) return null

        val query = normalize(input)
        val steps = WorkoutStepGuide.split(instructions).take(MAX_STEPS)
        val requested = buildList {
            if (listOf("buoc dau", "first step").any(query::contains)) add(1)
            if (listOf("buoc cuoi", "last step").any(query::contains)) add(steps.size)
            numericStep.findAll(query).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let(::add)
            }
            ordinalStep.findAll(query).forEach { match ->
                ordinalValues[match.groupValues[1]]?.let(::add)
            }
        }.distinct()

        val safeName = safe(moveName, 80).ifBlank { "Động tác này" }
        if (requested.size != 1) {
            return "Chibi thấy nhiều bước được nhắc tới cho " + safeName +
                ". Hãy hỏi một bước cụ thể, ví dụ “bước 2”."
        }

        val position = requested.single()
        if (position !in 1..steps.size) {
            return safeName + " có " + steps.size +
                " bước hướng dẫn được hỗ trợ; không có bước " + position +
                ". Chibi không tự đổi sang bước khác."
        }

        val step = safe(steps[position - 1], MAX_STEP_CHARS)
        return "Bước " + position + "/" + steps.size + " của " + safeName + ": " + step +
            ". Đây là hướng dẫn chữ từ thư viện; hình minh họa không đánh giá tư thế thực tế."
    }

    private fun safe(value: String, limit: Int): String = value
        .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit)

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
