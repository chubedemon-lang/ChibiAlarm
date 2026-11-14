package com.chibialarm.app

/** Deterministic offline extension policy; not a medical assessment or AI diagnosis. */
data class WorkoutExtensionPreferences(
    val goal: Int = 0,
    val energy: Int = 1,
    val equipment: Int = 0,
    val impact: Int = 0,
    val constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
    // Additional explicitly reported tools allow a session to combine more than one tool type.
    val additionalEquipment: Set<TrainingEquipment> = emptySet()
) {
    init {
        require(goal in 0..5)
        require(energy in 0..2)
        require(equipment in 0..3)
        require(impact in 0..2)
        require(additionalEquipment.size <= TrainingEquipment.values().size)
    }
}

object WorkoutExtensionSelector {
    private val familyByName = mapOf(
        "Khởi động tại chỗ" to "warmup",
        "Thả lỏng" to "cooldown",
        "Đứng lên – ngồi xuống" to "squat",
        "Squat không tạ" to "squat",
        "Squat chậm 3 giây" to "squat",
        "Bước lùi – lunge" to "single_leg",
        "Chống đẩy tường" to "push",
        "Chống đẩy sàn" to "push",
        "Chống đẩy gối" to "push",
        "Chống đẩy trên bề mặt cao" to "push",
        "Ép ngực tường bằng cẳng tay" to "push",
        "Đẩy vai với tạ đơn hoặc dây" to "push",
        "Nâng hông trên sàn" to "hip_extension",
        "Gập hông với tạ đơn hoặc dây" to "hip_extension",
        "Bird-dog" to "core",
        "Dead bug cơ bụng" to "core",
        "Plank khuỷu hoặc plank gối" to "core",
        "Side plank gối" to "core",
        "Mountain climber chậm" to "cardio",
        "Đi bộ nhanh tại chỗ" to "cardio",
        "Nâng gối luân phiên có điểm tựa" to "cardio",
        "Bước ngang nhẹ" to "lateral_cardio",
        "Nhón gót" to "calves",
        "Nâng gối ngồi ghế" to "seated_march",
        "Đứng thăng bằng có hỗ trợ" to "balance",
        "Cat-cow nhẹ" to "mobility",
        "Giãn cơ và thở chậm" to "mobility",
        "Giãn gân kheo ngồi ghế" to "mobility",
        "Mở vai và ngực" to "upper_mobility",
        "Siết bả vai đứng" to "upper_back",
        "Kéo tạ đơn hoặc dây kháng lực" to "upper_back",
        "Dang chân sang bên có điểm tựa" to "hip_abduction"
    )

    private fun candidates(p: WorkoutExtensionPreferences, level: Int): List<String> {
        if (p.energy == 0 || p.impact == 2) return emptyList()
        if (p.impact == 1) return when (p.goal) {
            1 -> listOf("Nâng gối luân phiên có điểm tựa", "Bước ngang nhẹ",
                "Nâng gối ngồi ghế", "Nhón gót", "Đứng thăng bằng có hỗ trợ")
            2, 3 -> listOf("Giãn gân kheo ngồi ghế", "Mở vai và ngực",
                "Siết bả vai đứng", "Nâng gối ngồi ghế", "Đứng thăng bằng có hỗ trợ")
            else -> listOf("Dang chân sang bên có điểm tựa", "Nâng gối ngồi ghế",
                "Nhón gót", "Nâng hông trên sàn", "Giãn gân kheo ngồi ghế")
        }

        return when (p.goal) {
            0 -> if (p.equipment == 2 && level >= 1) {
                listOf("Kéo tạ đơn hoặc dây kháng lực", "Gập hông với tạ đơn hoặc dây",
                    "Đẩy vai với tạ đơn hoặc dây", "Dang chân sang bên có điểm tựa",
                    "Nhón gót", "Siết bả vai đứng")
            } else listOf("Dang chân sang bên có điểm tựa", "Nhón gót",
                "Siết bả vai đứng", "Nâng gối ngồi ghế", "Giãn gân kheo ngồi ghế")
            1 -> listOf("Nâng gối luân phiên có điểm tựa", "Bước ngang nhẹ",
                "Nâng gối ngồi ghế", "Nhón gót", "Đứng thăng bằng có hỗ trợ")
            2 -> listOf("Giãn gân kheo ngồi ghế", "Mở vai và ngực",
                "Siết bả vai đứng", "Đứng thăng bằng có hỗ trợ", "Nâng gối ngồi ghế")
            3 -> listOf("Mở vai và ngực", "Giãn gân kheo ngồi ghế",
                "Nâng gối ngồi ghế", "Siết bả vai đứng", "Đứng thăng bằng có hỗ trợ")
            4 -> listOf("Nâng gối ngồi ghế", "Dang chân sang bên có điểm tựa",
                "Nhón gót", "Giãn gân kheo ngồi ghế", "Mở vai và ngực")
            else -> if (level >= 2) listOf("Squat chậm 3 giây", "Bước lùi – lunge",
                "Side plank gối", "Nâng gối luân phiên có điểm tựa",
                "Dang chân sang bên có điểm tựa")
            else listOf("Nâng gối ngồi ghế", "Dang chân sang bên có điểm tựa",
                "Nhón gót", "Giãn gân kheo ngồi ghế")
        }
    }

    private fun availableEquipment(preferences: WorkoutExtensionPreferences): Set<TrainingEquipment> =
        TrainingEquipmentAvailability.fromPreference(preferences.equipment) + preferences.additionalEquipment

    fun select(
        baseMoves: List<TrainingMove>,
        level: Int,
        availableSeconds: Int,
        preferences: WorkoutExtensionPreferences
    ): List<TrainingMove> {
        require(level in 0..2)
        require(availableSeconds >= 0)
        if (availableSeconds == 0) return emptyList()

        // Only extend a complete curated session. Short/custom one-move plans and desk breaks
        // must not be padded with unrelated exercises merely because a target is longer.
        val hasWarmUp = baseMoves.firstOrNull()?.name == "Khởi động tại chỗ"
        val hasCoolDown = baseMoves.lastOrNull()?.name in setOf("Thả lỏng", "Giãn cơ và thở chậm")
        if (baseMoves.size < 4 || !hasWarmUp || !hasCoolDown) return emptyList()

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val existingNames = baseMoves.map { it.name }.toSet()
        val usedFamilies = baseMoves.mapNotNull { familyByName[it.name] }.toMutableSet()
        val transition = WorkoutDurationPlanner.transitionAllowance(level)
        val maxExtensions = if (level == 0) 2 else 3
        var budget = availableSeconds.toLong()
        val selected = mutableListOf<TrainingMove>()

        for (name in candidates(preferences, level).distinct()) {
            if (selected.size >= maxExtensions) break
            if (name in existingNames) continue
            val move = catalog[name] ?: continue
            if (!WorkoutConstraintFilter.isAllowed(move, preferences.constraints)) continue
            if (!TrainingEquipmentAvailability.allows(move, availableEquipment(preferences))) continue
            if (move.requiresExternalLoad && (level == 0 || preferences.constraints.avoidWristLoad)) continue

            val family = familyByName[name] ?: continue
            if (!usedFamilies.add(family)) continue
            val cost = WorkoutDurationPlanner.activeSeconds(move).toLong() +
                WorkoutDurationPlanner.prescribedRestSeconds(move).toLong() + transition
            if (cost > budget) {
                usedFamilies.remove(family)
                continue
            }
            selected += move
            budget -= cost
        }
        return selected
    }
    /**
     * Select a single optional move for an explicit mid-session request.
     * The current plan is used to prevent duplicate exercises and duplicate movement families.
     */
    fun selectAdditional(
        baseMoves: List<TrainingMove>,
        level: Int,
        availableSeconds: Int,
        preferences: WorkoutExtensionPreferences,
        preferredEquipment: Set<TrainingEquipment> = emptySet(),
        maxMoves: Int = 1
    ): List<TrainingMove> {
        require(level in 0..2)
        require(availableSeconds >= 0)
        require(maxMoves in 1..3)
        if (availableSeconds == 0 || preferences.energy == 0 || preferences.impact == 2) {
            return emptyList()
        }

        val hasWarmUp = baseMoves.firstOrNull()?.name == "Khởi động tại chỗ"
        val hasCoolDown = baseMoves.lastOrNull()?.name in setOf("Thả lỏng", "Giãn cơ và thở chậm")
        if (baseMoves.size < 4 || !hasWarmUp || !hasCoolDown) return emptyList()

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val existingNames = baseMoves.map { it.name }.toSet()
        val usedFamilies = baseMoves.mapNotNull { familyByName[it.name] }.toMutableSet()
        val available = availableEquipment(preferences)
        val orderedCandidates = candidates(preferences, level).distinct().sortedByDescending { name ->
            catalog[name]?.equipmentOptions.orEmpty().any(preferredEquipment::contains)
        }
        val transition = WorkoutDurationPlanner.transitionAllowance(level)
        var budget = availableSeconds.toLong()
        val selected = mutableListOf<TrainingMove>()

        for (name in orderedCandidates) {
            if (selected.size >= maxMoves) break
            if (name in existingNames) continue
            val move = catalog[name] ?: continue
            if (!WorkoutConstraintFilter.isAllowed(move, preferences.constraints)) continue
            if (!TrainingEquipmentAvailability.allows(move, available)) continue
            if (move.requiresExternalLoad && (level == 0 || preferences.constraints.avoidWristLoad)) continue

            val family = familyByName[name] ?: continue
            if (!usedFamilies.add(family)) continue
            val cost = WorkoutDurationPlanner.activeSeconds(move).toLong() +
                WorkoutDurationPlanner.prescribedRestSeconds(move) + transition
            if (cost > budget) {
                usedFamilies.remove(family)
                continue
            }
            selected += move
            budget -= cost
        }
        return selected
    }
}