package com.chibialarm.app

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FitnessCoachRestorableProfileTest {
    private fun profile(
        impact: Int = 1,
        additional: Set<TrainingEquipment> = setOf(
            TrainingEquipment.MAT,
            TrainingEquipment.STABLE_CHAIR
        ),
        avoidKneeling: Boolean? = true
    ) = FitnessCoachRestorableProfile.capture(
        goal = 2,
        level = 1,
        energy = 0,
        minutes = 25,
        equipment = 4,
        impact = impact,
        avoidWristLoad = true,
        avoidFloorPositions = false,
        avoidOverheadReach = true,
        additionalEquipment = additional,
        avoidKneeling = avoidKneeling
    )!!

    @Test fun bundleRoundTripsEveryStructuredChoiceAndSafetySwitch() {
        val original = profile()
        val bundle = Bundle()
        original.writeTo(bundle)

        val restored = FitnessCoachRestorableProfile.fromBundle(bundle)
        assertEquals(original, restored)
        val result = restored!!.restore()
        assertEquals(2, result.goal)
        assertEquals(1, result.level)
        assertEquals(0, result.energy)
        assertEquals(25, result.minutes)
        assertEquals(4, result.equipment)
        assertEquals(1, result.impact)
        assertEquals(true, result.avoidWristLoad)
        assertEquals(false, result.avoidFloorPositions)
        assertEquals(true, result.avoidOverheadReach)
        assertEquals(true, result.avoidKneeling)
        assertEquals(original.additionalEquipment, result.additionalEquipment)
    }

    @Test fun processRestoreKeepsThePainStopLatchAndNeverTurnsItIntoAPlan() {
        val bundle = Bundle()
        profile(impact = 2).writeTo(bundle)

        val result = FitnessCoachRestorableProfile.fromBundle(bundle)!!.restore()
        assertEquals(2, result.impact)
        val recommendation = FitnessPlanAdvisor.recommend(
            FitnessPlanAdvisor.Preferences(
                goal = result.goal!!,
                energy = result.energy!!,
                level = result.level!!,
                minutes = result.minutes!!,
                equipment = result.equipment!!,
                impact = result.impact!!
            )
        )
        assertTrue(recommendation.requiresProfessionalReview)
        assertEquals("", recommendation.programId)
    }

    @Test fun savedStateContainsNoTranscriptChatAccountOrCredentialFields() {
        val bundle = Bundle()
        profile().writeTo(bundle)

        assertTrue(bundle.keySet().none { key ->
            val normalized = key.lowercase()
            normalized.contains("transcript") ||
                normalized.contains("chat") ||
                normalized.contains("account") ||
                normalized.contains("token") ||
                normalized.contains("api_key")
        })
        assertFalse(bundle.toString().contains("private speech"))
        assertFalse(profile().toString().contains("private speech"))
    }

    @Test fun missingVersionFieldsAndUnknownEquipmentFailClosed() {
        assertNull(FitnessCoachRestorableProfile.fromBundle(Bundle()))

        val bundle = Bundle()
        profile().writeTo(bundle)
        bundle.putStringArrayList(
            "fitness_coach_state_additional_equipment",
            arrayListOf("UNKNOWN_TOOL")
        )
        assertNull(FitnessCoachRestorableProfile.fromBundle(bundle))
    }

    @Test fun invalidIndexesAndDuplicateEquipmentCannotBeCaptured() {
        assertNull(FitnessCoachRestorableProfile.capture(
            goal = 9, level = 1, energy = 0, minutes = 25, equipment = 4,
            impact = 0, avoidWristLoad = false, avoidFloorPositions = false,
            avoidOverheadReach = false, additionalEquipment = emptySet()
        ))
        assertNull(FitnessCoachRestorableProfile.capture(
            goal = 2, level = 1, energy = 0, minutes = 121, equipment = 4,
            impact = 0, avoidWristLoad = false, avoidFloorPositions = false,
            avoidOverheadReach = false, additionalEquipment = emptySet()
        ))

        val bundle = Bundle()
        profile().writeTo(bundle)
        bundle.putStringArrayList(
            "fitness_coach_state_additional_equipment",
            arrayListOf("MAT", "MAT")
        )
        assertNull(FitnessCoachRestorableProfile.fromBundle(bundle))
    }


    @Test fun versionOneSavedStateRemainsReadableWithoutInventingKneelingPreference() {
        val bundle = Bundle().apply {
            putInt("fitness_coach_state_version", 1)
            putInt("fitness_coach_state_goal", 2)
            putInt("fitness_coach_state_level", 1)
            putInt("fitness_coach_state_energy", 0)
            putInt("fitness_coach_state_minutes", 25)
            putInt("fitness_coach_state_equipment", 4)
            putInt("fitness_coach_state_impact", 1)
            putBoolean("fitness_coach_state_avoid_wrist", true)
            putBoolean("fitness_coach_state_avoid_floor", false)
            putBoolean("fitness_coach_state_avoid_overhead", true)
            putStringArrayList(
                "fitness_coach_state_additional_equipment",
                arrayListOf("MAT", "STABLE_CHAIR")
            )
        }

        val restored = FitnessCoachRestorableProfile.fromBundle(bundle)
        assertNotNull(restored)
        assertNull(restored!!.avoidKneeling)
        assertNull(restored.restore().avoidKneeling)
    }

}
