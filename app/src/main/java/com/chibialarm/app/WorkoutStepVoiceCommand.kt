package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small offline command grammar for the step-by-step guide; no transcript is retained. */
object WorkoutStepVoiceCommand {
    enum class Command { NEXT, PREVIOUS, REPEAT, REST, CLOSE, SAFETY, UNKNOWN }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(phrase) + "(\\s|$)").containsMatchIn(text)

    fun parse(utterance: String?): Command {
        if (utterance.isNullOrBlank()) return Command.UNKNOWN
        val raw = utterance.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        // Check the accented and plain-ASCII forms before removing diacritics so
        // "đầu gối" is not mistaken for "đau" (pain).
        if (containsPhrase(raw, "đau") || containsPhrase(raw, "dau")) return Command.SAFETY

        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (listOf("kho tho", "choang", "chong mat", "tuc nguc", "kho chiu")
                .any { containsPhrase(normalized, it) }) return Command.SAFETY

        if (listOf("khong nghi them", "dung nghi them", "khong them thoi gian nghi")
                .any { normalized.contains(it) }) return Command.UNKNOWN
        if (listOf("nghi them", "them thoi gian nghi", "keo dai thoi gian nghi", "rest more", "extend rest")
                .any { normalized.contains(it) }) return Command.REST
        if (listOf("buoc tiep", "tiep theo", "buoc sau", "sang buoc", "next step")
                .any { normalized.contains(it) }) return Command.NEXT
        if (listOf("buoc truoc", "quay lai", "lui lai", "previous step")
                .any { normalized.contains(it) }) return Command.PREVIOUS
        if (listOf("doc lai", "nghe lai", "lap lai", "nhac lai", "repeat step")
                .any { normalized.contains(it) }) return Command.REPEAT
        if (listOf("dong huong dan", "thoat huong dan", "dung huong dan", "close guide")
                .any { normalized.contains(it) }) return Command.CLOSE
        return Command.UNKNOWN
    }
}
