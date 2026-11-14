package com.chibialarm.app

/**
 * Short-lived presentation history for the on-device Fitness Coach.
 *
 * This object is deliberately memory-only: it has no serializer, preferences,
 * database, network, or account identifier. Structured coach choices are kept
 * separately by FitnessCoachSessionSnapshot; raw user wording disappears when
 * the Activity is recreated or destroyed.
 */
internal class FitnessCoachChatTranscript(
    private val maxMessages: Int = 12,
    private val maxCharactersPerMessage: Int = 320
) {
    enum class Speaker { USER, CHIBI }

    data class Message(val speaker: Speaker, val text: String)

    private val entries = mutableListOf<Message>()

    init {
        require(maxMessages in 2..40)
        require(maxCharactersPerMessage in 32..1000)
    }

    fun addUser(text: String) = append(Speaker.USER, text)

    fun addChibi(text: String) = append(Speaker.CHIBI, text)

    fun messages(): List<Message> = entries.toList()

    fun render(): String = entries.joinToString("\n\n") { message ->
        val label = if (message.speaker == Speaker.USER) "Bạn" else "🐰 Chibi"
        "$label: ${message.text}"
    }

    fun clear() {
        entries.clear()
    }

    private fun append(speaker: Speaker, rawText: String) {
        val text = rawText.trim().take(maxCharactersPerMessage)
        if (text.isEmpty()) return
        entries += Message(speaker, text)
        while (entries.size > maxMessages) entries.removeAt(0)
    }
}
