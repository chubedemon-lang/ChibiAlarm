package com.chibialarm.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class FitnessSpeechInputTest {
    private fun speechRequestCode(activity: FitnessActivity): Int =
        FitnessActivity::class.java.getDeclaredField("speechRequestCode").let { field ->
            field.isAccessible = true
            field.getInt(activity)
        }

    private fun speechInput(activity: FitnessActivity): EditText =
        FitnessActivity::class.java.getDeclaredField("speechInput").let { field ->
            field.isAccessible = true
            field.get(activity) as EditText
        }

    private fun deliverSpeechResult(activity: FitnessActivity, result: Intent) {
        // onActivityResult is a protected Android lifecycle callback. Invoke the override
        // reflectively so this test exercises the real Activity handler without widening
        // production visibility solely for Robolectric.
        val callback = FitnessActivity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java
        )
        callback.isAccessible = true
        callback.invoke(activity, speechRequestCode(activity), Activity.RESULT_OK, result)
    }

    @Test fun multipleSpeechAlternativesRequireAnExplicitChoiceBeforePrefilling() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val input = speechInput(activity)
        input.setText("nội dung đang nhập")

        val result = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            arrayListOf("cardio 10 phút", "giãn cơ 20 phút")
        )
        deliverSpeechResult(activity, result)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals("nội dung đang nhập", input.text.toString())
        assertEquals(2, dialog!!.listView.count)

        dialog.listView.performItemClick(
            dialog.listView,
            1,
            dialog.listView.getItemIdAtPosition(1)
        )
        assertEquals("giãn cơ 20 phút", input.text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun oneSpeechAlternativeIsPrefilledForReviewButNeverAutoSubmitted() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val input = speechInput(activity)

        val result = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            arrayListOf("  cardio   10 phút  ")
        )
        deliverSpeechResult(activity, result)

        assertEquals("cardio 10 phút", input.text.toString())
        assertEquals(input.text.length, input.selectionStart)
        controller.pause().stop().destroy()
    }

    @Test fun emptySpeechResultsLeaveTheExistingDraftUntouched() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val input = speechInput(activity)
        input.setText("giữ nguyên bản nháp")

        val result = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            arrayListOf("", "   ")
        )
        deliverSpeechResult(activity, result)

        assertEquals("giữ nguyên bản nháp", input.text.toString())
        controller.pause().stop().destroy()
    }
}
