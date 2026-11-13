package com.chibialarm.app

import com.chibialarm.app.fitness.WorkoutMidSessionPlanner
import com.chibialarm.app.fitness.WorkoutMidSessionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(resistanceMoves.all {
            it.equipmentOptions == setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND)
        })
        val chairMoves = setOf(
            "Đứng lên – ngồi xuống",
            "Giãn gân kheo ngồi ghế",
            "Nâng gối ngồi ghế"
        )
        assertTrue(TrainingCatalog.allMoves.filterNot { it.name in resistanceMoves.map(TrainingMove::name) }
            .all { move ->
                if (move.name in chairMoves) {
                    move.equipmentOptions == setOf(TrainingEquipment.STABLE_CHAIR)
                } else {
                    move.equipmentOptions.isEmpty()
                }
            })
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

    @Test fun curatedExtensionMovesHaveCompleteStepwiseGuidance() {
        val extensions = TrainingCatalog.extensionMoves
        assertEquals(3, extensions.size)
        assertTrue(extensions.all {
            it.name.isNotBlank() && it.dose.isNotBlank() &&
                it.steps.isNotBlank() && it.easier.isNotBlank() && it.avoid.isNotBlank()
        })
        assertTrue(extensions.all { it.restSeconds in 15..180 })
    }

    @Test fun lowCardioProgramUsesDistinctMovesInsteadOfRepeatingMarch() {
        val moves = TrainingCatalog.byId("lowcardio").moves
        assertEquals(moves.size, moves.map { it.name }.distinct().size)
        assertTrue(moves.any { it.name == "Nâng gối luân phiên có điểm tựa" })
    }

    @Test fun legacyDurationHelperNeverDuplicatesPrescribedMoves() {
        TrainingCatalog.programs.forEach { program ->
            for (level in 0..2) {
                val adjusted = TrainingCatalog.adjusted(program, level, 60)
                assertEquals(program.moves, adjusted)
                assertEquals(adjusted.size, adjusted.map { it.name }.distinct().size)
            }
        }
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

    @Test fun overheadPressDeclaresItsReachRequirementExplicitly() {
        val weighted = TrainingCatalog.byId("weighted").moves
        val press = weighted.single { it.name == "Đẩy vai với tạ đơn hoặc dây" }
        assertTrue(press.requiresOverheadReach)
        assertFalse(weighted.single { it.name == "Kéo tạ đơn hoặc dây kháng lực" }.requiresOverheadReach)
        assertFalse(weighted.single { it.name == "Gập hông với tạ đơn hoặc dây" }.requiresOverheadReach)
    }

    @Test fun chairDependentMovesDeclareStableChairRequirement() {
        val moves = TrainingCatalog.allMoves.associateBy { it.name }
        val sitToStand = moves.getValue("Đứng lên – ngồi xuống")
        val seatedStretch = moves.getValue("Giãn gân kheo ngồi ghế")
        val seatedKneeRaise = moves.getValue("Nâng gối ngồi ghế")

        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), sitToStand.equipmentOptions)
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), seatedStretch.equipmentOptions)
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), seatedKneeRaise.equipmentOptions)
        assertFalse(TrainingEquipmentAvailability.allows(sitToStand, emptySet()))
        assertFalse(TrainingEquipmentAvailability.allows(seatedKneeRaise, emptySet()))
        assertTrue(
            TrainingEquipmentAvailability.allows(
                seatedKneeRaise,
                setOf(TrainingEquipment.STABLE_CHAIR)
            )
        )
        assertTrue(
            TrainingEquipmentAvailability.allows(
                moves.getValue("Chống đẩy tường"),
                emptySet()
            )
        )
    }

    @Test fun spokenNoChairRequestRemovesOnlyUnstartedChairDependentMoves() {
        val parsed = WorkoutMidSessionRequest.parse("Hôm nay tao không có ghế")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), request.unavailableEquipment)

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val warm = catalog.getValue("Khởi động tại chỗ")
        val chairMove = catalog.getValue("Đứng lên – ngồi xuống")
        val wallPush = catalog.getValue("Chống đẩy tường")
        val cool = catalog.getValue("Thả lỏng")
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(warm, chairMove, wallPush, cool),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 120,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(listOf(warm, wallPush, cool), preview.after)
        assertEquals(listOf(chairMove.name), preview.removedMoves)
    }

    @Test fun noChairRequestRemovesUnstartedSeatedKneeRaiseFromRemainingPlan() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không có ghế")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val warm = catalog.getValue("Khởi động tại chỗ")
        val seatedKneeRaise = catalog.getValue("Nâng gối ngồi ghế")
        val wallPush = catalog.getValue("Chống đẩy tường")
        val cool = catalog.getValue("Thả lỏng")

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(warm, seatedKneeRaise, wallPush, cool),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 120,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(listOf(warm, wallPush, cool), preview.after)
        assertEquals(listOf(seatedKneeRaise.name), preview.removedMoves)
    }

    @Test fun missingChairCannotRewriteAnAlreadyStartedChairDependentMove() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không có ghế")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                catalog.getValue("Đứng lên – ngồi xuống"),
                catalog.getValue("Chống đẩy tường"),
                catalog.getValue("Thả lỏng")
            ),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun noChairCannotRewriteAnAlreadyStartedSeatedKneeRaise() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không có ghế")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                catalog.getValue("Nâng gối ngồi ghế"),
                catalog.getValue("Chống đẩy tường"),
                catalog.getValue("Thả lỏng")
            ),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }
}
