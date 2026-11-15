package com.chibialarm.app

/** Chooses only a Vietnamese TTS voice for the Vietnamese coach UI. */
internal object CoachSpeechVoicePolicy {
    data class VoiceOption(val name: String, val language: String)
    data class Selection(val languageSupported: Boolean, val voiceName: String?)

    /** Android TTS language statuses are non-negative when the requested locale is supported. */
    fun isSupportedLanguageStatus(status: Int): Boolean = status >= 0

    /** Resolve language support and an optional installed Vietnamese voice as one decision. */
    fun resolve(
        languageStatus: Int,
        preferredName: String?,
        available: List<VoiceOption>
    ): Selection {
        val supported = isSupportedLanguageStatus(languageStatus)
        return Selection(
            languageSupported = supported,
            voiceName = if (supported) selectVietnameseVoice(preferredName, available) else null
        )
    }

    /**
     * Keep a saved voice only if it is still installed and speaks Vietnamese.
     * If that voice disappeared after an engine update, choose an installed Vietnamese voice.
     * Never silently select an unrelated language for Vietnamese coaching.
     */
    fun selectVietnameseVoice(
        preferredName: String?,
        available: List<VoiceOption>
    ): String? {
        val vietnamese = available.filter { it.language.equals("vi", ignoreCase = true) }
        return vietnamese.firstOrNull { it.name == preferredName }?.name
            ?: vietnamese.firstOrNull()?.name
    }
}
