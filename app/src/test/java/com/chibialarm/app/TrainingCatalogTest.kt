package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun exerciseRestRecommendationsAreBoundedAndMatchEffort() {
        val moves = TrainingCatalog.allMoves.associateBy { it.name }
        assertEquals(60, moves.getValue("Squat không tạ").restSeconds)
        assertEquals(60, moves.getValue("Đứng lên – ngồi xuống").restSeconds)
        assertEquals(75, moves.getValue("Kéo tạ đơn hoặc dây kháng lực").restSeconds)
        assertEquals(30, moves.getValue("Siết bả vai đứng").restSeconds)
        assertNull(WorkoutSetTracker.restGuidance(moves.getValue("Đi bộ nhanh tại chỗ").dose,
            moves.getValue("Đi bộ nhanh tại chỗ").restSeconds))
        assertTrue(moves.values.all { it.restSeconds in 15..180 })
    }

    @Test fun programIdentifiersRemainUnique() {
        val ids = TrainingCatalog.programs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun wristNeutralAlternativesHaveStepwiseGuidance() {
        val forearmPress = TrainingCatalog.allMoves.first { it.name == "Ép ngực tường bằng cẳng tay" }
        val scapular = TrainingCatalog.allMoves.first { it.name == "Siết bả vai đứng" }
        assertTrue(forearmPress.steps.contains("cẳng tay"))
        assertTrue(forearmPress.steps.contains("cổ tay thả lỏng"))
        assertTrue(forearmPress.avoid.contains("đau"))
        assertTrue(scapular.steps.contains("bả vai"))
        assertTrue(scapular.dose.contains("hiệp"))
    }


}