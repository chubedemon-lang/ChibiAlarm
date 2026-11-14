package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Builds a conservative next-session preview from a real, self-reported guided workout.
 * It never edits a plan, changes set count/rest/load, or grants XP.
 */
object WorkoutProgressionPreview {
    data class Proposal(
        val exerciseName: String,
        val previousMinimumReps: Int,
        val targetReps: Int,
        val prescribedMinReps: Int,
        val prescribedMaxReps: Int,
        val requiredSets: Int,
        val maximumSets: Int,
        val restSeconds: Int,
        val latestDate: LocalDate
    )

    private data class SetValue(val index: Int, val value: Int, val unit: String)
    private data class MoveValues(val name: String, val sets: List<SetValue>)
    private data class EffortValue(val index: Int, val level: WorkoutEffortFeedback.Level)
    private data class MoveEffort(val name: String, val sets: List<EffortValue>)

    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 100
    private const val MAX_SETS = 20

    private val setPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(\d{1,3})\s*(lần|nhịp|giây)\b""",
        RegexOption.IGNORE_CASE
    )
    private val effortPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(Nhẹ,\s*còn dư sức|Vừa sức|Khó,\s*cần nghỉ thêm|Quá sức,\s*tạm dừng)""",
        RegexOption.IGNORE_CASE
    )

    fun propose(
        suggestion: WorkoutSteadyProgressAdvisor.Suggestion,
        workouts: List<Workout>,
        plannedMoves: List<TrainingMove>,
        today: LocalDate = LocalDate.now()
    ): Proposal? {
        if (suggestion.consistentDays < 3 || suggestion.exerciseName.isBlank() ||
            plannedMoves.isEmpty() || plannedMoves.size > 500
        ) return null
        val age = ChronoUnit.DAYS.between(suggestion.latestDate, today)
        if (age !in 0L..30L) return null

        // Re-check the evidence at the point of preview so stale or fabricated UI state
        // cannot turn an old suggestion into a new target.
        val verified = WorkoutSteadyProgressAdvisor.suggest(workouts, today) ?: return null
        if (normalize(verified.exerciseName) != normalize(suggestion.exerciseName) ||
            verified.latestDate != suggestion.latestDate || verified.consistentDays < 3
        ) return null

        val exerciseKey = normalize(suggestion.exerciseName)
        val matchingIndices = plannedMoves.indices.filter {
            normalize(plannedMoves[it].name) == exerciseKey
        }
        if (matchingIndices.size != 1) return null
        val move = plannedMoves[matchingIndices.single()]
        val plan = WorkoutSetTracker.parse(move.dose, move.restSeconds) ?: return null
        if (plan.unit != "lần" || plan.targetMax <= plan.targetMin || plan.targetMax > 100) return null

        // Use the latest session on the evidence date that contains a check-in for this move.
        // If that session has incomplete or inconsistent rep data, do not fall back to an older one.
        val latestIndex = workouts.indices.lastOrNull { index ->
            val workout = workouts[index]
            runCatching { LocalDate.parse(workout.date) }.getOrNull() == suggestion.latestDate &&
                isValidGuidedSession(workout) &&
                parseEffortLog(workout.effortLog)?.any { normalize(it.name) == exerciseKey } == true
        } ?: return null
        val latest = workouts[latestIndex]
        val setEntries = parseSetLog(latest.setLog) ?: return null
        val effortEntries = parseEffortLog(latest.effortLog) ?: return null
        if (setEntries.sumOf { it.sets.size } != latest.selfReportedSets) return null

        val matchingSetMoves = setEntries.filter { normalize(it.name) == exerciseKey }
        val matchingEffortMoves = effortEntries.filter { normalize(it.name) == exerciseKey }
        if (matchingSetMoves.size != 1 || matchingEffortMoves.size != 1) return null

        val values = matchingSetMoves.single().sets
        val efforts = matchingEffortMoves.single().sets
        if (values.size !in plan.requiredSets..plan.maximumSets ||
            values.map { it.index }.sorted() != efforts.map { it.index }.sorted() ||
            values.any { it.unit != "lần" || it.value !in plan.targetMin..plan.targetMax } ||
            efforts.any {
                it.level != WorkoutEffortFeedback.Level.EASY &&
                    it.level != WorkoutEffortFeedback.Level.MODERATE
            }
        ) return null

        // The guided screen applies one exact rep target to every set. Only offer it
        // when the latest self-reported sets used the same count; never lower a stronger set.
        val distinctRepCounts = values.map { it.value }.distinct()
        if (distinctRepCounts.size != 1) return null
        val previousMinimum = distinctRepCounts.single()
        val target = previousMinimum + 1
        if (target <= previousMinimum || target > plan.targetMax) return null

        return Proposal(
            exerciseName = move.name,
            previousMinimumReps = previousMinimum,
            targetReps = target,
            prescribedMinReps = plan.targetMin,
            prescribedMaxReps = plan.targetMax,
            requiredSets = plan.requiredSets,
            maximumSets = plan.maximumSets,
            restSeconds = plan.defaultRestSeconds,
            latestDate = suggestion.latestDate
        )
    }

    private fun isValidGuidedSession(workout: Workout): Boolean {
        val validId = runCatching {
            UUID.fromString(workout.sessionId).toString() == workout.sessionId
        }.getOrDefault(false)
        return validId && workout.minutes in 1..600 &&
            workout.completedMoves in 2..500 &&
            workout.selfReportedSets in 1..2_000 &&
            workout.setLog.isNotBlank() && workout.setLog.length <= MAX_LOG_CHARS &&
            workout.effortLog.isNotBlank() && workout.effortLog.length <= MAX_LOG_CHARS
    }

    private fun parseSetLog(raw: String): List<MoveValues>? {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return null
        val segments = raw.split(';')
        if (segments.isEmpty() || segments.size > MAX_MOVES) return null
        val result = mutableListOf<MoveValues>()
        for (segment in segments) {
            val separator = segment.indexOf(':')
            if (separator !in 1..90) return null
            val name = segment.substring(0, separator).trim().take(90)
            if (name.isBlank()) return null
            val matches = setPattern.findAll(segment.substring(separator + 1)).toList()
            if (matches.isEmpty() || matches.size > MAX_SETS) return null
            val values = matches.map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: return null
                val value = match.groupValues[2].toIntOrNull() ?: return null
                val unit = match.groupValues[3].lowercase(Locale.ROOT)
                if (index !in 1..MAX_SETS || value !in 1..200 ||
                    unit !in setOf("lần", "nhịp", "giây")
                ) return null
                SetValue(index, value, unit)
            }
            val indices = values.map { it.index }
            if (indices.distinct().size != indices.size ||
                indices.sorted() != (1..values.size).toList()
            ) return null
            result += MoveValues(name, values)
        }
        return result.ifEmpty { null }
    }

    private fun parseEffortLog(raw: String): List<MoveEffort>? {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return null
        val segments = raw.split(';')
        if (segments.isEmpty() || segments.size > MAX_MOVES) return null
        val result = mutableListOf<MoveEffort>()
        for (segment in segments) {
            val separator = segment.indexOf(':')
            if (separator !in 1..90) return null
            val name = segment.substring(0, separator).trim().take(90)
            if (name.isBlank()) return null
            val matches = effortPattern.findAll(segment.substring(separator + 1)).toList()
            if (matches.isEmpty() || matches.size > MAX_SETS) return null
            val values = matches.map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: return null
                if (index !in 1..MAX_SETS) return null
                val label = match.groupValues[2].replace(Regex("\\s+"), " ").trim()
                val level = WorkoutEffortFeedback.Level.values().firstOrNull {
                    it.label.equals(label, ignoreCase = true)
                } ?: return null
                EffortValue(index, level)
            }
            val indices = values.map { it.index }
            if (indices.distinct().size != indices.size ||
                indices.sorted() != (1..values.size).toList()
            ) return null
            result += MoveEffort(name, values)
        }
        return result.ifEmpty { null }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
