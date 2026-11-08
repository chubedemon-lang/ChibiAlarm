package com.chibialarm.app

/** Offline catalog instruction parser: preserves explicit steps and avoids empty TTS prompts. */
object WorkoutStepGuide {
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+")
    private val bulletPrefix = Regex("^\\s*(?:[•*\\-]\\s*|\\d{1,2}[.)]\\s*)")
    private const val fallback = "Đọc hướng dẫn trên màn hình và tập theo sức mình."

    fun split(instructions: String): List<String> {
        if (instructions.isBlank()) return listOf(fallback)
        // Explicit newlines and numbered/bulleted items are independent spoken steps.
        // Avoid splitting decimals (e.g. 2.5 kg) or abbreviated words without whitespace.
        val lines = instructions.split(Regex("[\\r\\n]+"))
            .flatMap { line -> line.split(sentenceBoundary) }
            .map { it.replace(bulletPrefix, "").trim() }
            .filter { it.isNotEmpty() }
        return lines.ifEmpty { listOf(fallback) }
    }
}
