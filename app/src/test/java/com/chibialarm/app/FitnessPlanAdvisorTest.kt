package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("weighted", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, equipment = 4)).programId)
        assertEquals("weighted", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, equipment = 5)).programId)
        assertTrue(FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, equipment = 4)).explanation.contains("tạ đơn nhẹ"))
        assertTrue(FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, equipment = 5)).explanation.contains("dây kháng lực"))
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

    @Test fun allSixPrimaryEquipmentSelectionsAreAccepted() {
        for (equipment in 0..5) {
            val result = FitnessPlanAdvisor.recommend(
                prefs(goal = 0, level = 1, equipment = equipment)
            )
            assertTrue("equipment=$equipment should return a catalog program", result.programId.isNotBlank())
            assertTrue(TrainingCatalog.programs.any { it.id == result.programId })
        }
    }

    @Test fun exactResistanceSelectionsFilterOutTheUnownedTool() {
        fun move(name: String, tool: TrainingEquipment) = TrainingMove(
            name = name,
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện chậm",
            easier = "Giảm biên độ",
            avoid = "Dừng nếu đau",
            equipmentOptions = setOf(tool)
        )
        val dumbbellProgram = TrainingProgram(
            "dumbbell-only", "Tạ đơn", "Kiểm thử", "Sức mạnh",
            listOf(move("Kéo tạ", TrainingEquipment.DUMBBELL))
        )
        val bandProgram = TrainingProgram(
            "band-only", "Dây", "Kiểm thử", "Sức mạnh",
            listOf(move("Kéo dây", TrainingEquipment.RESISTANCE_BAND))
        )

        assertTrue(WorkoutEquipmentFilter.apply(dumbbellProgram, 4).canStart)
        assertFalse(WorkoutEquipmentFilter.apply(dumbbellProgram, 5).canStart)
        assertTrue(WorkoutEquipmentFilter.apply(bandProgram, 5).canStart)
        assertFalse(WorkoutEquipmentFilter.apply(bandProgram, 4).canStart)
    }

    @Test fun elevenToFifteenMinuteRequestsChooseGoalEnergyAndEquipmentSpecificPlans() {
        assertEquals("quick15", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 0, minutes = 11)).programId)
        assertEquals("quick15lowcardio", FitnessPlanAdvisor.recommend(
            prefs(goal = 1, minutes = 15)).programId)
        assertEquals("quick15mobility", FitnessPlanAdvisor.recommend(
            prefs(goal = 2, minutes = 12)).programId)
        assertEquals("quick15recovery", FitnessPlanAdvisor.recommend(
            prefs(energy = 0, minutes = 15)).programId)
        assertEquals("quick15chair", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 0, minutes = 15, equipment = 3)).programId)
        assertEquals("quick15weighted", FitnessPlanAdvisor.recommend(
            prefs(goal = 0, level = 1, minutes = 15, equipment = 4)).programId)
        assertEquals("desk", FitnessPlanAdvisor.recommend(
            prefs(goal = 3, minutes = 15)).programId)
    }

    @Test fun elevenToFifteenMinuteCatalogPlansKeepWarmupCooldownAndStayWithinBudget() {
        val cases = listOf(
            Triple("quick15", prefs(goal = 0, level = 0, minutes = 11), 0),
            Triple("quick15lowcardio", prefs(goal = 1, minutes = 11), 1),
            Triple("quick15mobility", prefs(goal = 2, minutes = 11), 2),
            Triple("quick15recovery", prefs(energy = 0, minutes = 11), 0),
            Triple("quick15chair", prefs(goal = 0, level = 0, minutes = 11, equipment = 3), 0),
            Triple("quick15weighted", prefs(goal = 0, level = 1, minutes = 11, equipment = 4), 0)
        )

        cases.forEach { (id, preference, goal) ->
            val program = TrainingCatalog.byId(id)
            val plan = WorkoutDurationPlanner.build(
                program = program,
                level = preference.level,
                targetMinutes = preference.minutes,
                preferences = WorkoutExtensionPreferences(
                    goal = goal,
                    energy = preference.energy,
                    equipment = preference.equipment,
                    impact = preference.impact,
                    additionalEquipment = preference.additionalEquipment
                )
            )
            assertEquals(id, "Khởi động tại chỗ", plan.moves.first().name)
            assertEquals(id, "Thả lỏng", plan.moves.last().name)
            assertTrue("$id should fit its 11-minute budget", !plan.exceedsTarget)
            assertEquals(id, plan.moves.size, plan.moves.map { it.name }.distinct().size)
        }
    }

    @Test fun quickFifteenMinuteVariantsKeepTheirEquipmentAndImpactBoundaries() {
        val weighted = WorkoutEquipmentFilter.apply(TrainingCatalog.byId("quick15weighted"), 4)
        assertTrue(weighted.canStart)
        assertTrue(weighted.program.moves.all {
            TrainingEquipmentAvailability.allows(
                it, TrainingEquipmentAvailability.fromPreference(4)
            )
        })

        val chair = WorkoutEquipmentFilter.apply(TrainingCatalog.byId("quick15chair"), 3)
        assertTrue(chair.canStart)
        assertTrue(chair.program.moves.all {
            TrainingEquipmentAvailability.allows(
                it, TrainingEquipmentAvailability.fromPreference(3)
            )
        })

        val cardio = TrainingCatalog.byId("quick15lowcardio")
        assertFalse(cardio.moves.any { it.requiresJumping })
        val beginner = TrainingCatalog.byId("quick15")
        assertFalse(beginner.moves.any { it.requiresExternalLoad })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRestrictionIsRejected() {
        FitnessPlanAdvisor.recommend(prefs(impact = 3))
    }
}
