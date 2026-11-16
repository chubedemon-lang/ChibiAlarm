package com.chibialarm.app

import android.content.Intent
import android.os.Bundle

/**
 * Transfers only coarse, user-reviewed workout choices between the offline coach
 * and FitnessActivity. Raw speech, transcript text and health notes are never copied.
 */
object CoachFitnessHandoff {
    private const val BUNDLE_PRESENT = "fitness_context_present"
    private const val INTENT_PREFIX = "coach_"

    fun saveToBundle(context: FitnessNaturalRequest.Result?, bundle: Bundle) {
        val snapshot = FitnessCoachSessionSnapshot.capture(context, context?.impact) ?: return
        bundle.putBoolean(BUNDLE_PRESENT, true)
        snapshot.goal?.let { bundle.putInt("fitness_goal", it) }
        snapshot.level?.let { bundle.putInt("fitness_level", it) }
        snapshot.energy?.let { bundle.putInt("fitness_energy", it) }
        snapshot.minutes?.let { bundle.putInt("fitness_minutes", it) }
        snapshot.equipment?.let { bundle.putInt("fitness_equipment", it) }
        snapshot.impact?.let { bundle.putInt("fitness_impact", it) }
        snapshot.avoidWristLoad?.let { bundle.putBoolean("fitness_avoid_wrist_load", it) }
        snapshot.avoidFloorPositions?.let { bundle.putBoolean("fitness_avoid_floor_positions", it) }
        snapshot.avoidOverheadReach?.let { bundle.putBoolean("fitness_avoid_overhead_reach", it) }
        snapshot.additionalEquipment?.let { selected ->
            bundle.putStringArrayList(
                "fitness_additional_equipment",
                ArrayList(selected.map { it.name }.sorted())
            )
        }
    }

    fun restoreFromBundle(bundle: Bundle?): FitnessNaturalRequest.Result? {
        if (bundle?.getBoolean(BUNDLE_PRESENT) != true) return null

        fun intValue(key: String): Int? =
            if (bundle.containsKey(key)) bundle.getInt(key) else null
        fun booleanValue(key: String): Boolean? =
            if (bundle.containsKey(key)) bundle.getBoolean(key) else null

        val additionalEquipment = bundle.getStringArrayList("fitness_additional_equipment")
            ?.mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
            ?.toSet()

        return FitnessCoachSessionSnapshot(
            goal = intValue("fitness_goal"),
            level = intValue("fitness_level"),
            energy = intValue("fitness_energy"),
            minutes = intValue("fitness_minutes"),
            equipment = intValue("fitness_equipment"),
            impact = intValue("fitness_impact"),
            avoidWristLoad = booleanValue("fitness_avoid_wrist_load"),
            avoidFloorPositions = booleanValue("fitness_avoid_floor_positions"),
            avoidOverheadReach = booleanValue("fitness_avoid_overhead_reach"),
            additionalEquipment = additionalEquipment
        ).restore()
    }

    fun addToFitnessIntent(context: FitnessNaturalRequest.Result?, intent: Intent) {
        context ?: return
        context.goal?.let { intent.putExtra(INTENT_PREFIX + "goal", it) }
        context.level?.let { intent.putExtra(INTENT_PREFIX + "level", it) }
        context.energy?.let { intent.putExtra(INTENT_PREFIX + "energy", it) }
        context.minutes?.let { intent.putExtra(INTENT_PREFIX + "minutes", it) }
        context.equipment?.let { intent.putExtra(INTENT_PREFIX + "equipment", it) }
        context.impact?.let { intent.putExtra(INTENT_PREFIX + "impact", it) }
        context.avoidWristLoad?.let { intent.putExtra(INTENT_PREFIX + "avoid_wrist_load", it) }
        context.avoidFloorPositions?.let { intent.putExtra(INTENT_PREFIX + "avoid_floor_positions", it) }
        context.avoidOverheadReach?.let { intent.putExtra(INTENT_PREFIX + "avoid_overhead_reach", it) }
        context.additionalEquipment?.let { selected ->
            intent.putStringArrayListExtra(
                INTENT_PREFIX + "additional_equipment",
                ArrayList(selected.map { it.name }.sorted())
            )
        }
    }
}
