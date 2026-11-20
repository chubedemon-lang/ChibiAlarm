package com.chibialarm.app

/**
 * Holds the latest rest-completion announcement until TTS can accept it.
 *
 * The message is local, bounded, and checkpointed by GuidedWorkoutActivity.
 * Safety pauses and microphone sessions never consume the pending message.
 */
internal class WorkoutRestCompletionSpeechQueue {
    var pendingMessage: String? = null
        private set

    fun enqueue(message: String?) {
        pendingMessage = sanitize(message)
    }

    fun restore(message: String?) {
        pendingMessage = sanitize(message)
    }

    fun takeIfReady(
        microphoneActive: Boolean,
        pausedForSafety: Boolean,
        speechEngineReady: Boolean
    ): String? {
        if (microphoneActive || pausedForSafety || !speechEngineReady) return null
        val message = pendingMessage ?: return null
        pendingMessage = null
        return message
    }

    private fun sanitize(value: String?): String? {
        val cleaned = value.orEmpty()
            .replace(Regex("[\\r\\n\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(350)
        return cleaned.takeIf { it.isNotBlank() }
    }
}
