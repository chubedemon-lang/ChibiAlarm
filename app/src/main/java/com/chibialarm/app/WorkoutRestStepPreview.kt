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

    /** One explicit, user-requested TTS cue; setup text never gets a guessed movement phase. */
    fun spokenMessage(move: TrainingMove): String? {
        val cue = forMove(move) ?: return null
        val safeName = move.name
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        if (safeName.isBlank()) return null
        val phase = if (cue.hasExplicitPhase) {
            "${WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.classify(move.name), cue.alternate)}. "
        } else ""
        return "$safeName. ${phase}Bước cần chú ý: ${cue.instruction}."
    }
}
