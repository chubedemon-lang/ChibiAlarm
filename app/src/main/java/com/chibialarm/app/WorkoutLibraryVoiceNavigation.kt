package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Maps the shared offline voice grammar to actions that are safe inside the library preview only. */
internal object WorkoutLibraryVoiceNavigation {
    enum class Action {
        FIRST,
        NEXT,
        PREVIOUS,
        REPEAT,
        AUTO_SPEAK_ON,
        AUTO_SPEAK_OFF,
        SPEAK_SUMMARY,
        SPEAK_EASIER,
        SPEAK_SAFETY_NOTE,
        SPEAK_EXERCISE,
        OPEN_ALTERNATIVES,
        SELECT_ALTERNATIVE_1,
        SELECT_ALTERNATIVE_2,
        SELECT_ALTERNATIVE_3,
        SELECT_ALTERNATIVE_4,
        CLOSE,
        SAFETY,
        UNSUPPORTED
    }

    private val negationWords = setOf("khong", "dung", "chua")
    private val actionPhrases = listOf(
        "buoc tiep", "buoc truoc", "buoc dau", "doc lai", "tu doc",
        "doc muc tieu", "doc cach tap", "doc luu y", "phuong an thay the",
        "chon phuong an", "lua chon phuong an", "chon bai", "lua chon bai",
        "phuong an",
        "cach tap", "dong huong dan", "thoat huong dan"
    )

    fun action(utterance: String?): Action {
        val parsed = WorkoutStepVoiceCommand.parse(utterance)
        // Pain/breathing symptoms always outrank navigation, even if the utterance also
        // contains a phrase such as "bước tiếp theo".
        if (parsed == WorkoutStepVoiceCommand.Command.SAFETY) return Action.SAFETY

        val normalized = Normalizer.normalize(
            utterance.orEmpty().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        val firstActionIndex = actionPhrases.map { phrase ->
            normalized.indexOf(phrase).takeIf { it >= 0 }
        }.filterNotNull().minOrNull()
        if (firstActionIndex != null) {
            val prefix = normalized.substring(0, firstActionIndex)
            if (prefix.split(" ").any { it in negationWords }) return Action.UNSUPPORTED
        }

        val selectionIntent = normalized.startsWith("chon phuong an") ||
            normalized.startsWith("lua chon phuong an") ||
            normalized.startsWith("chon bai") ||
            normalized.startsWith("lua chon bai") ||
            normalized == "phuong an" ||
            Regex("^phuong an(?: so| thu)? [0-9].*$").containsMatchIn(normalized)
        val selectedAlternative = listOf(
            Regex("^(?:chon|lua chon) phuong an(?: so| thu)? ([1-4])$"),
            Regex("^(?:chon|lua chon) bai(?: tap)?(?: so| thu)? ([1-4])$"),
            Regex("^phuong an(?: so| thu)? ([1-4])$")
        ).firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(normalized)?.groupValues?.get(1)?.toIntOrNull()
        }
        if (selectionIntent && selectedAlternative == null) return Action.UNSUPPORTED
        if (selectedAlternative != null) {
            return when (selectedAlternative) {
                1 -> Action.SELECT_ALTERNATIVE_1
                2 -> Action.SELECT_ALTERNATIVE_2
                3 -> Action.SELECT_ALTERNATIVE_3
                4 -> Action.SELECT_ALTERNATIVE_4
                else -> Action.UNSUPPORTED
            }
        }

        return when (parsed) {
            WorkoutStepVoiceCommand.Command.FIRST -> Action.FIRST
            WorkoutStepVoiceCommand.Command.NEXT -> Action.NEXT
            WorkoutStepVoiceCommand.Command.PREVIOUS -> Action.PREVIOUS
            WorkoutStepVoiceCommand.Command.REPEAT,
            WorkoutStepVoiceCommand.Command.REPEAT_ANSWER -> Action.REPEAT
            WorkoutStepVoiceCommand.Command.AUTO_SPEAK_ON -> Action.AUTO_SPEAK_ON
            WorkoutStepVoiceCommand.Command.AUTO_SPEAK_OFF -> Action.AUTO_SPEAK_OFF
            WorkoutStepVoiceCommand.Command.SUMMARY -> Action.SPEAK_SUMMARY
            WorkoutStepVoiceCommand.Command.EASIER -> Action.SPEAK_EASIER
            WorkoutStepVoiceCommand.Command.SAFETY_NOTE -> Action.SPEAK_SAFETY_NOTE
            WorkoutStepVoiceCommand.Command.ASK_EXERCISE -> Action.SPEAK_EXERCISE
            WorkoutStepVoiceCommand.Command.OPEN_EASIER_ALTERNATIVES -> Action.OPEN_ALTERNATIVES
            WorkoutStepVoiceCommand.Command.CLOSE -> Action.CLOSE
            else -> Action.UNSUPPORTED
        }
    }
}
