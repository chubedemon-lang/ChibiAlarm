package com.chibialarm.app

import android.os.Bundle

/**
 * Transient Activity saved-state snapshot for process recreation.
 *
 * It contains structured workout choices and session safety switches only. It never contains
 * raw text, recognized speech, coach replies, journal notes, account identifiers, or credentials.
 * Unlike SharedPreferences/Room, this snapshot is not an app-owned long-term profile store.
 */
internal data class FitnessCoachRestorableProfile(
    val goal: Int,
    val level: Int,
    val energy: Int,
    val minutes: Int,
    val equipment: Int,
    val impact: Int,
    val avoidWristLoad: Boolean,
    val avoidFloorPositions: Boolean,
    val avoidOverheadReach: Boolean,
    val additionalEquipment: Set<TrainingEquipment>,
    val avoidKneeling: Boolean? = null
) {
    fun restore(): FitnessNaturalRequest.Result = FitnessNaturalRequest.Result(
        goal = goal,
        level = level,
        energy = energy,
        minutes = minutes,
        equipment = equipment,
        impact = impact,
        reply = "Đã khôi phục lựa chọn của buổi tập. Hãy kiểm tra lại giáo án trước khi bắt đầu.",
        avoidWristLoad = avoidWristLoad,
        avoidFloorPositions = avoidFloorPositions,
        avoidOverheadReach = avoidOverheadReach,
        additionalEquipment = additionalEquipment,
        avoidKneeling = avoidKneeling
    )

    fun writeTo(bundle: Bundle) {
        require(isValid())
        bundle.putInt(KEY_VERSION, VERSION)
        bundle.putInt(KEY_GOAL, goal)
        bundle.putInt(KEY_LEVEL, level)
        bundle.putInt(KEY_ENERGY, energy)
        bundle.putInt(KEY_MINUTES, minutes)
        bundle.putInt(KEY_EQUIPMENT, equipment)
        bundle.putInt(KEY_IMPACT, impact)
        bundle.putBoolean(KEY_WRIST, avoidWristLoad)
        bundle.putBoolean(KEY_FLOOR, avoidFloorPositions)
        bundle.putBoolean(KEY_OVERHEAD, avoidOverheadReach)
        avoidKneeling?.let { bundle.putBoolean(KEY_KNEELING, it) }
        bundle.putStringArrayList(
            KEY_ADDITIONAL_EQUIPMENT,
            ArrayList(additionalEquipment.sortedBy { it.ordinal }.map { it.name })
        )
    }

    private fun isValid(): Boolean =
        goal in 0..5 &&
            level in 0..2 &&
            energy in 0..2 &&
            minutes in 5..120 &&
            equipment in 0..5 &&
            impact in 0..2 &&
            additionalEquipment.size <= TrainingEquipment.values().size

    companion object {
        private const val VERSION = 2
        private const val LEGACY_VERSION = 1
        private const val KEY_VERSION = "fitness_coach_state_version"
        private const val KEY_GOAL = "fitness_coach_state_goal"
        private const val KEY_LEVEL = "fitness_coach_state_level"
        private const val KEY_ENERGY = "fitness_coach_state_energy"
        private const val KEY_MINUTES = "fitness_coach_state_minutes"
        private const val KEY_EQUIPMENT = "fitness_coach_state_equipment"
        private const val KEY_IMPACT = "fitness_coach_state_impact"
        private const val KEY_WRIST = "fitness_coach_state_avoid_wrist"
        private const val KEY_FLOOR = "fitness_coach_state_avoid_floor"
        private const val KEY_OVERHEAD = "fitness_coach_state_avoid_overhead"
        private const val KEY_KNEELING = "fitness_coach_state_avoid_kneeling"
        private const val KEY_ADDITIONAL_EQUIPMENT = "fitness_coach_state_additional_equipment"

        fun capture(
            goal: Int?,
            level: Int?,
            energy: Int?,
            minutes: Int?,
            equipment: Int?,
            impact: Int?,
            avoidWristLoad: Boolean?,
            avoidFloorPositions: Boolean?,
            avoidOverheadReach: Boolean?,
            additionalEquipment: Set<TrainingEquipment>?,
            avoidKneeling: Boolean? = null
        ): FitnessCoachRestorableProfile? {
            if (goal == null || goal !in 0..5 ||
                level == null || level !in 0..2 ||
                energy == null || energy !in 0..2 ||
                minutes == null || minutes !in 5..120 ||
                equipment == null || equipment !in 0..5 ||
                impact == null || impact !in 0..2 ||
                avoidWristLoad == null || avoidFloorPositions == null ||
                avoidOverheadReach == null || additionalEquipment == null ||
                additionalEquipment.size > TrainingEquipment.values().size
            ) return null

            return FitnessCoachRestorableProfile(
                goal = goal,
                level = level,
                energy = energy,
                minutes = minutes,
                equipment = equipment,
                impact = impact,
                avoidWristLoad = avoidWristLoad,
                avoidFloorPositions = avoidFloorPositions,
                avoidOverheadReach = avoidOverheadReach,
                additionalEquipment = additionalEquipment.toSet(),
                avoidKneeling = avoidKneeling
            )
        }

        fun fromBundle(bundle: Bundle?): FitnessCoachRestorableProfile? {
            if (bundle == null) return null
            val version = bundle.getInt(KEY_VERSION, -1)
            if (version != VERSION && version != LEGACY_VERSION) return null
            val rawEquipment = bundle.getStringArrayList(KEY_ADDITIONAL_EQUIPMENT) ?: return null
            if (rawEquipment.size > TrainingEquipment.values().size) return null

            val additional = linkedSetOf<TrainingEquipment>()
            rawEquipment.forEach { name ->
                val item = TrainingEquipment.values().firstOrNull { it.name == name } ?: return null
                if (!additional.add(item)) return null
            }

            fun intValue(key: String): Int? =
                if (bundle.containsKey(key)) bundle.getInt(key) else null
            fun booleanValue(key: String): Boolean? =
                if (bundle.containsKey(key)) bundle.getBoolean(key) else null

            return capture(
                goal = intValue(KEY_GOAL),
                level = intValue(KEY_LEVEL),
                energy = intValue(KEY_ENERGY),
                minutes = intValue(KEY_MINUTES),
                equipment = intValue(KEY_EQUIPMENT),
                impact = intValue(KEY_IMPACT),
                avoidWristLoad = booleanValue(KEY_WRIST),
                avoidFloorPositions = booleanValue(KEY_FLOOR),
                avoidOverheadReach = booleanValue(KEY_OVERHEAD),
                additionalEquipment = additional,
                avoidKneeling = if (version >= VERSION) booleanValue(KEY_KNEELING) else null
            )
        }
    }
}
