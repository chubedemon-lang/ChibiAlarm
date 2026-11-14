package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Contextual, offline voice parser for the post-set perceived-effort check-in.
 *
 * It accepts only short, explicit answers because speech recognition can be wrong.
 * Safety symptoms always win; a report is only stored after a separate confirmation.
 */
object WorkoutEffortVoiceCommand {
    sealed class Result {
        data class Report(val level: WorkoutEffortFeedback.Level) : Result()
        object SafetyStop : Result()
    }

    private val phrases = listOf(
        WorkoutEffortFeedback.Level.EASY to setOf(
            "nhe", "rat nhe", "con du suc", "nhe con du suc", "de dang"
        ),
        WorkoutEffortFeedback.Level.MODERATE to setOf(
            "vua suc", "binh thuong", "on", "on roi", "tam duoc"
        ),
        WorkoutEffortFeedback.Level.HARD to setOf(
            "kho", "kho qua", "hoi kho", "kha kho", "kho can nghi them",
            "can nghi them", "met", "hoi met", "met roi", "met qua"
        ),
        WorkoutEffortFeedback.Level.VERY_HARD to setOf(
            "qua suc", "qua suc roi", "het suc", "het suc roi",
            "tam dung", "khong the tiep tuc", "khong chiu noi", "duoi qua"
        )
    )

    fun parse(utterance: String): Result? {
        if (utterance.length !in 1..160) return null
        // The word "đầu" in "đầu gối" names a body part; do not mistake it for "đau".
        val safetyProbe = utterance.replace(
            Regex("(?<![\\p{L}\\p{N}])đầu gối(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "khớp gối"
        )
        if (WorkoutVoiceCommands.parse(safetyProbe) == WorkoutVoiceCommands.Command.PAIN) {
            return Result.SafetyStop
        }

        val normalized = Normalizer.normalize(
            utterance.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return null

        val matches = phrases.filter { normalized in it.second }
        if (matches.size != 1) return null
        return Result.Report(matches.single().first)
    }
}
