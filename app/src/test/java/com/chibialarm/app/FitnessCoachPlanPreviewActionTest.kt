package com.chibialarm.app

import android.content.Intent
import android.os.Looper
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
import org.robolectric.Shadows
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
        avoidKneeling = false,
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

    private fun collectText(view: View): List<String> {
        val own = if (view is TextView) listOf(view.text.toString()) else emptyList()
        val children = if (view is ViewGroup) {
            (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
        } else {
            emptyList()
        }
        return own + children
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
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val coachDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(coachDialog)
        val previewAction = coachDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        assertTrue("A complete, non-injury profile should enable the preview action", previewAction.isEnabled)
        assertEquals("Xem giáo án", previewAction.text.toString())
        previewAction.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val recommendation = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("The curated recommendation dialog should open", recommendation)
        val previewText = collectText(recommendation.window!!.decorView).joinToString("\n")
        assertTrue(
            "Plan preview did not show the selected 15-minute duration: $previewText",
            previewText.contains("15 phút")
        )
        controller.pause().stop().destroy()
    }

    @Test fun incompleteConversationCannotSkipRequiredPersonalizationQuestions() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()

        input(activity).setText("giãn cơ 15 phút")
        val understand = findButton(activity.window.decorView, "💬 Chibi hiểu yêu cầu của tôi")
        assertNotNull(understand)
        understand!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

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
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Injury is intentionally handled as an inline stop response, not a plan-preview dialog.
        val transcriptField = FitnessActivity::class.java.getDeclaredField("coachTranscriptView")
            .apply { isAccessible = true }
        val transcript = transcriptField.get(activity) as TextView
        assertTrue(transcript.text.toString().contains("Chibi không đề xuất đổi bài"))

        val recommendation = findButton(activity.window.decorView, "🐰 Gợi ý chương trình phù hợp")
        assertNotNull(recommendation)
        recommendation!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val stopDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("The explicit recommendation action must still show the injury stop", stopDialog)
        val message = stopDialog.findViewById<TextView>(android.R.id.message)
        assertNotNull(message)
        assertTrue(message.text.toString().contains("Bạn báo đang đau hoặc có chấn thương"))
        assertEquals("Đã hiểu", stopDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text.toString())
        controller.pause().stop().destroy()
    }
}
