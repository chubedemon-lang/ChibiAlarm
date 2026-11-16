package com.chibialarm.app

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
            .filter { seen.add(it.text) }
            .take(alternativeLimit)
            .toList()
    }
}
