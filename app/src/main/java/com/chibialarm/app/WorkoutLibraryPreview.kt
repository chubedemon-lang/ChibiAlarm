package com.chibialarm.app

/**
 * Immutable, bounded navigation state for the exercise-library preview.
 * It snapshots the selected plan so a caller cannot change the preview underneath the dialog.
 */
internal class WorkoutLibraryPreview private constructor(
    private val plan: List<TrainingMove>,
    val index: Int
) {
    init {
        require(plan.isNotEmpty() && plan.size <= MAX_MOVES)
        require(index in plan.indices)
    }

    val currentMove: TrainingMove
        get() = plan[index]

    val position: Int
        get() = index + 1

    val size: Int
        get() = plan.size

    val canGoPrevious: Boolean
        get() = index > 0

    val canGoNext: Boolean
        get() = index < plan.lastIndex

    fun previous(): WorkoutLibraryPreview =
        if (canGoPrevious) WorkoutLibraryPreview(plan, index - 1) else this

    fun next(): WorkoutLibraryPreview =
        if (canGoNext) WorkoutLibraryPreview(plan, index + 1) else this

    companion object {
        const val MAX_MOVES = 100

        /** Returns null for an empty or unreasonable plan instead of opening a broken preview. */
        fun start(moves: List<TrainingMove>): WorkoutLibraryPreview? {
            if (moves.isEmpty() || moves.size > MAX_MOVES) return null
            return WorkoutLibraryPreview(moves.toList(), 0)
        }
    }
}
