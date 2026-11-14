package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessPlanAdvisorTest {
    private fun prefs(goal: Int = 0, energy: Int = 1, level: Int = 1,
                      minutes: Int = 20, equipment: Int = 0, impact: Int = 0,
                      additionalEquipment: Set<TrainingEquipment> = emptySet()) =
        FitnessPlanAdvisor.Preferences(
            goal, energy, level, minutes, equipment, impact, additionalEquipment
        )

    @Test fun painOrInjuryBlocksWorkoutAndRequiresProfessionalReview() {
        for (goal in 0..5) for (energy in 0..2) for (level in 0..2) {
            val result = FitnessPlanAdvisor.recommend(
                prefs(goal = goal, energy = energy, level = level, impact = 2))
            assertEquals("", result.programId)
            assertEquals(0, result.minutes)
            assertTrue(result.requiresProfessionalReview)
            assertTrue(result.explanation.contains("chuyên gia y tế"))
        }
    }

    @Test fun ordinaryRecommendationsRemainStartable() {
        val result = FitnessPlanAdvisor.recommend(prefs())
        assertTrue(result.programId.isNotBlank())
        assertTrue(!result.requiresProfessionalReview)
        assertTrue(result.minutes in 5..120)
    }

    @Test fun lowImpactAvoidsAdvancedWorkout() {
        for (goal in 0..5) {
            val result = FitnessPlanAdvisor.recommend(
                prefs(goal = goal, level = 2, equipment = 2, impact = 1))
            assertTrue(result.programId == "mobility" || result.programId == "lowcardio")
        }
    }

    @Test fun lowEnergyOverridesAdvancedGoal() {
        assertEquals("recovery", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, energy = 0, level = 2, equipment = 2)).programId)
    }

    @Test fun shortSessionSelectsGoalEnergyAndImpactAwarePlans() {
        assertEquals("quick", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, minutes = 10)).programId)
        assertEquals("quicklowcardio", FitnessPlanAdvisor.recommend(
            prefs(goal = 1, minutes = 10)).programId)
        assertEquals("quickmobility", FitnessPlanAdvisor.recommend(
            prefs(goal = 2, minutes = 10)).programId)
        assertEquals("quickrecovery", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, energy = 0, minutes = 10)).programId)
        assertEquals("quicklowcardio", FitnessPlanAdvisor.recommend(
            prefs(goal = 1, impact = 1, minutes = 10)).programId)
        assertEquals("quickmobility", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, impact = 1, minutes = 10)).programId)
    }

    @Test fun resistanceEquipmentSelectsItsOwnStrengthProgram() {
        assertEquals("full", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 2, equipment = 0)).programId)
        assertEquals("advanced", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 2, equipment = 1)).programId)
        assertEquals("weighted", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 2, equipment = 2)).programId)
        assertEquals("weighted", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, equipment = 2)).programId)
    }

    @Test fun beginnersDoNotReceiveLoadedStrengthPlanEvenWhenTheyOwnEquipment() {
        assertEquals("starter", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 0, equipment = 2)).programId)
    }

    @Test fun fatigueAndLowImpactStillOverrideResistanceEquipment() {
        assertEquals("recovery", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, energy = 0, level = 2, equipment = 2)).programId)
        assertEquals("mobility", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 2, equipment = 2, impact = 1)).programId)
    }

    @Test fun stableChairSelectionUsesAChairSpecificStrengthPlan() {
        val result = FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 0, equipment = 3)
        )
        assertEquals("chair", result.programId)
        assertTrue(result.explanation.contains("ghế vững chắc"))
    }

    @Test fun chairPlanContainsOnlyMovementsAvailableWithAStableChair() {
        val chairPlan = TrainingCatalog.byId("chair")
        val filtered = WorkoutEquipmentFilter.apply(chairPlan, equipmentSelection = 3)
        assertTrue(filtered.canStart)
        assertTrue(filtered.program.moves.any {
            it.name == "Đứng lên – ngồi xuống" &&
                TrainingEquipment.STABLE_CHAIR in it.equipmentOptions
        })
        assertTrue(filtered.program.moves.any {
            it.name == "Nâng gối ngồi ghế" &&
                TrainingEquipment.STABLE_CHAIR in it.equipmentOptions
        })
    }

    @Test fun additionalResistanceEquipmentCanBeCombinedWithChairSelection() {
        val result = FitnessPlanAdvisor.recommend(
            prefs(
                goal = 0,
                level = 1,
                equipment = 3,
                additionalEquipment = setOf(TrainingEquipment.DUMBBELL)
            )
        )
        assertEquals("weighted", result.programId)
    }

    @Test fun chairAsAnAdditionalToolIsAlsoConsideredByTheAdvisor() {
        val result = FitnessPlanAdvisor.recommend(
            prefs(
                goal = 0,
                level = 1,
                equipment = 0,
                additionalEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
            )
        )
        assertEquals("chair", result.programId)
    }

    @Test fun allFourPrimaryEquipmentSelectionsAreAccepted() {
        for (equipment in 0..3) {
            val result = FitnessPlanAdvisor.recommend(
                prefs(goal = 0, level = 1, equipment = equipment)
            )
            assertTrue("equipment=$equipment should return a catalog program", result.programId.isNotBlank())
            assertTrue(TrainingCatalog.programs.any { it.id == result.programId })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRestrictionIsRejected() {
        FitnessPlanAdvisor.recommend(prefs(impact = 3))
    }
}
