package com.chibialarm.app

/**
 * Chooses a safe, profile-compatible fallback when the advisor's first catalog plan
 * loses every main movement after equipment and session-only movement filters.
 *
 * This is a deterministic on-device policy, not a medical assessment or AI diagnosis.
 */
internal object WorkoutPlanCandidateSelector {
    data class Selection(
        val program: TrainingProgram,
        val usedFallback: Boolean,
        val notice: String
    )

    private data class Ranked(
        val program: TrainingProgram,
        val score: Int,
        val unallocatedSeconds: Int
    )

    private val nonMainMoves = setOf(
        "Khởi động tại chỗ",
        "Thả lỏng",
        "Giãn cơ và thở chậm"
    )

    fun select(
        preferred: TrainingProgram,
        candidates: List<TrainingProgram>,
        preferences: FitnessPlanAdvisor.Preferences,
        constraints: WorkoutConstraintProfile
    ): Selection? {
        require(preferences.goal in 0..5)
        require(preferences.energy in 0..2)
        require(preferences.level in 0..2)
        require(preferences.minutes in 5..120)
        require(preferences.equipment in 0..5)
        require(preferences.impact in 0..2)
        require(preferences.additionalEquipment.size <= TrainingEquipment.values().size)

        if (preferences.impact == 2) return null

        val availableEquipment = TrainingEquipmentAvailability.fromPreference(preferences.equipment) +
            preferences.additionalEquipment

        fun isCompatible(program: TrainingProgram): Boolean {
            if (program.moves.none { it.name !in nonMainMoves }) return false
            if (program.id == "advanced" && preferences.level < 2) return false
            if (program.id == "weighted" && preferences.level < 1) return false
            return program.moves.all { move ->
                WorkoutConstraintFilter.isAllowed(move, constraints) &&
                    TrainingEquipmentAvailability.allows(move, availableEquipment) &&
                    (preferences.impact != 1 || !move.requiresJumping)
            }
        }

        val compatible = candidates.distinctBy { it.id }.filter(::isCompatible)
        compatible.firstOrNull { it.id == preferred.id }?.let {
            return Selection(it, usedFallback = false, notice = "")
        }
        if (compatible.isEmpty()) return null

        val extensionPreferences = WorkoutExtensionPreferences(
            goal = preferences.goal,
            energy = preferences.energy,
            equipment = preferences.equipment,
            impact = preferences.impact,
            constraints = constraints,
            additionalEquipment = preferences.additionalEquipment
        )

        val ranked = compatible.mapNotNull { program ->
            val plan = runCatching {
                WorkoutDurationPlanner.build(
                    program = program,
                    level = preferences.level,
                    targetMinutes = preferences.minutes,
                    preferences = extensionPreferences
                )
            }.getOrNull() ?: return@mapNotNull null

            // A fallback must fit the selected time budget. The normal preferred plan keeps
            // its existing preview/warning behavior when it is otherwise compatible.
            if (plan.exceedsTarget) return@mapNotNull null
            Ranked(
                program = program,
                score = score(program, preferences, plan.unallocatedSeconds),
                unallocatedSeconds = plan.unallocatedSeconds
            )
        }

        val selected = ranked.sortedWith(
            compareByDescending<Ranked> { it.score }
                .thenBy { it.unallocatedSeconds }
                .thenBy { it.program.id }
        ).firstOrNull() ?: return null

        val notice = buildString {
            append("Chương trình ưu tiên “").append(preferred.name)
                .append("” không còn phù hợp với bộ lựa chọn hiện tại (trình độ, dụng cụ, hạn chế vận động và thời gian). ")
            append("Chibi đề xuất “").append(selected.program.name)
                .append("” vì phù hợp hơn với mục tiêu, trình độ và khung ")
                .append(preferences.minutes).append(" phút. ")
            append("Đây là phương án đã lọc theo lựa chọn hiện tại, không phải bài thay thế tương đương y khoa; hãy xem lại từng động tác trước khi xác nhận.")
        }
        return Selection(selected.program, usedFallback = true, notice = notice)
    }

    private fun score(
        program: TrainingProgram,
        preferences: FitnessPlanAdvisor.Preferences,
        unallocatedSeconds: Int
    ): Int {
        var score = when (preferences.goal) {
            0 -> when (program.category) {
                "Sức mạnh" -> 12
                "Toàn thân" -> 7
                "Vận động nhẹ" -> 3
                else -> 0
            }
            1 -> when {
                program.category.startsWith("Cardio") -> 14
                program.category == "Vận động nhẹ" -> 5
                else -> 0
            }
            2 -> when (program.category) {
                "Linh hoạt" -> 14
                "Phục hồi nhẹ" -> 7
                else -> 0
            }
            3 -> when {
                program.id == "desk" -> 16
                program.category == "Vận động nhẹ" -> 8
                program.category == "Linh hoạt" -> 5
                else -> 0
            }
            4 -> if (program.id in beginnerPrograms) 16 else 0
            else -> when {
                program.id == "advanced" -> 16
                program.category == "Sức mạnh" -> 9
                else -> 0
            }
        }

        if (preferences.energy == 0) {
            score += when (program.category) {
                "Phục hồi nhẹ" -> 20
                "Linh hoạt" -> 6
                else -> -10
            }
        }
        if (preferences.impact == 1) {
            score += when (program.category) {
                "Cardio nhẹ", "Linh hoạt", "Vận động nhẹ", "Phục hồi nhẹ" -> 10
                "Cardio" -> 4
                "Thử thách" -> -6
                else -> 0
            }
        }
        if (preferences.level == 0 && program.id in beginnerPrograms) score += 3
        if (preferences.level == 2 && program.id == "advanced") score += 3

        score += when {
            preferences.minutes <= 10 && program.id.startsWith("quick") -> 8
            preferences.minutes <= 10 -> -3
            preferences.minutes in 11..15 && program.id.startsWith("quick15") -> 10
            preferences.minutes in 11..15 && program.id.startsWith("quick") -> 3
            preferences.minutes in 11..15 -> 0
            program.id.startsWith("quick") -> -5
            else -> 2
        }

        // Prefer plans that use the available window without padding or repeating sets.
        score -= (unallocatedSeconds / 120).coerceAtMost(8)
        return score
    }

    private val beginnerPrograms = setOf(
        "starter", "chair", "quick", "quick15", "quick15chair"
    )
}
