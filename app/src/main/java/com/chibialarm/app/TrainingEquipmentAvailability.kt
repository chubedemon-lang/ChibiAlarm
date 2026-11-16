package com.chibialarm.app

/**
 * Maps the Fitness screen's coarse equipment choice to catalog capabilities.
 * A move's equipmentOptions are alternatives: any one available option is sufficient.
 */
object TrainingEquipmentAvailability {
    fun fromPreference(selection: Int): Set<TrainingEquipment> = when (selection) {
        0 -> emptySet()
        1 -> setOf(TrainingEquipment.MAT)
        2 -> setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND)
        3 -> setOf(TrainingEquipment.STABLE_CHAIR)
        // New exact choices are appended so existing saved indexes 0..3 keep their meaning.
        4 -> setOf(TrainingEquipment.DUMBBELL)
        5 -> setOf(TrainingEquipment.RESISTANCE_BAND)
        else -> throw IllegalArgumentException("Unknown equipment selection: $selection")
    }

    fun allows(move: TrainingMove, available: Set<TrainingEquipment>): Boolean =
        move.equipmentOptions.isEmpty() || move.equipmentOptions.any(available::contains)
}
