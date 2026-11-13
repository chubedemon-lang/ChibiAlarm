package com.chibialarm.app

/** Offline catalog instruction parser: preserves explicit steps and avoids empty TTS prompts. */
object WorkoutStepGuide {
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+")
    private val semicolonBoundary = Regex("\\s*;\\s*")
    private val bulletPrefix = Regex("^\\s*(?:[•*\\-]\\s*|\\d{1,2}[.)]\\s*)")
    private const val fallback = "Đọc hướng dẫn trên màn hình và tập theo sức mình."

    fun split(instructions: String): List<String> {
        if (instructions.isBlank()) return listOf(fallback)
        // Newlines, sentences and semicolons separate spoken cues. Semicolons also
        // support compact inline lists such as "1) Stand; 2) Lower; 3) Breathe".
        // Sentence splitting still requires whitespace, so decimal weights remain intact.
        val lines = instructions.split(Regex("[\\r\\n]+"))
            .flatMap { line -> line.split(sentenceBoundary).flatMap { sentence ->
                sentence.split(semicolonBoundary)
            } }
            .map { it.replace(bulletPrefix, "").trim() }
            .filter { it.isNotEmpty() }
        return lines.ifEmpty { listOf(fallback) }
    }
    
    /**
     * Build the exact spoken cue for one selected catalog step. Position is 1-based;
     * the cue includes the exercise-specific phase only when the wording is unambiguous.
     */
    fun spokenCue(moveName: String, instructions: String, position: Int): String {
        val steps = split(instructions).take(100)
        val selected = position.coerceIn(1, steps.size)
        val pose = WorkoutPoseGuide.classify(moveName)
        return WorkoutPoseMotion.spokenInstructionStepCue(
            pose = pose,
            step = steps[selected - 1],
            position = selected,
            total = steps.size
        )
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
