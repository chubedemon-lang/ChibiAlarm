package com.chibialarm.app

import android.content.Intent
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoachFitnessHandoffTest {
    private val selectedTools = setOf(
        TrainingEquipment.MAT,
        TrainingEquipment.STABLE_CHAIR
    )

    private fun context() = FitnessNaturalRequest.Result(
        goal = 0,
        level = 1,
        energy = 2,
        minutes = 25,
        equipment = 2,
        impact = 1,
        reply = "private spoken transcript must not be transferred",
        avoidWristLoad = true,
        avoidFloorPositions = false,
        avoidOverheadReach = true,
        additionalEquipment = selectedTools
    )

    @Test fun configurationBundleRoundTripKeepsAllCoarseChoicesIncludingExtraTools() {
        val bundle = Bundle()
        CoachFitnessHandoff.saveToBundle(context(), bundle)

        val restored = CoachFitnessHandoff.restoreFromBundle(bundle)

        assertNotNull(restored)
        assertEquals(0, restored!!.goal)
        assertEquals(1, restored.level)
        assertEquals(2, restored.energy)
        assertEquals(25, restored.minutes)
        assertEquals(2, restored.equipment)
        assertEquals(1, restored.impact)
        assertEquals(true, restored.avoidWristLoad)
        assertEquals(false, restored.avoidFloorPositions)
        assertEquals(true, restored.avoidOverheadReach)
        assertEquals(selectedTools, restored.additionalEquipment)
        assertFalse(bundle.containsKey("fitness_reply"))
        assertFalse(bundle.containsKey("fitness_transcript"))
    }

    @Test fun fitnessIntentCarriesAdditionalToolsIntoTheExistingFitnessHandoffContract() {
        val intent = Intent()
        CoachFitnessHandoff.addToFitnessIntent(context(), intent)

        assertEquals(0, intent.getIntExtra("coach_goal", -1))
        assertEquals(25, intent.getIntExtra("coach_minutes", -1))
        assertEquals(
            selectedTools.map { it.name }.sorted(),
            intent.getStringArrayListExtra("coach_additional_equipment")?.toList()
        )

        val controller = Robolectric.buildActivity(FitnessActivity::class.java, intent).setup()
        val field = FitnessActivity::class.java.getDeclaredField("conversation").apply {
            isAccessible = true
        }
        val restored = field.get(controller.get()) as FitnessNaturalRequest.Result
        assertEquals(selectedTools, restored.additionalEquipment)
        assertEquals(true, restored.avoidWristLoad)
        controller.pause().stop().destroy()
    }

    @Test fun absentAdditionalEquipmentStaysAbsentInsteadOfInventingTools() {
        val source = context().copy(additionalEquipment = null)
        val intent = Intent()
        CoachFitnessHandoff.addToFitnessIntent(source, intent)
        assertFalse(intent.hasExtra("coach_additional_equipment"))

        val bundle = Bundle()
        CoachFitnessHandoff.saveToBundle(source, bundle)
        assertFalse(bundle.containsKey("fitness_additional_equipment"))
        assertNull(CoachFitnessHandoff.restoreFromBundle(bundle)?.additionalEquipment)
    }

    @Test fun exactResistanceEquipmentChoicesSurviveCoachHandoffAndRotation() {
        for (selection in listOf(4, 5)) {
            val source = context().copy(
                equipment = selection,
                additionalEquipment = emptySet()
            )
            val bundle = Bundle()
            CoachFitnessHandoff.saveToBundle(source, bundle)
            assertEquals(selection, CoachFitnessHandoff.restoreFromBundle(bundle)?.equipment)

            val intent = Intent()
            CoachFitnessHandoff.addToFitnessIntent(source, intent)
            val controller = Robolectric.buildActivity(FitnessActivity::class.java, intent).setup()
            val field = FitnessActivity::class.java.getDeclaredField("conversation").apply {
                isAccessible = true
            }
            val restored = field.get(controller.get()) as FitnessNaturalRequest.Result
            assertEquals(selection, restored.equipment)
            controller.pause().stop().destroy()
        }
    }

    @Test fun invalidCoarseSelectionsAreRejectedWhenRestoringBundle() {
        val bundle = Bundle().apply {
            putBoolean("fitness_context_present", true)
            putInt("fitness_goal", 99)
            putStringArrayList("fitness_additional_equipment", arrayListOf("MAT"))
        }
        assertNull(CoachFitnessHandoff.restoreFromBundle(bundle))
    }

    @Test fun unknownEquipmentNamesAreIgnoredAndCannotCreateNewCatalogEntries() {
        val bundle = Bundle().apply {
            putBoolean("fitness_context_present", true)
            putInt("fitness_goal", 0)
            putStringArrayList(
                "fitness_additional_equipment",
                arrayListOf("MAT", "UNTRUSTED_TOOL")
            )
        }
        val restored = CoachFitnessHandoff.restoreFromBundle(bundle)
        assertEquals(setOf(TrainingEquipment.MAT), restored?.additionalEquipment)
    }
}
