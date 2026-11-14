package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutEquipmentFilterTest {
    @Test fun noEquipmentRemovesChairMovesButKeepsBodyweightMoves() {
        val starter = TrainingCatalog.byId("starter")
        val result = WorkoutEquipmentFilter.apply(starter, 0)

        assertTrue(result.canStart)
        assertTrue(result.removedMoves.contains("Đứng lên – ngồi xuống"))
        assertFalse(result.program.moves.any { it.name == "Đứng lên – ngồi xuống" })
        assertTrue(result.program.moves.any { it.name == "Chống đẩy tường" })
        assertTrue(result.notice.contains("dụng cụ chưa chọn"))
        assertEquals(5, starter.moves.size)
    }

    @Test fun dumbbellOrBandSelectionKeepsOnlyMovesWhoseMetadataAllowsIt() {
        val weighted = TrainingCatalog.byId("weighted")
        val result = WorkoutEquipmentFilter.apply(weighted, 2)

        assertTrue(result.canStart)
        assertTrue(result.program.moves.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        assertFalse(result.program.moves.any { it.name == "Đứng lên – ngồi xuống" })
    }

    @Test fun equipmentOnlyProgramCannotStartWhenItsToolIsMissing() {
        val chairMove = TrainingCatalog.byId("starter").moves.first {
            it.name == "Đứng lên – ngồi xuống"
        }
        val onlyChair = TrainingProgram(
            id = "chair-only",
            name = "Chair only",
            subtitle = "test",
            category = "test",
            moves = listOf(chairMove)
        )
        val result = WorkoutEquipmentFilter.apply(onlyChair, 0)
        assertFalse(result.canStart)
        assertTrue(result.program.moves.isEmpty())
    }

    @Test fun filteringDoesNotMutateTheCatalogProgram() {
        val original = TrainingCatalog.byId("starter")
        val before = original.moves.toList()
        WorkoutEquipmentFilter.apply(original, 0)
        assertEquals(before, original.moves)
    }

    @Test fun stableChairSelectionKeepsChairMovesAndBodyweightMoves() {
        val starter = TrainingCatalog.byId("starter")
        val result = WorkoutEquipmentFilter.apply(starter, 3)

        assertTrue(result.canStart)
        assertFalse(result.removedMoves.contains("Đứng lên – ngồi xuống"))
        assertTrue(result.program.moves.any { it.name == "Đứng lên – ngồi xuống" })
        assertTrue(result.program.moves.any { it.name == "Chống đẩy tường" })
        assertTrue(result.program.moves.all {
            TrainingEquipmentAvailability.allows(
                it, setOf(TrainingEquipment.STABLE_CHAIR)
            )
        })
        assertTrue(starter.moves.any { it.name == "Đứng lên – ngồi xuống" })
    }

    @Test fun primaryAndAdditionalEquipmentAreCombinedForBasePlanFiltering() {
        val starter = TrainingCatalog.byId("starter")
        val result = WorkoutEquipmentFilter.apply(
            starter,
            0,
            setOf(TrainingEquipment.STABLE_CHAIR, TrainingEquipment.MAT)
        )
        assertTrue(result.canStart)
        assertTrue(result.program.moves.any { it.name == "Đứng lên – ngồi xuống" })
        assertTrue(result.program.moves.any { it.name == "Chống đẩy tường" })
        assertFalse(result.program.moves.any {
            it.equipmentOptions.contains(TrainingEquipment.DUMBBELL) ||
                it.equipmentOptions.contains(TrainingEquipment.RESISTANCE_BAND)
        })
    }

}
