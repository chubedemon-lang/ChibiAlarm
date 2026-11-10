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
    
    /**
     * A bounded, in-memory walkthrough for the current exercise. It never changes
     * workout timing or progress; users can move backward and forward at their pace.
     */
    class Walkthrough private constructor(
        val steps: List<String>,
        val index: Int
    ) {
        init {
            require(steps.isNotEmpty())
            require(index in steps.indices)
        }

        val currentStep: String get() = steps[index]
        val position: Int get() = index + 1
        val canGoPrevious: Boolean get() = index > 0
        val canGoNext: Boolean get() = index < steps.lastIndex

        fun previous(): Walkthrough =
            if (canGoPrevious) Walkthrough(steps, index - 1) else this

        fun next(): Walkthrough =
            if (canGoNext) Walkthrough(steps, index + 1) else this

        companion object {
            fun start(instructions: String): Walkthrough =
                Walkthrough(WorkoutStepGuide.split(instructions), 0)
        }
    }
}
