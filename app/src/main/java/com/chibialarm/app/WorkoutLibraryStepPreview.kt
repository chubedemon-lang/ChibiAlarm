package com.chibialarm.app

/**
 * One-exercise walkthrough state for the library preview.
 * Moving through instructions never changes the workout timer, set log or XP.
 */
internal class WorkoutLibraryStepPreview private constructor(
    private val moveName: String,
    private val instructions: String,
    private val walkthrough: WorkoutStepGuide.Walkthrough
) {
    val position: Int get() = walkthrough.position
    val size: Int get() = walkthrough.steps.size
    val currentStep: String get() = walkthrough.currentStep
    val canGoPrevious: Boolean get() = walkthrough.canGoPrevious
    val canGoNext: Boolean get() = walkthrough.canGoNext

    fun previous(): WorkoutLibraryStepPreview =
        if (canGoPrevious) WorkoutLibraryStepPreview(moveName, instructions, walkthrough.previous()) else this

    fun next(): WorkoutLibraryStepPreview =
        if (canGoNext) WorkoutLibraryStepPreview(moveName, instructions, walkthrough.next()) else this

    fun spokenCue(): String =
        WorkoutStepGuide.spokenCue(moveName, instructions, position)

    /** Null means the wording does not clearly identify either illustrated movement phase. */
    fun illustrationPhase(): Boolean? =
        WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.classify(moveName),
            currentStep
        )

    companion object {
        fun start(move: TrainingMove): WorkoutLibraryStepPreview =
            WorkoutLibraryStepPreview(
                moveName = move.name,
                instructions = move.steps,
                walkthrough = WorkoutStepGuide.Walkthrough.start(move.steps)
            )
    }
}
