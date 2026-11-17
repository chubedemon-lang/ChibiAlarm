package com.chibialarm.app.fitness

import java.util.Locale
import com.chibialarm.app.WorkoutVoiceCommands

/**
 * Deterministic Vietnamese offline coach commands for an explicitly selected exercise.
 * This parser only proposes a change; the UI must show a preview and call
 * WorkoutTargetEdit.accept after explicit confirmation.
 */
object WorkoutTargetCommand {
    data class SpokenTarget(val value: Int, val unit: String)

    sealed class Result {
        data class Proposed(val preview: WorkoutTargetEdit.Preview) : Result()
        object Unsupported : Result()
        object SafetyStop : Result()
    }

    private val painWords = Regex("""\b(đau|nhức|chấn thương|choáng|chóng mặt|khó thở)\b""")
    // Recognize common STT forms, including compound numbers ("hai mươi lăm")
    // and mixed units ("một phút ba mươi giây"), without a network dependency.
    private val numeral = """(?:\d{1,3}|một|hai|ba|bốn|tư|năm|lăm|mốt|sáu|bảy|tám|chín|mười|mươi|linh|lẻ|trăm)(?:\s+(?:\d{1,3}|một|hai|ba|bốn|tư|năm|lăm|mốt|sáu|bảy|tám|chín|mười|mươi|linh|lẻ|trăm))*"""
    private val repsPattern = Regex("""(?:\b(?:còn|xuống|thành|giảm xuống|tăng lên|đổi thành)\s*)?($numeral)\s*(?:lần|cái|reps?)\b""")
    private val spokenTargetPattern = Regex(
        """(?:(còn|xuống|thành|giảm xuống|tăng lên|đổi thành)\s*)?($numeral)\s*(lần|cái|reps?|nhịp|giây)\b"""
    )
    private val restPattern = Regex("""(?:nghỉ|thời gian nghỉ)\s*(?:thêm|lên|thành|còn)?\s*($numeral)\s*(giây|phút)\b""")
    private val secondsAfterMinutes = Regex("""^\s*($numeral)\s*giây\b""")
    private val units = mapOf(
        "một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "tư" to 4,
        "năm" to 5, "lăm" to 5, "mốt" to 1, "sáu" to 6, "bảy" to 7,
        "tám" to 8, "chín" to 9
    )

    /** Parse a conventional Vietnamese tens-and-units tail (10–99). */
    private fun parseTens(words: List<String>): Int? {
        if (words.isEmpty()) return 0
        if (words[0] == "mười") {
            if (words.size > 2) return null
            val unit = if (words.size == 1) 0 else units[words[1]] ?: return null
            return 10 + unit
        }
        if (words.size !in 2..3 || words[1] != "mươi") return null
        val tens = units[words[0]]?.takeIf { it in 2..9 } ?: return null
        val unit = if (words.size == 2) 0 else units[words[2]] ?: return null
        return tens * 10 + unit
    }

    /**
     * Parse digits or spoken Vietnamese integers up to 999.
     *
     * Hundreds require an explicit "linh/lẻ" before a single-digit remainder
     * ("một trăm linh năm"), avoiding guesses for ambiguous phrases such as
     * "một trăm năm". Callers still enforce exercise-specific limits.
     */
    private fun number(value: String?): Int? {
        if (value == null) return null
        value.trim().toIntOrNull()?.let { return it }
        val words = value.trim().lowercase(Locale.ROOT).split(Regex("""\s+"""))
        if (words.isEmpty() || words.any { it.isBlank() }) return null

        val hundredIndex = words.indexOf("trăm")
        if (hundredIndex >= 0) {
            if (hundredIndex != 1) return null
            val hundreds = when (words[0]) {
                "một" -> 1
                "hai" -> 2
                "ba" -> 3
                "bốn" -> 4
                "năm" -> 5
                "sáu" -> 6
                "bảy" -> 7
                "tám" -> 8
                "chín" -> 9
                else -> return null
            }
            val remainderWords = words.drop(2)
            val remainder = when (remainderWords.firstOrNull()) {
                null -> 0
                "linh", "lẻ" -> {
                    if (remainderWords.size != 2) return null
                    units[remainderWords[1]] ?: return null
                }
                else -> parseTens(remainderWords) ?: return null
            }
            return hundreds * 100 + remainder
        }

        if (words.size == 1) {
            if (words[0] == "mười") return 10
            return units[words[0]]
        }
        return parseTens(words)
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
        WorkoutVoiceCommands.Command.EXPLAIN_CURRENT,
        // Set completion is a state-changing workout action, never a repetition edit.
        WorkoutVoiceCommands.Command.COMPLETE_SET,
        // Equipment changes and rest skipping are plan/session actions, never rep edits.
        WorkoutVoiceCommands.Command.NO_EQUIPMENT,
        WorkoutVoiceCommands.Command.SKIP_REST -> true
        WorkoutVoiceCommands.Command.REST,
        WorkoutVoiceCommands.Command.UNKNOWN -> false
    }

    /**
     * Parse a spoken target using the selected exercise's unit. A bare rest duration
     * must never become a Plank target; time targets require an explicit change marker.
     */
    fun spokenTarget(spokenText: String): SpokenTarget? {
        val normalized = spokenText.lowercase(Locale.ROOT).trim()
        if (painWords.containsMatchIn(normalized) || hasCompetingControlIntent(normalized)) return null
        val matches = spokenTargetPattern.findAll(normalized).toList()
        val match = matches.firstOrNull { it.groupValues[1].isNotBlank() }
            ?: matches.firstOrNull()
            ?: return null
        val rawUnit = match.groupValues[3]
        val unit = when (rawUnit) {
            "lần", "cái", "rep", "reps" -> "lần"
            "nhịp" -> "nhịp"
            "giây" -> "giây"
            else -> return null
        }
        if (match.groupValues[1].isBlank() &&
            (unit == "giây" || WorkoutVoiceCommands.parse(normalized) == WorkoutVoiceCommands.Command.REST)
        ) return null
        val value = number(match.groupValues[2])?.takeIf { it in 1..100 } ?: return null
        return SpokenTarget(value, unit)
    }

    /** Backwards-compatible rep-only parser for callers that explicitly need repetitions. */
    fun spokenReps(spokenText: String): Int? =
        spokenTarget(spokenText)?.takeIf { it.unit == "lần" }?.value

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
