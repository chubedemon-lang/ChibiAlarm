package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutExtensionSelectorTest {
    private fun preferences(
        goal: Int = 0,
        energy: Int = 1,
        equipment: Int = 0,
        impact: Int = 0,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        additionalEquipment: Set<TrainingEquipment> = emptySet()
    ) = WorkoutExtensionPreferences(goal, energy, equipment, impact, constraints, additionalEquipment)

    @Test fun strengthGoalAddsDistinctHipAndAccessoryFamilies() {
        val program = TrainingCatalog.byId("full")
        val plan = WorkoutDurationPlanner.build(program, 1, 30, preferences(goal = 0))
        assertEquals(program.moves, plan.moves.filterNot { it in plan.extensionMoves })
        assertTrue(plan.extensionMoves.any { it.name == "Dang chân sang bên có điểm tựa" })
        assertTrue(plan.extensionMoves.any { it.name == "Nhón gót" })
        assertEquals(plan.moves.size, plan.moves.map { it.name }.distinct().size)
        assertEquals("Thả lỏng", plan.moves.last().name)
    }

    @Test fun cardioGoalSelectsCardioSpecificExtension() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), 1, 30, preferences(goal = 1)
        )
        assertTrue(plan.extensionMoves.any { it.name == "Nâng gối luân phiên có điểm tựa" })
    }

    @Test fun resistanceMovesRequireReportedEquipmentAndExperience() {
        val program = TrainingCatalog.byId("full")
        val withoutEquipment = WorkoutDurationPlanner.build(program, 1, 30, preferences(goal = 0, equipment = 0))
        val withEquipment = WorkoutDurationPlanner.build(program, 1, 30, preferences(goal = 0, equipment = 2))
        val beginner = WorkoutDurationPlanner.build(program, 0, 30, preferences(goal = 0, equipment = 2))
        assertFalse(withoutEquipment.extensionMoves.any { it.name.contains("tạ đơn") || it.name.contains("dây kháng lực") })
        assertTrue(withEquipment.extensionMoves.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        assertFalse(beginner.extensionMoves.any { it.name.contains("tạ đơn") || it.name.contains("dây kháng lực") })
    }

    @Test fun wristRestrictionIsAppliedToEveryExtensionCandidate() {
        val constraints = WorkoutConstraintProfile(avoidWristLoad = true)
        val filtered = WorkoutConstraintFilter.apply(TrainingCatalog.byId("full"), constraints)
        val plan = WorkoutDurationPlanner.build(
            filtered.program, 1, 30,
            preferences(goal = 0, equipment = 2, constraints = constraints)
        )
        assertTrue(plan.extensionMoves.isNotEmpty())
        assertTrue(plan.extensionMoves.all { WorkoutConstraintFilter.isAllowed(it, constraints) })
        assertFalse(plan.extensionMoves.any { it.name.contains("tạ đơn") || it.name.contains("dây kháng lực") })
    }

    @Test fun lowEnergyAndPainDoNotAddExtraTrainingVolume() {
        val program = TrainingCatalog.byId("full")
        val tired = WorkoutDurationPlanner.build(program, 1, 30, preferences(energy = 0))
        val pain = WorkoutDurationPlanner.build(program, 1, 30, preferences(impact = 2))
        assertTrue(tired.extensionMoves.isEmpty())
        assertTrue(pain.extensionMoves.isEmpty())
    }

    @Test fun extensionBlockNeverExceedsAvailableTargetTimeOrMoveCap() {
        for (level in 0..2) {
            val plan = WorkoutDurationPlanner.build(
                TrainingCatalog.byId("full"), level, 30, preferences(goal = 0)
            )
            assertTrue(plan.estimatedSeconds <= 30 * 60)
            assertTrue(plan.extensionMoves.size <= if (level == 0) 2 else 3)
            assertEquals(plan.moves.size, plan.moves.map { it.name }.distinct().size)
        }
    }

    @Test fun extensionSelectionDoesNotCloneMovesAlreadyInTheProgram() {
        TrainingCatalog.programs.forEach { program ->
            val plan = WorkoutDurationPlanner.build(program, 1, 60, preferences(goal = 0))
            assertTrue(program.moves.none { base -> plan.extensionMoves.any { it.name == base.name } })
            assertEquals(plan.moves.size, plan.moves.map { it.name }.distinct().size)
        }
    }

    @Test fun stableChairSelectionCanUseOnlyCuratedChairExtensions() {
        val plan = WorkoutDurationPlanner.build(
            TrainingCatalog.byId("full"), 1, 30,
            preferences(goal = 1, equipment = 3)
        )
        val available = setOf(TrainingEquipment.STABLE_CHAIR)

        assertTrue(plan.extensionMoves.any {
            TrainingEquipment.STABLE_CHAIR in it.equipmentOptions
        })
        assertTrue(plan.extensionMoves.all {
            TrainingEquipmentAvailability.allows(it, available)
        })
        assertFalse(plan.extensionMoves.any { it.requiresExternalLoad })
    }

    @Test fun additionalToolsCanDriveEquipmentAwareExtensionsWithoutChangingTheLegacyChoice() {
        val program = TrainingCatalog.byId("full")
        val preferences = preferences(
            goal = 0,
            equipment = 0,
            additionalEquipment = setOf(
                TrainingEquipment.DUMBBELL,
                TrainingEquipment.STABLE_CHAIR
            )
        )
        val plan = WorkoutDurationPlanner.build(program, 1, 30, preferences)
        val available = setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.STABLE_CHAIR)

        assertTrue(plan.extensionMoves.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        assertTrue(plan.extensionMoves.all {
            TrainingEquipmentAvailability.allows(it, available)
        })
        assertFalse(plan.extensionMoves.any { it.name == "Đẩy vai với tạ đơn hoặc dây" } &&
            TrainingEquipment.STABLE_CHAIR !in available)
    }

}