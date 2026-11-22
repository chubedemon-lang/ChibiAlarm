package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanCandidateSelectorTest {
    private fun preferences(
        goal: Int = 0,
        energy: Int = 1,
        level: Int = 1,
        minutes: Int = 20,
        equipment: Int = 0,
        impact: Int = 0,
        additionalEquipment: Set<TrainingEquipment> = emptySet()
    ) = FitnessPlanAdvisor.Preferences(
        goal, energy, level, minutes, equipment, impact, additionalEquipment
    )

    @Test fun compatiblePreferredProgramIsNeverReplaced() {
        val preferred = TrainingCatalog.byId("full")
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = preferred,
            candidates = listOf(TrainingCatalog.byId("quick"), preferred),
            preferences = preferences(minutes = 5),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("full", selection!!.program.id)
        assertFalse(selection.usedFallback)
        assertEquals("", selection.notice)
    }

    @Test fun fallbackKeepsTheRequestedCardioGoalAndShortTimeWindow() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("core"),
            candidates = listOf(
                TrainingCatalog.byId("quickmobility"),
                TrainingCatalog.byId("quicklowcardio")
            ),
            preferences = preferences(goal = 1, minutes = 10),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("quicklowcardio", selection!!.program.id)
        assertTrue(selection.usedFallback)
        assertTrue(selection.notice.contains("10 phút"))
        assertTrue(selection.notice.contains("không phải bài thay thế tương đương y khoa"))
    }

    @Test fun lowEnergyPrefersRecoveryOverTheOriginalCardioGoalWhenPreferredIsUnavailable() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("lowcardio"),
            candidates = listOf(
                TrainingCatalog.byId("quicklowcardio"),
                TrainingCatalog.byId("quickrecovery")
            ),
            preferences = preferences(goal = 1, energy = 0, minutes = 10),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("quickrecovery", selection!!.program.id)
    }

    @Test fun elevenToFifteenMinuteFallbackPrefersTheMatchingShortPlanFamily() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("core"),
            candidates = listOf(
                TrainingCatalog.byId("quicklowcardio"),
                TrainingCatalog.byId("quick15lowcardio")
            ),
            preferences = preferences(goal = 1, minutes = 12),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("quick15lowcardio", selection!!.program.id)
    }

    @Test fun beginnerFallbackNeverSelectsAdvancedOrLoadedStrengthPrograms() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("advanced"),
            candidates = listOf(
                TrainingCatalog.byId("advanced"),
                TrainingCatalog.byId("weighted"),
                TrainingCatalog.byId("starter")
            ),
            preferences = preferences(goal = 5, level = 0, minutes = 20),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("starter", selection!!.program.id)
        assertFalse(selection.program.id in setOf("advanced", "weighted"))
    }

    @Test fun movementConstraintsAreRecheckedBeforeAPlanCanBeSelected() {
        val constraints = WorkoutConstraintProfile(avoidFloorPositions = true)
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("full"),
            candidates = listOf(
                TrainingCatalog.byId("full"),
                TrainingCatalog.byId("lowcardio")
            ),
            preferences = preferences(goal = 1, minutes = 20),
            constraints = constraints
        )

        assertNotNull(selection)
        assertEquals("lowcardio", selection!!.program.id)
        assertTrue(selection.program.moves.all {
            WorkoutConstraintFilter.isAllowed(it, constraints)
        })
    }

    @Test fun lowImpactFallbackCannotReintroduceJumping() {
        val constraints = WorkoutConstraintProfile.fromSessionSelections(impact = 1)
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("advanced"),
            candidates = listOf(
                TrainingCatalog.byId("advanced"),
                TrainingCatalog.byId("quick15lowcardio")
            ),
            preferences = preferences(goal = 1, minutes = 12, impact = 1),
            constraints = constraints
        )

        assertNotNull(selection)
        assertEquals("quick15lowcardio", selection!!.program.id)
        assertFalse(selection.program.moves.any { it.requiresJumping })
    }

    @Test fun exactEquipmentIsRecheckedForFallbackCandidates() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("chair"),
            candidates = listOf(
                TrainingCatalog.byId("chair"),
                TrainingCatalog.byId("weighted")
            ),
            preferences = preferences(
                goal = 0,
                level = 1,
                minutes = 20,
                equipment = 4
            ),
            constraints = WorkoutConstraintProfile()
        )

        assertNotNull(selection)
        assertEquals("weighted", selection!!.program.id)
        assertTrue(selection.program.moves.all {
            TrainingEquipmentAvailability.allows(
                it,
                TrainingEquipmentAvailability.fromPreference(4)
            )
        })
    }

    @Test fun fallbackIsRejectedWhenEveryCompatibleProgramExceedsTheTimeBudget() {
        val longMove = TrainingMove(
            name = "Bài kiểm thử dài",
            dose = "Một lượt",
            steps = "Thực hiện nhẹ nhàng",
            easier = "Dừng nếu không thoải mái",
            avoid = "Không cố quá sức",
            seconds = 600
        )
        val longProgram = TrainingProgram(
            id = "long-only",
            name = "Chương trình dài",
            subtitle = "Kiểm thử",
            category = "Sức mạnh",
            moves = listOf(longMove)
        )

        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("full"),
            candidates = listOf(longProgram),
            preferences = preferences(minutes = 5),
            constraints = WorkoutConstraintProfile()
        )

        assertNull(selection)
    }

    @Test fun injuryGateNeverSelectsAnyFallback() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("starter"),
            candidates = TrainingCatalog.programs,
            preferences = preferences(impact = 2),
            constraints = WorkoutConstraintProfile()
        )

        assertNull(selection)
    }

    @Test fun noCompatibleCandidateReturnsNoPlanInsteadOfGuessing() {
        val selection = WorkoutPlanCandidateSelector.select(
            preferred = TrainingCatalog.byId("core"),
            candidates = emptyList(),
            preferences = preferences(),
            constraints = WorkoutConstraintProfile()
        )

        assertNull(selection)
    }
}
