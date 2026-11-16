package com.chibialarm.app

/**
 * Short-lived memory for replaying the latest Fitness Coach answer.
 *
 * Deliberately has no Android context, storage, account identifier, microphone access,
 * or network client. The Activity clears it when the screen is destroyed or the
 * user clears the visible conversation.
 */
internal class FitnessCoachSpeechMemory(
    private val maxCharacters: Int = 350
) {
    private var lastReply: String? = null

    init {
        require(maxCharacters in 40..2_000)
    }

    fun remember(reply: String) {
        val normalized = reply.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) {
            lastReply = null
            return
        }
        if (normalized.length <= maxCharacters) {
            lastReply = normalized
            return
        }

        // Prefer a word boundary and reserve one character for the spoken ellipsis.
        val limit = maxCharacters - 1
        val boundary = normalized.lastIndexOf(' ', limit)
        val end = if (boundary >= limit / 2) boundary else limit
        lastReply = normalized.take(end).trimEnd() + "…"
    }

    fun hasReply(): Boolean = lastReply != null

    /** Replay is available only after the user enables voice and the TTS engine is ready. */
    fun replayText(voiceEnabled: Boolean, engineReady: Boolean): String? =
        if (voiceEnabled && engineReady) lastReply else null

    fun clear() {
        lastReply = null
    }
}
