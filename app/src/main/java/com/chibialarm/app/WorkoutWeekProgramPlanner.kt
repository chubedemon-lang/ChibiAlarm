package com.chibialarm.app

import java.time.LocalDate

/**
 * Deterministically distributes compatible catalog programs across a weekly reminder draft.
 * The first session preserves the user's selected program; later sessions rotate toward the
 * selected goal. Candidates must already have passed level, equipment and movement filters.
 */
internal object WorkoutWeekProgramPlanner {
    data class Session(val date: LocalDate, val program: TrainingProgram) {
        /** RoutineReminder stores Sunday at bit zero, matching java.util.Calendar. */
        val repeatMask: Int
            get() = 1 shl (date.dayOfWeek.value % 7)

        /** A stable, human-readable title for the recurring weekday reminder. */
        val routineTitle: String
            get() = "${weekdayNames[date.dayOfWeek.value - 1]} · ${program.name}"
    }

    private val weekdayNames = listOf(
        "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    )

    // Goal-specific secondary sessions. The selected catalog program remains the first day.
    private val rotations: List<List<Set<String>>> = listOf(
        // Strength: alternate strength with low-impact cardio and mobility.
        listOf(
            setOf("Cardio", "Cardio nhẹ"),
            setOf("Linh hoạt", "Vận động nhẹ", "Phục hồi nhẹ"),
            setOf("Sức mạnh", "Toàn thân"),
            setOf("Cardio", "Cardio nhẹ")
        ),
        // Cardiovascular activity: mobility and low-impact days separate cardio sessions.
        listOf(
            setOf("Linh hoạt", "Vận động nhẹ", "Phục hồi nhẹ"),
            setOf("Cardio", "Cardio nhẹ"),
            setOf("Sức mạnh", "Toàn thân"),
            setOf("Cardio", "Cardio nhẹ")
        ),
        // Flexibility: prioritize gentle movement, with varied supporting sessions.
        listOf(
            setOf("Vận động nhẹ", "Phục hồi nhẹ"),
            setOf("Cardio nhẹ", "Cardio"),
            setOf("Sức mạnh", "Toàn thân"),
            setOf("Linh hoạt")
        ),
        // Desk fatigue: alternate mobility, light movement and general strength.
        listOf(
            setOf("Linh hoạt", "Vận động nhẹ", "Phục hồi nhẹ"),
            setOf("Cardio nhẹ", "Cardio"),
            setOf("Sức mạnh", "Toàn thân"),
            setOf("Vận động nhẹ", "Phục hồi nhẹ")
        ),
        // Beginner: keep a gentle mix and avoid selecting the advanced track by policy upstream.
        listOf(
            setOf("Sức mạnh", "Toàn thân"),
            setOf("Linh hoạt", "Vận động nhẹ", "Phục hồi nhẹ"),
            setOf("Cardio nhẹ", "Cardio"),
            setOf("Vận động nhẹ", "Phục hồi nhẹ")
        ),
        // Challenge: use the advanced category only if it survived the caller's level filter.
        listOf(
            setOf("Cardio", "Cardio nhẹ"),
            setOf("Linh hoạt", "Vận động nhẹ"),
            setOf("Thử thách", "Sức mạnh"),
            setOf("Cardio nhẹ", "Cardio")
        )
    )

    fun plan(
        trainingDates: List<LocalDate>,
        preferred: TrainingProgram,
        eligiblePrograms: List<TrainingProgram>,
        goalIndex: Int
    ): List<Session> {
        require(trainingDates.size in 1..5) { "A weekly plan supports one to five training days" }
        require(trainingDates.distinct().size == trainingDates.size) { "Training dates must be unique" }
        require(goalIndex in rotations.indices) { "Unknown Fitness goal" }

        val candidates = eligiblePrograms.distinctBy { it.id }
        require(candidates.isNotEmpty()) { "No compatible programs are available" }
        val selectedPreferred = candidates.firstOrNull { it.id == preferred.id }
            ?: throw IllegalArgumentException("The selected program is not in the compatible catalog")

        val usage = linkedMapOf<String, Int>()
        val rotation = rotations[goalIndex]
        return trainingDates.sorted().mapIndexed { index, date ->
            val selected = if (index == 0) {
                selectedPreferred
            } else {
                val desiredCategories = rotation[(index - 1) % rotation.size]
                candidates.firstOrNull { it.id !in usage && it.category in desiredCategories }
                    ?: candidates.firstOrNull { it.id !in usage }
                    ?: candidates.minByOrNull { usage[it.id] ?: 0 }
                    ?: error("Compatible catalog unexpectedly became empty")
            }
            usage[selected.id] = (usage[selected.id] ?: 0) + 1
            Session(date, selected)
        }
    }
}
