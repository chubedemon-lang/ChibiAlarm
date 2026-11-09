package com.chibialarm.app.fitness

import java.util.Locale

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
    private val repsPattern = Regex("""(?:\b(?:còn|xuống|thành|giảm xuống|tăng lên|đổi thành)\s*)?(\d{1,3})\s*(?:lần|cái|reps?)\b""")
    private val restPattern = Regex("""(?:nghỉ|thời gian nghỉ)\s*(?:thêm|lên|thành|còn)?\s*(\d{1,3})\s*(giây|phút)\b""")

    fun propose(
        spokenText: String,
        selectedExerciseId: String,
        session: CoachSessionPlanner.Session,
        completedSets: Int
    ): Result {
        val normalized = spokenText.lowercase(Locale.ROOT).trim()
        if (painWords.containsMatchIn(normalized) || session.pausedForSafety) return Result.SafetyStop
        if (selectedExerciseId.isBlank() || completedSets !in 0..session.sets.size) return Result.Unsupported
        if (session.sets.drop(completedSets).none { it.exerciseId == selectedExerciseId }) return Result.Unsupported

        val reps = repsPattern.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val restMatch = restPattern.find(normalized)
        val rawRest = restMatch?.groupValues?.get(1)?.toIntOrNull()
        val rest = if (rawRest != null) {
            if (restMatch?.groupValues?.get(2) == "phút") rawRest * 60 else rawRest
        } else null
        if (reps == null && rest == null) return Result.Unsupported
        if (reps != null && reps !in 1..100) return Result.Unsupported
        if (rest != null && rest !in 0..600) return Result.Unsupported

        val target = WorkoutTargetEdit.Target(selectedExerciseId, reps, rest)
        val preview = WorkoutTargetEdit.preview(session, completedSets, target)
        return if (preview.changed) Result.Proposed(preview) else Result.Unsupported
    }
}
