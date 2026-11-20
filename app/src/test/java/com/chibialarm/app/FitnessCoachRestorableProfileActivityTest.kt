package com.chibialarm.app

import android.os.Bundle
import android.widget.CheckBox
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FitnessCoachRestorableProfileActivityTest {
    private fun field(name: String) =
        FitnessActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun savedActivityRestoresSelectionsAndKeepsTranscriptOutOfTheBundle() {
        val firstController = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val first = firstController.get()

        (field("recommendationGoalSpinner").get(first) as Spinner).setSelection(2)
        (field("recommendationEnergySpinner").get(first) as Spinner).setSelection(0)
        (field("recommendationEquipmentSpinner").get(first) as Spinner).setSelection(4)
        (field("level").get(first) as Spinner).setSelection(1)
        (field("length").get(first) as Spinner).setSelection(
            WorkoutDurationChoices.indexForMinutes(25)
        )
        (field("impactRestrictionSpinner").get(first) as Spinner).setSelection(1)
        (field("wristLoadCheckBox").get(first) as CheckBox).isChecked = true
        (field("floorPositionCheckBox").get(first) as CheckBox).isChecked = true
        @Suppress("UNCHECKED_CAST")
        val additional = field("additionalEquipmentCheckBoxes").get(first)
            as Map<TrainingEquipment, CheckBox>
        additional.getValue(TrainingEquipment.MAT).isChecked = true
        additional.getValue(TrainingEquipment.STABLE_CHAIR).isChecked = true

        val transcript = field("coachTranscript").get(first) as FitnessCoachChatTranscript
        transcript.addUser("private transcript must not be saved")
        val bundle = Bundle()
        FitnessActivity::class.java.getDeclaredMethod(
            "onSaveInstanceState",
            Bundle::class.java
        ).apply { isAccessible = true }.invoke(first, bundle)

        assertFalse(bundle.toString().contains("private transcript must not be saved"))
        assertEquals(1, FitnessCoachRestorableProfile.fromBundle(bundle)!!.impact)
        firstController.pause().stop().destroy()

        val restoredController = Robolectric.buildActivity(FitnessActivity::class.java)
            .create(bundle).start().resume().visible()
        val restored = restoredController.get()
        assertEquals(2, (field("recommendationGoalSpinner").get(restored) as Spinner).selectedItemPosition)
        assertEquals(0, (field("recommendationEnergySpinner").get(restored) as Spinner).selectedItemPosition)
        assertEquals(4, (field("recommendationEquipmentSpinner").get(restored) as Spinner).selectedItemPosition)
        assertEquals(1, (field("level").get(restored) as Spinner).selectedItemPosition)
        assertEquals(25, WorkoutDurationChoices.minutes[
            (field("length").get(restored) as Spinner).selectedItemPosition
        ])
        assertEquals(1, (field("impactRestrictionSpinner").get(restored) as Spinner).selectedItemPosition)
        assertTrue((field("wristLoadCheckBox").get(restored) as CheckBox).isChecked)
        assertTrue((field("floorPositionCheckBox").get(restored) as CheckBox).isChecked)
        @Suppress("UNCHECKED_CAST")
        val restoredAdditional = field("additionalEquipmentCheckBoxes").get(restored)
            as Map<TrainingEquipment, CheckBox>
        assertTrue(restoredAdditional.getValue(TrainingEquipment.MAT).isChecked)
        assertTrue(restoredAdditional.getValue(TrainingEquipment.STABLE_CHAIR).isChecked)
        assertEquals("", (field("coachTranscript").get(restored) as FitnessCoachChatTranscript).render())

        val preferences = restored.getSharedPreferences("chibi_fitness_offline", 0)
        assertEquals(2, preferences.getInt("recommendGoal", -1))
        assertEquals(0, preferences.getInt("recommendEnergy", -1))
        assertEquals(4, preferences.getInt("recommendEquipment", -1))
        assertEquals(1, preferences.getInt("fitLevel", -1))
        assertEquals(25, preferences.getInt("fitTimeMinutes", -1))
        val savedTools = preferences.getString("recommendAdditionalEquipment", "").orEmpty()
        assertTrue(savedTools.contains("MAT"))
        assertTrue(savedTools.contains("STABLE_CHAIR"))
        assertFalse(preferences.contains("impact"))
        assertFalse(preferences.contains("avoidWristLoad"))
        restoredController.pause().stop().destroy()
    }
}
