package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Contextual, offline voice parser for the post-set perceived-effort check-in.
 *
 * It accepts short, explicit answers and a small set of conversational Vietnamese variants
 * because speech recognition can be wrong. Safety symptoms always win; a report is only
 * stored after a separate confirmation.
 */
object WorkoutEffortVoiceCommand {
    sealed class Result {
        data class Report(val level: WorkoutEffortFeedback.Level) : Result()
        object Confirm : Result()
        object Revise : Result()
        object SafetyStop : Result()
    }

    private val phrases = listOf(
        WorkoutEffortFeedback.Level.EASY to setOf(
            "nhe", "rat nhe", "con du suc", "nhe con du suc", "de dang",
            "rat nhe con du suc"
        ),
        WorkoutEffortFeedback.Level.MODERATE to setOf(
            "vua suc", "binh thuong", "on", "on roi", "tam duoc",
            "met nhung van on", "hoi met nhung van on", "hoi duoi nhung van on"
        ),
        WorkoutEffortFeedback.Level.HARD to setOf(
            "kho", "kho qua", "hoi kho", "kha kho", "kho can nghi them",
            "can nghi them", "met", "hoi met", "met roi", "met qua",
            "kha met", "met lam", "met nhung van lam duoc",
            "hoi met nhung van lam duoc", "met nhung can nghi them",
            "hoi met can nghi them", "kha met can nghi them",
            "kho nhung van lam duoc", "hoi kho nhung van lam duoc",
            "kha kho nhung van lam duoc", "hoi nang nhung van kiem soat duoc",
            "kha nang nhung van kiem soat duoc"
        ),
        WorkoutEffortFeedback.Level.VERY_HARD to setOf(
            "qua suc", "qua suc roi", "het suc", "het suc roi",
            "tam dung", "khong the tiep tuc", "khong the tiep tuc nua",
            "khong chiu noi", "duoi qua", "qua met khong the tiep tuc",
            "met den muc khong the tiep tuc", "qua suc khong the tiep tuc",
            "qua suc roi khong the tiep tuc", "duoi khong the tiep tuc"
        )
    )

    // Confirmation is a separate turn after Chibi reads the recognized effort back.
    // Keep the vocabulary exact so an ambiguous "ừ" or a mixed sentence cannot save a report.
    private val confirmationPhrases = setOf(
        "xac nhan", "dung roi", "chinh xac", "chuan roi",
        "dung nhu vay", "toi dong y", "minh dong y", "dong y", "ok", "oke"
    )

    private val correctionPhrases = setOf(
        "khong dung", "chua dung", "sai roi", "nghe nham",
        "chon lai", "khong chon lai", "de toi noi lai",
        "toi noi lai", "minh noi lai", "noi lai", "khong phai"
    )

    // Strip only a small set of conversational introductions. The remaining answer
    // must still match a complete phrase; arbitrary long sentences stay unclassified.
    private val conversationalPrefixes = listOf(
        "muc do cua toi la ",
        "muc do cua minh la ",
        "hom nay toi cam thay ",
        "hom nay minh cam thay ",
        "hom nay toi thay ",
        "hom nay minh thay ",
        "bai nay toi thay ",
        "bai nay minh thay ",
        "em cam thay ",
        "em thay ",
        "toi cam thay ",
        "minh cam thay ",
        "toi thay ",
        "minh thay ",
        "cam thay ",
        "cam giac ",
        "hiep vua roi ",
        "hiep nay "
    )

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun exactAnswerVariants(normalized: String): Set<String> {
        val variants = linkedSetOf(normalized)
        var current = normalized
        for (ignored in 0 until 2) {
            val prefix = conversationalPrefixes.firstOrNull {
                current.startsWith(it) && current.length > it.length
            } ?: break
            current = current.removePrefix(prefix).trim()
            if (current.isNotBlank()) variants.add(current)
        }
        return variants
    }

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

        val normalized = normalize(utterance)
        if (normalized.isBlank()) return null
        val variants = exactAnswerVariants(normalized)
        if (variants.any(confirmationPhrases::contains)) return Result.Confirm
        if (variants.any(correctionPhrases::contains)) return Result.Revise
        val matches = phrases.filter { (_, accepted) -> variants.any(accepted::contains) }
        if (matches.size != 1) return null
        return Result.Report(matches.single().first)
    }
}
