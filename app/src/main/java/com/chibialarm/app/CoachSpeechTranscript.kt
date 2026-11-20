package com.chibialarm.app

import java.util.Locale

/**
 * Bounded, reviewable alternatives returned by Android's external speech recognizer.
 * Raw audio is never accepted or stored here; candidates remain in the Activity until sent.
 */
object CoachSpeechTranscript {
    const val MAX_CHARS = 900
    const val MAX_ALTERNATIVES = 5

    data class Candidate(
        val text: String,
        val truncated: Boolean
    ) {
        val label: String
            get() = buildString {
                append(text.take(120))
                if (text.length > 120) append("…")
                if (truncated) append(" (đã rút gọn)")
            }
    }

    fun candidates(
        raw: List<String>?,
        maxChars: Int = MAX_CHARS,
        maxAlternatives: Int = MAX_ALTERNATIVES
    ): List<Candidate> {
        val characterLimit = maxChars.coerceIn(1, MAX_CHARS)
        val alternativeLimit = maxAlternatives.coerceIn(1, MAX_ALTERNATIVES)
        val seen = linkedSetOf<String>()

        return raw.orEmpty().asSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .map { normalized ->
                Candidate(
                    text = normalized.take(characterLimit),
                    truncated = normalized.length > characterLimit
                )
            }
            // Recognizers may vary only capitalization between alternatives. Keep the
            // first display form, but avoid presenting the same spoken sentence twice.
            .filter { seen.add(it.text.lowercase(Locale.ROOT)) }
            .take(alternativeLimit)
            .toList()
    }
}

/**
 * A spoken command is actionable only after its length and safety priority are reviewed.
 * Long/truncated recognizer output must be edited before a non-safety action can run.
 */
internal object WorkoutVoiceCandidatePolicy {
    const val MAX_COMMAND_CHARS = 160

    data class Review(
        val command: WorkoutVoiceCommands.Command,
        val requiresEdit: Boolean
    )

    fun review(candidate: CoachSpeechTranscript.Candidate): Review {
        val text = candidate.text.trim()
        return Review(
            command = WorkoutVoiceCommands.parse(text.take(MAX_COMMAND_CHARS)),
            requiresEdit = candidate.truncated || text.length > MAX_COMMAND_CHARS
        )
    }
}
