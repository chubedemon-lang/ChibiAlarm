package com.chibialarm.app

/**
 * A one-shot spoken heads-up near the end of a timed exercise.
 *
 * The caller retries while speech recognition is active or TTS is unavailable/busy.
 * This is only a navigation cue: it never marks a set complete or advances the workout.
 */
internal object WorkoutExerciseVoiceCue {
    const val WARNING_WINDOW_MS = 10_000L

    fun shouldAnnounce(
        remainingMs: Long,
        alreadyAnnounced: Boolean,
        pausedForSafety: Boolean,
        microphoneActive: Boolean,
        speechEngineReady: Boolean
    ): Boolean = !alreadyAnnounced &&
        !pausedForSafety &&
        !microphoneActive &&
        speechEngineReady &&
        remainingMs in 1L..WARNING_WINDOW_MS

    fun message(remainingMs: Long, upcomingMoveName: String?): String? {
        if (remainingMs !in 1L..WARNING_WINDOW_MS) return null
        val seconds = ((remainingMs + 999L) / 1_000L).coerceIn(1L, 10L)
        val upcoming = safeLabel(upcomingMoveName)
        return if (upcoming.isBlank()) {
            "Còn $seconds giây. Sắp hoàn thành động tác này."
        } else {
            "Còn $seconds giây. Tiếp theo: $upcoming."
        }
    }

    private fun safeLabel(value: String?): String = value.orEmpty()
        .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
}
