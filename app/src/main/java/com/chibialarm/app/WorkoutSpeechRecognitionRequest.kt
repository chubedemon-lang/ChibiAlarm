package com.chibialarm.app

import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Shared, privacy-aware request for Android's external Vietnamese speech recognizer.
 *
 * Offline recognition is a preference hint only: the installed provider may ignore it
 * and process audio online. The app receives text alternatives, never raw audio here.
 */
internal object WorkoutSpeechRecognitionRequest {
    const val LANGUAGE_TAG = "vi-VN"
    const val MAX_PROMPT_CHARS = 240
    const val MAX_ALTERNATIVES = CoachSpeechTranscript.MAX_ALTERNATIVES

    fun create(prompt: String): Intent {
        val safePrompt = prompt
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_PROMPT_CHARS)
        require(safePrompt.isNotBlank()) { "Speech prompt must not be blank" }

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)
            putExtra(RecognizerIntent.EXTRA_PROMPT, safePrompt)
        }
    }
}
