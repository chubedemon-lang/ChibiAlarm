package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Exact, offline grammar for a spoken decision on a mid-session preview. */
internal object WorkoutPlanPreviewVoiceCommand {
    enum class Action { CONFIRM, CANCEL, SAFETY, UNSUPPORTED }

    private val confirmPhrases = setOf(
        "xac nhan ap dung",
        "dong y ap dung",
        "xac nhan dieu chinh",
        "ap dung dieu chinh"
    )
    private val cancelPhrases = setOf(
        "huy",
        "huy thay doi",
        "giu nguyen",
        "khong ap dung",
        "dung ap dung",
        "bo qua thay doi",
        "de sau"
    )

    fun parse(utterance: String?): Action {
        if (utterance.isNullOrBlank() || utterance.length > 160) return Action.UNSUPPORTED
        if (WorkoutVoiceCommands.parse(utterance) == WorkoutVoiceCommands.Command.PAIN) {
            return Action.SAFETY
        }
        val normalized = Normalizer.normalize(
            utterance.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when (normalized) {
            in confirmPhrases -> Action.CONFIRM
            in cancelPhrases -> Action.CANCEL
            else -> Action.UNSUPPORTED
        }
    }

    /**
     * Collapses recognizer alternatives only when every candidate agrees.
     * Any safety signal wins; disagreement or an unrecognized alternative fails closed.
     */
    fun parseAlternatives(utterances: List<String>?): Action {
        if (utterances.isNullOrEmpty()) return Action.UNSUPPORTED
        val actions = utterances.map(::parse)
        if (actions.any { it == Action.SAFETY }) return Action.SAFETY
        if (actions.any { it == Action.UNSUPPORTED }) return Action.UNSUPPORTED
        return actions.distinct().singleOrNull() ?: Action.UNSUPPORTED
    }
}
