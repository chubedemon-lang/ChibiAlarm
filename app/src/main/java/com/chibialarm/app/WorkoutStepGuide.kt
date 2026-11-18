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
     * Read a catalogued easier variation without changing the active plan or timer.
     * Catalog text is bounded and normalized before it reaches platform TTS.
     */
    fun spokenEasierCue(moveName: String, easierInstructions: String): String =
        spokenMoveNote(
            moveName,
            easierInstructions,
            "Chưa có hướng dẫn giảm độ khó riêng cho động tác này. Hãy giữ biên độ thoải mái và chỉ tiếp tục khi thấy ổn."
        )

    /**
     * Read the exercise-specific caution note on request. This is not a symptom report:
     * only the separate voice-command parser decides whether the user needs to stop.
     */
    fun spokenSafetyNoteCue(moveName: String, safetyNote: String): String =
        spokenMoveNote(
            moveName,
            safetyNote,
            "Chưa có lưu ý riêng cho động tác này. Hãy đọc phần lưu ý trên màn hình và dừng nếu thấy đau hoặc khó chịu."
        )

    private fun spokenMoveNote(moveName: String, note: String, fallback: String): String {
        val safeName = moveName
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        val safeNote = note
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
        if (safeNote.isBlank()) return fallback
        return if (safeName.isBlank()) safeNote else "$safeName. $safeNote"
    }

    /**
     * A compact, accessible dose summary for the step-by-step guide.
     * Uses the already-adjusted plan and self-reported completed-set count;
     * it never infers completion from the timer.
     */
    fun workoutSummary(
        dose: String,
        plan: WorkoutSetTracker.Plan?,
        completedSets: Int
    ): String {
        val safeDose = dose
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { "Theo giáo án" }
        if (plan == null) {
            return "Mục tiêu: $safeDose. Bộ đếm thời gian chỉ là gợi ý; nghỉ thêm nếu cần."
        }

        val maximum = plan.maximumSets.coerceAtLeast(0)
        val required = plan.requiredSets.coerceIn(0, maximum)
        val done = completedSets.coerceIn(0, maximum)
        val target = if (plan.targetMin == plan.targetMax) {
            "${plan.targetMin}"
        } else {
            "${plan.targetMin} đến ${plan.targetMax}"
        }
        val unit = plan.unit.replace(Regex("[\\r\\n\\p{Cc}]"), " ").take(20)
        val progress = when {
            done >= maximum -> "Đã ghi nhận đủ $maximum hiệp; không cần tập thêm."
            done >= required -> "Đã hoàn thành đủ hiệp bắt buộc; hiệp bổ sung còn lại là tùy chọn."
            else -> "Đã xác nhận $done/$maximum hiệp."
        }
        val rest = plan.defaultRestSeconds.coerceIn(0, 600)
        return "Mục tiêu: $required hiệp bắt buộc, tối đa $maximum hiệp; " +
            "$target $unit/hiệp. Nghỉ đề xuất $rest giây. $progress"
    }

    /** Last selected spoken step, scoped to the current exercise index. */
    data class SelectedCue(val moveIndex: Int, val speech: String)

    /** Repeat the selected step only while it still belongs to the current exercise. */
    fun repeatCue(
        currentMoveIndex: Int,
        selected: SelectedCue?,
        moveName: String,
        instructions: String
    ): String = selected
        ?.takeIf { it.moveIndex == currentMoveIndex && it.speech.isNotBlank() }
        ?.speech
        ?: spokenCue(moveName, instructions, 1)

    /**
     * Auto-read is opt-in. The initial render and unchanged position never trigger
     * speech; only a real, valid step navigation does.
     */
    fun shouldSpeakOnNavigation(
        enabled: Boolean,
        previousPosition: Int?,
        nextPosition: Int
    ): Boolean = enabled && previousPosition != null &&
        previousPosition > 0 && nextPosition > 0 && previousPosition != nextPosition

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

        /** Return to the first instruction without touching workout progress or timing. */
        fun first(): Walkthrough =
            if (index == 0) this else Walkthrough(steps, 0)

        companion object {
            fun start(instructions: String): Walkthrough =
                Walkthrough(WorkoutStepGuide.split(instructions), 0)
        }
    }
}
