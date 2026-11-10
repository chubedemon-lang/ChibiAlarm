package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCatalogTest {
    @Test fun resistanceProgramStatesItsEquipmentRequirement() {
        val program = TrainingCatalog.byId("weighted")
        assertTrue(program.subtitle.contains("tạ đơn nhẹ"))
        assertTrue(program.subtitle.contains("dây kháng lực"))
        assertEquals("Sức mạnh", program.category)
    }

    @Test fun resistanceExercisesExplainSetupEasierOptionAndSafety() {
        val program = TrainingCatalog.byId("weighted")
        val resistanceMoves = program.moves.filter {
            it.name.contains("tạ đơn") || it.name.contains("dây kháng lực")
        }
        assertEquals(3, resistanceMoves.size)
        assertTrue(resistanceMoves.all { it.steps.isNotBlank() && it.easier.isNotBlank() && it.avoid.isNotBlank() })
        assertTrue(resistanceMoves.all { it.dose.contains("hiệp") })
    }

    @Test fun programIdentifiersRemainUnique() {
        val ids = TrainingCatalog.programs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}