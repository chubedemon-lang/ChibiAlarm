package com.chibialarm.app

import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FitnessCoachSessionSnapshotTest {
    @Test fun injuryReportSurvivesRotationWithoutPersistingTranscriptOrHealthPreferences() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val conversationField = FitnessActivity::class.java.getDeclaredField("conversation").apply {
            isAccessible = true
        }
        conversationField.set(activity, FitnessNaturalRequest.Result(
            goal = 2, level = 1, minutes = 20, equipment = 0, impact = 2,
            reply = "raw speech must not be retained"
        ))
        val spinnerField = FitnessActivity::class.java.getDeclaredField("impactRestrictionSpinner").apply {
            isAccessible = true
        }
        (spinnerField.get(activity) as Spinner).setSelection(0)

        controller.recreate()
        val restored = controller.get()
        val result = conversationField.get(restored) as FitnessNaturalRequest.Result
        assertEquals(2, result.impact)
        assertEquals(2, (spinnerField.get(restored) as Spinner).selectedItemPosition)
        assertEquals(2, FitnessCoachSessionSnapshot.effectiveImpact(result.impact, 0))
        assertFalse(restored.getSharedPreferences("chibi_fitness_offline", 0).contains("impact"))
        assertFalse(
            FitnessCoachSessionSnapshot.capture(result, 0).toString()
                .contains("raw speech must not be retained")
        )
        controller.pause().stop().destroy()
    }

    @Test fun manuallySelectedInjuryAlsoSurvivesRotationWhenConversationIsEmpty() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val spinnerField = FitnessActivity::class.java.getDeclaredField("impactRestrictionSpinner").apply {
            isAccessible = true
        }
        (spinnerField.get(controller.get()) as Spinner).setSelection(2)

        controller.recreate()
        val restored = controller.get()
        val resultField = FitnessActivity::class.java.getDeclaredField("conversation").apply {
            isAccessible = true
        }
        val restoredResult = resultField.get(restored) as FitnessNaturalRequest.Result
        assertEquals(2, restoredResult.impact)
        assertEquals(2, (spinnerField.get(restored) as Spinner).selectedItemPosition)
        controller.pause().stop().destroy()
    }

    @Test fun lowImpactPreferenceCanBeChangedButInjuryCannotBeSilentlyCleared() {
        assertEquals(0, FitnessCoachSessionSnapshot.effectiveImpact(1, 0))
        assertEquals(1, FitnessCoachSessionSnapshot.effectiveImpact(null, 1))
        assertEquals(2, FitnessCoachSessionSnapshot.effectiveImpact(2, 0))
        assertEquals(2, FitnessCoachSessionSnapshot.effectiveImpact(null, 2))
    }

    @Test fun snapshotContainsOnlyValidatedCoarseSelections() {
        val source = FitnessNaturalRequest.Result(
            goal = 2, level = 1, energy = 0, minutes = 15, equipment = 0,
            impact = 2, reply = "private transcript text"
        )
        val snapshot = FitnessCoachSessionSnapshot.capture(source, 0)!!
        assertEquals(2, snapshot.restore()?.impact)
        assertFalse(snapshot.toString().contains("private transcript text"))
        assertNull(FitnessCoachSessionSnapshot(goal = 99).restore())
        assertNull(FitnessCoachSessionSnapshot(minutes = 999).restore())
        assertNull(FitnessCoachSessionSnapshot().restore())
    }

    @Test fun wristLoadPreferenceSurvivesRotationWithoutPersistingFreeText() {
        val source = FitnessNaturalRequest.Result(
            reply = "private speech transcript",
            avoidWristLoad = true
        )
        val snapshot = FitnessCoachSessionSnapshot.capture(source, 0, true)!!
        assertEquals(true, snapshot.avoidWristLoad)
        assertEquals(true, snapshot.restore()?.avoidWristLoad)
        assertFalse(snapshot.toString().contains("private speech transcript"))
        val cleared = FitnessCoachSessionSnapshot.capture(source.copy(avoidWristLoad = null), 0, false)!!
        assertEquals(false, cleared.restore()?.avoidWristLoad)
    }



    @Test fun overheadReachPreferenceSurvivesRotationWithoutPersistingFreeText() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val checkboxField = FitnessActivity::class.java.getDeclaredField("overheadReachCheckBox").apply {
            isAccessible = true
        }
        val checkbox = checkboxField.get(controller.get()) as android.widget.CheckBox
        checkbox.isChecked = true

        controller.recreate()
        val restored = controller.get()
        val restoredCheckbox = checkboxField.get(restored) as android.widget.CheckBox
        assertTrue(restoredCheckbox.isChecked)
        assertFalse(restored.getSharedPreferences("chibi_fitness_offline", 0)
            .contains("avoidOverheadReach"))
        controller.pause().stop().destroy()

        val source = FitnessNaturalRequest.Result(
            reply = "private speech transcript",
            avoidOverheadReach = true
        )
        val snapshot = FitnessCoachSessionSnapshot.capture(
            source, 0, selectedAvoidOverheadReach = true
        )!!
        assertTrue(snapshot.restore()?.avoidOverheadReach == true)
        assertFalse(snapshot.toString().contains("private speech transcript"))
        val cleared = FitnessCoachSessionSnapshot.capture(
            source.copy(avoidOverheadReach = null), 0, selectedAvoidOverheadReach = false
        )!!
        assertEquals(false, cleared.restore()?.avoidOverheadReach)
    }

    @Test fun floorPositionPreferenceSurvivesRotationSnapshotWithoutFreeText() {
        val source = FitnessNaturalRequest.Result(
            reply = "private speech transcript",
            avoidFloorPositions = true
        )
        val snapshot = FitnessCoachSessionSnapshot.capture(
            source, 0, selectedAvoidFloorPositions = true
        )!!
        assertEquals(true, snapshot.avoidFloorPositions)
        assertEquals(true, snapshot.restore()?.avoidFloorPositions)
        assertFalse(snapshot.toString().contains("private speech transcript"))
        val cleared = FitnessCoachSessionSnapshot.capture(
            source.copy(avoidFloorPositions = null), 0, selectedAvoidFloorPositions = false
        )!!
        assertEquals(false, cleared.restore()?.avoidFloorPositions)
    }


    @Test fun stableChairEquipmentSelectionSurvivesTheInMemoryCoachSnapshot() {
        val source = FitnessNaturalRequest.Result(
            reply = "coarse chair preference",
            equipment = 3
        )
        val snapshot = FitnessCoachSessionSnapshot.capture(source, 0)!!
        assertEquals(3, snapshot.restore()?.equipment)
        assertNull(FitnessCoachSessionSnapshot(equipment = 4).restore())
    }

}
