package com.chibialarm.app

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class FitnessActivityProgressionPreviewTest {
    @Before fun prepareGuestFitnessData() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE).edit()
            .putInt("recommendEquipment", 2)
            .putInt("fitTimeMinutes", 30)
            .commit()
    }

    private fun workout(date: LocalDate) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = 20,
        note = "private note",
        sessionId = UUID.randomUUID().toString(),
        completedMoves = 2,
        selfReportedSets = 2,
        setLog = "Kéo tạ đơn hoặc dây kháng lực: hiệp 1: 8 lần, hiệp 2: 8 lần",
        effortLog = "Kéo tạ đơn hoặc dây kháng lực: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức"
    )

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
        }
    }

    @Test fun steadyProgressActionShowsAConcretePreviewBeforeStartingTheNextSession() {
        val today = LocalDate.now()
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            workout(today.minusDays(4)),
            workout(today.minusDays(2)),
            workout(today)
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val action = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .first { it.text.toString() == "🌱 Xem bước tiến triển có kiểm soát" }
        assertEquals(View.VISIBLE, action.visibility)

        action.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
        assertTrue(message.contains("9 lần/hiệp"))
        assertTrue(message.contains("Giữ 2 hiệp tối thiểu"))
        assertTrue(message.contains("Không tăng tải"))

        controller.pause().stop().destroy()
    }

    @Test fun libraryPreviewOffersOptInStepNarrationWithoutOpeningMicrophone() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val libraryButton = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .firstOrNull { it.text.toString().startsWith("📖 Xem bài") }
        assertNotNull("The offline exercise library should expose a preview action", libraryButton)

        libraryButton!!.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        val dialogRoot = dialog.findViewById<ViewGroup>(android.R.id.custom)
        val dialogViews = dialogRoot?.let(::views).orEmpty()
        val autoSpeak = dialogViews.filterIsInstance<CheckBox>()
            .firstOrNull { it.tag == "fitness-library-step-auto-speak" }

        assertNotNull("The preview should expose a separate opt-in TTS control", autoSpeak)
        assertFalse(autoSpeak!!.isChecked)
        assertFalse("TTS must be enabled on the parent screen before auto narration is available", autoSpeak.isEnabled)
        assertTrue(autoSpeak.contentDescription.toString().contains("không mở microphone"))
        assertTrue(dialogViews.filterIsInstance<Button>().any {
            it.text.toString() == "🔊 Nghe bước này"
        })

        dialog.dismiss()
        controller.pause().stop().destroy()
    }


    @Test fun libraryPreviewExplainsTheCurrentMoveAndRefreshesTheReasonWhenNavigating() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val libraryButton = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .firstOrNull { it.text.toString().startsWith("📖 Xem bài") }
        assertNotNull("The exercise library should expose a preview action", libraryButton)

        libraryButton!!.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val dialogRoot = dialog.findViewById<ViewGroup>(android.R.id.custom)
        val dialogViews = dialogRoot?.let(::views).orEmpty()
        val rationale = dialogViews.filterIsInstance<TextView>()
            .firstOrNull { it.tag == "fitness-library-selection-rationale" }

        assertNotNull("The preview should explain why the selected move is compatible", rationale)
        val firstExplanation = rationale!!.text.toString()
        assertTrue(firstExplanation.contains("Vì sao bài này phù hợp"))
        assertTrue(firstExplanation.contains("Bài 1/"))
        assertTrue(firstExplanation.contains("Nhóm cơ chính theo thư viện"))
        val rationaleSpeech = dialogViews.filterIsInstance<Button>()
            .firstOrNull { it.tag == "fitness-library-selection-rationale-speak" }
        assertNotNull("The current exercise rationale should be readable with the existing TTS engine", rationaleSpeech)
        assertTrue(rationaleSpeech!!.contentDescription.contains("không mở microphone"))
        assertTrue(rationaleSpeech.contentDescription.contains("đồng ý riêng"))

        val nextMove = dialogViews.filterIsInstance<Button>()
            .firstOrNull { it.text.toString() == "Bài tiếp →" }
        assertNotNull("The preview should allow moving to the next exercise", nextMove)
        nextMove!!.performClick()

        val secondExplanation = rationale.text.toString()
        assertTrue(secondExplanation.contains("Bài 2/"))
        assertNotEquals("The rationale must refresh for the newly selected move", firstExplanation, secondExplanation)

        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun libraryVoiceNavigationRequiresExplicitPrivacyConfirmation() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val libraryButton = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .firstOrNull { it.text.toString().startsWith("📖 Xem bài") }
        assertNotNull(libraryButton)

        libraryButton!!.performClick()
        val previewDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(previewDialog)
        val previewRoot = previewDialog.findViewById<ViewGroup>(android.R.id.custom)
        val previewViews = previewRoot?.let(::views).orEmpty()
        val voiceButton = previewViews.filterIsInstance<Button>()
            .firstOrNull { it.text.toString().startsWith("🎙 Nói lệnh") }

        assertNotNull("Voice navigation should be available from the exercise walkthrough", voiceButton)
        assertTrue(voiceButton!!.contentDescription.toString().contains("Chibi không nghe nền"))

        voiceButton.performClick()
        val privacyDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(privacyDialog)
        assertTrue(privacyDialog.isShowing)
        val disclosure = privacyDialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
        assertTrue(disclosure.contains("Chibi không nghe nền"))
        assertTrue(disclosure.contains("Chibi chỉ nhận văn bản"))
        assertTrue(disclosure.contains("có thể xử lý âm thanh trực tuyến"))
        assertTrue(disclosure.contains("quyền microphone"))

        privacyDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(privacyDialog.isShowing)
        previewDialog.dismiss()
        controller.pause().stop().destroy()
    }

}
