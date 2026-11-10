package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessPlanAdvisorTest {
    private fun prefs(goal: Int = 0, energy: Int = 1, level: Int = 1,
                      minutes: Int = 20, equipment: Int = 0, impact: Int = 0) =
        FitnessPlanAdvisor.Preferences(goal, energy, level, minutes, equipment, impact)

    @Test fun painOrInjuryNeverSelectsIntenseWorkout() {
        for (goal in 0..5) for (energy in 0..2) for (level in 0..2) {
            val result = FitnessPlanAdvisor.recommend(
                prefs(goal = goal, energy = energy, level = level, impact = 2))
            assertEquals("recovery", result.programId)
            assertTrue(result.minutes <= 10)
        }
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

    @Test fun shortSessionGetsStarterProgram() {
        assertEquals("starter", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, minutes = 10)).programId)
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

    @Test(expected = IllegalArgumentException::class)
    fun invalidRestrictionIsRejected() {
        FitnessPlanAdvisor.recommend(prefs(impact = 3))
    }
}
