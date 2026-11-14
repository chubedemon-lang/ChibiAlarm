package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class TrainingEquipmentAvailabilityTest {
    private fun move(
        name: String,
        options: Set<TrainingEquipment> = emptySet()
    ) = TrainingMove(
        name = name,
        dose = "2 hiệp × 8 lần",
        steps = "Thực hiện chậm và có kiểm soát",
        easier = "Giảm biên độ",
        avoid = "Dừng nếu đau",
        equipmentOptions = options
    )

    @Test fun alternativeEquipmentOptionsRequireOnlyOneAvailableOption() {
        val weighted = move(
            "Bài kéo lưng",
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND)
        )
        assertTrue(TrainingEquipmentAvailability.allows(weighted, setOf(TrainingEquipment.DUMBBELL)))
        assertTrue(TrainingEquipmentAvailability.allows(weighted, setOf(TrainingEquipment.RESISTANCE_BAND)))
        assertFalse(TrainingEquipmentAvailability.allows(weighted, emptySet()))
    }

    @Test fun equipmentMetadataRatherThanDisplayNameControlsAvailability() {
        val renamed = move("Upper-body pull", setOf(TrainingEquipment.DUMBBELL))
        val misleadingNameWithoutMetadata = move("Kéo tạ đơn hoặc dây kháng lực")
        assertFalse(TrainingEquipmentAvailability.allows(renamed, emptySet()))
        assertTrue(TrainingEquipmentAvailability.allows(renamed, setOf(TrainingEquipment.DUMBBELL)))
        assertTrue(TrainingEquipmentAvailability.allows(misleadingNameWithoutMetadata, emptySet()))
    }

    @Test fun screenChoicesMapToExplicitEquipmentCapabilities() {
        assertEquals(emptySet<TrainingEquipment>(), TrainingEquipmentAvailability.fromPreference(0))
        assertEquals(setOf(TrainingEquipment.MAT), TrainingEquipmentAvailability.fromPreference(1))
        assertEquals(
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
            TrainingEquipmentAvailability.fromPreference(2)
        )
        assertEquals(
            setOf(TrainingEquipment.STABLE_CHAIR),
            TrainingEquipmentAvailability.fromPreference(3)
        )
    }

    @Test fun invalidScreenChoiceIsRejected() {
        try {
            TrainingEquipmentAvailability.fromPreference(4)
            fail("Expected invalid equipment selection to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
