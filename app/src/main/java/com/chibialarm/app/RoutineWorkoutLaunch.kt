package com.chibialarm.app

/**
 * Resolves a weekly fitness reminder to an exact, locally curated workout.
 *
 * Routine JSON predates weekly program IDs, so the weekday prefix is intentionally parsed
 * only against known catalog names. The caller opens FitnessActivity for review; its existing
 * startProgram path rechecks current equipment and movement restrictions before launching.
 */
internal object RoutineWorkoutLaunch {
    private val weekdayPrefixes = setOf(
        "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    )
    private val nonWorkoutMoves = setOf("Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm")

    fun resolve(
        routine: Routine,
        programs: List<TrainingProgram> = TrainingCatalog.programs
    ): TrainingProgram? {
        if (routine.category != "🏃 Sức khỏe") return null
        val title = routine.title.trim()
        val program = programs.firstOrNull { candidate ->
            title == candidate.name ||
                weekdayPrefixes.any { weekday -> title == "$weekday · ${candidate.name}" }
        } ?: return null

        if (program.moves.isEmpty()) return null
        if (program.moves.none { it.name !in nonWorkoutMoves }) return null
        return program
    }
}
