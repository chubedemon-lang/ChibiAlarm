package com.chibialarm.app.fitness

import java.util.Locale
import com.chibialarm.app.WorkoutVoiceCommands

/**
 * Deterministic Vietnamese offline coach commands for an explicitly selected exercise.
 * This parser only proposes a change; the UI must show a preview and call
 * WorkoutTargetEdit.accept after explicit confirmation.
 */
object WorkoutTargetCommand {
    sealed class Result {
        data class Proposed(val preview: WorkoutTargetEdit.Preview) : Result()
        object Unsupported : Result()
        object SafetyStop : Result()
    }

    private val painWords = Regex("""\b(đau|nhức|chấn thương|choáng|chóng mặt|khó thở)\b""")
    // Recognize common STT forms, including compound numbers ("hai mươi lăm")
    // and mixed units ("một phút ba mươi giây"), without a network dependency.
    private val numeral = """(?:\d{1,3}|một|hai|ba|bốn|tư|năm|lăm|sáu|bảy|tám|chín|mười|mươi|linh|lẻ|trăm)(?:\s+(?:\d{1,3}|một|hai|ba|bốn|tư|năm|lăm|sáu|bảy|tám|chín|mười|mươi|linh|lẻ|trăm))*"""
    private val repsPattern = Regex("""(?:\b(?:còn|xuống|thành|giảm xuống|tăng lên|đổi thành)\s*)?($numeral)\s*(?:lần|cái|reps?)\b""")
    private val restPattern = Regex("""(?:nghỉ|thời gian nghỉ)\s*(?:thêm|lên|thành|còn)?\s*($numeral)\s*(giây|phút)\b""")
    private val secondsAfterMinutes = Regex("""^\s*($numeral)\s*giây\b""")
    private val units = mapOf("một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4,
        "tư" to 4, "năm" to 5, "lăm" to 5, "sáu" to 6, "bảy" to 7,
        "tám" to 8, "chín" to 9)
    private fun number(value: String?): Int? {
        if (value == null) return null
        value.toIntOrNull()?.let { return it }
        val words = value.trim().split(Regex("""\s+"""))
        if (words.size == 1) return if (words[0] == "mười") 10 else units[words[0]]
        // Supported speech numbers: 11–99. Reject ambiguous/partial phrases.
        val tens = when (words[0]) {
            "mười" -> 10
            else -> units[words[0]]?.times(10)?.takeIf { words.getOrNull(1) == "mươi" }
        } ?: return null
        val remainder = if (words[0] == "mười") words.drop(1) else words.drop(2)
        if (remainder.size > 1) return null
        val unit = if (remainder.isEmpty()) 0 else units[remainder[0]] ?: return null
        return tens + unit
    }

    /** Navigation, pause and safety intents must never be hidden by a target edit. */
    private fun hasCompetingControlIntent(text: String): Boolean = when (WorkoutVoiceCommands.parse(text)) {
        WorkoutVoiceCommands.Command.PAIN,
        WorkoutVoiceCommands.Command.PAUSE,
        // A read-only progress request must be handled by the coach, never by a target editor.
        WorkoutVoiceCommands.Command.STATUS,
        WorkoutVoiceCommands.Command.RESUME,
        WorkoutVoiceCommands.Command.NEXT,
        WorkoutVoiceCommands.Command.PREVIOUS,
        WorkoutVoiceCommands.Command.REPEAT,
        // Repeating a selected walkthrough step is a coach action, never a rep edit.
        WorkoutVoiceCommands.Command.REPEAT_STEP,
        WorkoutVoiceCommands.Command.EASIER,
        WorkoutVoiceCommands.Command.SWAP_EASIER,
        WorkoutVoiceCommands.Command.EASIER_AND_REST,
        WorkoutVoiceCommands.Command.SHORTER,
        WorkoutVoiceCommands.Command.SHORTER_REMAINING,
        // Opening a walkthrough is a separate coach action, never a repetition edit.
        WorkoutVoiceCommands.Command.STEP_GUIDE,
        // Set completion is a state-changing workout action, never a repetition edit.
        WorkoutVoiceCommands.Command.COMPLETE_SET,
        // Equipment changes are plan-level actions and must not be parsed as rep edits.
        WorkoutVoiceCommands.Command.NO_EQUIPMENT -> true
        WorkoutVoiceCommands.Command.REST,
        WorkoutVoiceCommands.Command.UNKNOWN -> false
    }

    /** Parse a spoken repetition target without changing the workout. */
    fun spokenReps(spokenText: String): Int? {
        val normalized = spokenText.lowercase(Locale.ROOT).trim()
        if (painWords.containsMatchIn(normalized) || hasCompetingControlIntent(normalized)) return null
        return number(repsPattern.find(normalized)?.groupValues?.get(1))?.takeIf { it in 1..100 }
    }

    fun propose(
        spokenText: String,
        selectedExerciseId: String,
        session: CoachSessionPlanner.Session,
        completedSets: Int
    ): Result {
        val normalized = spokenText.lowercase(Locale.ROOT).trim()
        if (painWords.containsMatchIn(normalized) || session.pausedForSafety ||
            WorkoutVoiceCommands.parse(normalized) == WorkoutVoiceCommands.Command.PAIN) return Result.SafetyStop
        if (hasCompetingControlIntent(normalized)) return Result.Unsupported
        if (selectedExerciseId.isBlank() || completedSets !in 0..session.sets.size) return Result.Unsupported
        if (session.sets.drop(completedSets).none { it.exerciseId == selectedExerciseId }) return Result.Unsupported

        val reps = number(repsPattern.find(normalized)?.groupValues?.get(1))
        val restMatch = restPattern.find(normalized)
        val rawRest = number(restMatch?.groupValues?.get(1))
        val rest = if (rawRest != null) {
            if (restMatch?.groupValues?.get(2) == "phút") {
                val tail = normalized.substring(restMatch.range.last + 1)
                val secondsMatch = secondsAfterMinutes.find(tail)
                val extra = if (secondsMatch != null) number(secondsMatch.groupValues[1]) else 0
                if (extra == null || extra !in 0..59) return Result.Unsupported
                rawRest * 60 + extra
            } else rawRest
        } else null
        if (reps == null && rest == null) return Result.Unsupported
        if (reps != null && reps !in 1..100) return Result.Unsupported
        if (rest != null && rest !in 0..600) return Result.Unsupported

        val target = WorkoutTargetEdit.Target(selectedExerciseId, reps, rest)
        val preview = WorkoutTargetEdit.preview(session, completedSets, target)
        return if (preview.changed) Result.Proposed(preview) else Result.Unsupported
    }
}
