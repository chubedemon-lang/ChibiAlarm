package com.chibialarm.app

/**
 * Splits a coach answer into bounded Android TTS utterances without dropping words.
 * Sentence boundaries are preferred; otherwise the split happens at a whitespace boundary.
 */
internal object WorkoutSpeechText {
    const val DEFAULT_MAX_CHARS = 280

    fun chunks(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        require(maxChars in 40..1_000) { "maxChars must be between 40 and 1000" }

        val normalized = text
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var remaining = normalized
        while (remaining.length > maxChars) {
            val lowerBound = maxChars / 2
            val sentenceBoundary = (lowerBound until maxChars).lastOrNull { position ->
                remaining[position] in ".!?;:" &&
                    (position == remaining.lastIndex || remaining[position + 1].isWhitespace())
            }?.plus(1)
            val wordBoundary = remaining.lastIndexOf(' ', maxChars)
                .takeIf { it >= lowerBound }
            val boundary = sentenceBoundary ?: wordBoundary ?: maxChars

            val chunk = remaining.substring(0, boundary).trim()
            if (chunk.isNotEmpty()) result += chunk
            remaining = remaining.substring(boundary).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
}
