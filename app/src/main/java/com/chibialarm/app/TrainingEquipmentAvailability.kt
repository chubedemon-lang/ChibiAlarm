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
        else -> throw IllegalArgumentException("Unknown equipment selection: $selection")
    }

    fun allows(move: TrainingMove, available: Set<TrainingEquipment>): Boolean =
        move.equipmentOptions.isEmpty() || move.equipmentOptions.any(available::contains)
}
