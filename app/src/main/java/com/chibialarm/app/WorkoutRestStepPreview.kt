package com.chibialarm.app

/**
 * Picks one actionable catalog instruction for the rest-screen preview.
 *
 * Setup instructions are skipped only when the catalog has an unambiguous
 * two-phase movement cue. Unknown/hold-only poses keep the first setup cue and
 * never invent a movement phase or claim that form has been assessed.
 */
internal object WorkoutRestStepPreview {
    data class Cue(
        val instruction: String,
        val alternate: Boolean,
        val hasExplicitPhase: Boolean
    )

    fun forMove(move: TrainingMove): Cue? {
        if (move.steps.isBlank()) return null
        val pose = WorkoutPoseGuide.classify(move.name)
        val steps = WorkoutStepGuide.split(move.steps).take(100)
        val actionable = steps.mapNotNull { step ->
            WorkoutPoseMotion.guidePhaseForStep(pose, step)?.let { phase ->
                Cue(step, phase, hasExplicitPhase = true)
            }
        }.firstOrNull()
        val selected = actionable ?: steps.firstOrNull()?.let { Cue(it, false, hasExplicitPhase = false) } ?: return null
        val safeInstruction = selected.instruction
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
        if (safeInstruction.isBlank()) return null
        return selected.copy(instruction = safeInstruction)
    }

    /**
     * One explicit, user-requested TTS cue. Include the exact next set/dose when it is
     * a set-based move; optional work is labelled optional and never presented as required.
     */
    fun spokenMessage(
        move: TrainingMove,
        plan: WorkoutSetTracker.Plan? = null,
        completedSets: Int = 0
    ): String? {
        val cue = forMove(move) ?: return null
        val safeName = safeText(move.name, 80)
        if (safeName.isBlank()) return null
        val dose = doseCue(move, plan, completedSets)
        val phase = if (cue.hasExplicitPhase) {
            "${WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.classify(move.name), cue.alternate)}. "
        } else ""
        return "$safeName. $dose$phase Bước cần chú ý: ${cue.instruction}."
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(520)
    }

    private fun doseCue(
        move: TrainingMove,
        plan: WorkoutSetTracker.Plan?,
        completedSets: Int
    ): String {
        if (plan == null) {
            val dose = safeText(move.dose, 100)
            return dose.takeIf { it.isNotBlank() }
                ?.let { "Liều lượng theo giáo án: $it. " }
                .orEmpty()
        }

        val maximum = plan.maximumSets.coerceIn(1, 20)
        val done = completedSets.coerceIn(0, maximum)
        if (done >= maximum) return "Đã ghi nhận đủ $maximum hiệp; không cần tập thêm. "
        val nextSet = done + 1
        val target = if (plan.targetMin == plan.targetMax) {
            "${plan.targetMin} ${plan.unit}"
        } else {
            "${plan.targetMin} đến ${plan.targetMax} ${plan.unit}"
        }
        val optional = nextSet > plan.requiredSets
        val kind = if (optional) "hiệp tùy chọn" else "hiệp bắt buộc"
        val rest = plan.defaultRestSeconds.coerceIn(15, 180)
        val optionalNote = if (optional) " Chỉ thực hiện nếu còn sức; có thể bỏ qua." else ""
        return "$kind $nextSet/$maximum. Mục tiêu $target mỗi hiệp. " +
            "Nghỉ giữa hiệp đề xuất $rest giây.$optionalNote "
    }

    private fun safeText(value: String, limit: Int): String = value
        .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit)
}
