package com.chibialarm.app

import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutSpeechRecognitionRequestTest {
    @Test fun requestsVietnameseFreeFormAndPrefersOfflineRecognition() {
        val intent = WorkoutSpeechRecognitionRequest.create("Nói yêu cầu tập luyện")

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)
        )
        assertEquals(
            WorkoutSpeechRecognitionRequest.LANGUAGE_TAG,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        )
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test fun asksRecognizerForTheSameNumberOfAlternativesTheTranscriptCanReview() {
        val intent = WorkoutSpeechRecognitionRequest.create("Nói với Chibi")

        assertEquals(
            CoachSpeechTranscript.MAX_ALTERNATIVES,
            intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0)
        )
        assertEquals(
            WorkoutSpeechRecognitionRequest.MAX_ALTERNATIVES,
            intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0)
        )
    }

    @Test fun normalizesAndBoundsPromptWithoutControlCharacters() {
        val raw = "  Bước tiếp\\n  " + "x".repeat(400)
        val intent = WorkoutSpeechRecognitionRequest.create(raw)
        val prompt = intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT).orEmpty()

        assertTrue(prompt.length <= WorkoutSpeechRecognitionRequest.MAX_PROMPT_CHARS)
        assertFalse(prompt.any { it == '\\n' || it == '\\r' || it.code < 0x20 })
        assertTrue(prompt.startsWith("Bước tiếp"))
    }

    @Test fun rejectsAnEmptyPrompt() {
        try {
            WorkoutSpeechRecognitionRequest.create("  \\n ")
            throw AssertionError("Expected blank prompt to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: no malformed recognizer request is launched.
        }
    }
}
