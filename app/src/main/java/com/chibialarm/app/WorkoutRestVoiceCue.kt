package com.chibialarm.app

/**
 * A single spoken heads-up near the end of a rest interval.
 *
 * The caller owns and persists the one-shot flag so pause/rotation/process restore
 * cannot repeatedly announce the same countdown. Safety pauses suppress the cue.
 */
internal object WorkoutRestVoiceCue {
    const val WARNING_WINDOW_MS = 10_000L

    fun shouldAnnounce(
        remainingMs: Long,
        alreadyAnnounced: Boolean,
        pausedForSafety: Boolean
    ): Boolean = !alreadyAnnounced &&
        !pausedForSafety &&
        remainingMs in 1L..WARNING_WINDOW_MS

    fun message(remainingMs: Long): String? {
        if (remainingMs !in 1L..WARNING_WINDOW_MS) return null
        val seconds = ((remainingMs + 999L) / 1_000L).coerceIn(1L, 10L)
        return "Còn $seconds giây nghỉ. Chuẩn bị cho động tác tiếp theo."
    }
}
