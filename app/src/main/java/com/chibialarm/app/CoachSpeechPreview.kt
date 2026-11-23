package com.chibialarm.app

/**
 * Consent gate for using the currently selected system TTS voice.
 * A voice that requires a network, or whose network behavior cannot be inspected,
 * must never preview or auto-read until the user explicitly confirms that use.
 */
internal object CoachSpeechPreview {
    enum class Decision {
        UNAVAILABLE,
        PLAY_WITHOUT_NETWORK_PROMPT,
        REQUIRE_NETWORK_CONSENT,
        REQUIRE_UNKNOWN_NETWORK_CONSENT
    }

    const val SAMPLE_TEXT =
        "Chào bạn! Mình là Chibi. Hãy tập vừa sức, thở đều và nghỉ khi bạn cần."

    fun decision(languageSupported: Boolean, networkRequired: Boolean?): Decision = when {
        !languageSupported -> Decision.UNAVAILABLE
        networkRequired == true -> Decision.REQUIRE_NETWORK_CONSENT
        networkRequired == null -> Decision.REQUIRE_UNKNOWN_NETWORK_CONSENT
        else -> Decision.PLAY_WITHOUT_NETWORK_PROMPT
    }

    /** Auto-read uses the same network-consent gate as a one-off voice preview. */
    fun autoReadDecision(languageSupported: Boolean, networkRequired: Boolean?): Decision =
        decision(languageSupported, networkRequired)
}
