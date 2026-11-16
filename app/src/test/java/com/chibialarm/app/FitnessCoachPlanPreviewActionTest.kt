package com.chibialarm.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class FitnessCoachPlanPreviewActionTest {
    private fun completeContext() = FitnessNaturalRequest.Result(
        goal = 0,
        level = 0,
        energy = 1,
        minutes = 15,
        equipment = 0,
        impact = 0,
        reply = "structured selections only",
        avoidWristLoad = false,
        avoidFloorPositions = false,
        avoidOverheadReach = false,
        additionalEquipment = emptySet()
    )

    private fun input(activity: FitnessActivity): EditText {
        val field = FitnessActivity::class.java.getDeclaredField("coachRequestInput")
            .apply { isAccessible = true }
        return field.get(activity) as EditText
    }

    private fun findButton(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findButton(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    @Test fun completedConversationOpensTheExistingCuratedRecommendationPreview() {
        val intent = Intent()
        CoachFitnessHandoff.addToFitnessIntent(completeContext(), intent)
        val controller = Robolectric.buildActivity(FitnessActivity::class.java, intent).setup()
        val activity = controller.get()

        input(activity).setText("xem giáo án")
        val understand = findButton(activity.window.decorView, "💬 Chibi hiểu yêu cầu của tôi")
        assertNotNull(understand)
        understand!!.performClick()

        val coachDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(coachDialog)
        val previewAction = coachDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        assertTrue("A complete, non-injury profile should enable the preview action", previewAction.isEnabled)
        assertEquals("Xem giáo án", previewAction.text.toString())
        previewAction.performClick()

        val recommendation = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("The curated recommendation dialog should open", recommendation)
        val message = recommendation.findViewById<TextView>(android.R.id.message)
        assertNotNull(message)
        assertTrue(message.text.toString().contains("15 phút"))
        controller.pause().stop().destroy()
    }

    @Test fun incompleteConversationCannotSkipRequiredPersonalizationQuestions() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()

        input(activity).setText("giãn cơ 15 phút")
        val understand = findButton(activity.window.decorView, "💬 Chibi hiểu yêu cầu của tôi")
        assertNotNull(understand)
        understand!!.performClick()

        val coachDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(coachDialog)
        val previewAction = coachDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        assertFalse("The plan preview stays disabled while required profile answers are missing", previewAction.isEnabled)
        assertEquals("Chưa đủ thông tin", previewAction.text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun injuryStopCannotBeOverriddenByThePlanPreviewAction() {
        val intent = Intent()
        CoachFitnessHandoff.addToFitnessIntent(completeContext().copy(impact = 2), intent)
        val controller = Robolectric.buildActivity(FitnessActivity::class.java, intent).setup()
        val activity = controller.get()

        input(activity).setText("xem giáo án")
        val understand = findButton(activity.window.decorView, "💬 Chibi hiểu yêu cầu của tôi")
        assertNotNull(understand)
        understand!!.performClick()

        val coachDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(coachDialog)
        assertFalse("A reported injury must keep the recommendation action disabled",
            coachDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled)
        controller.pause().stop().destroy()
    }
}
