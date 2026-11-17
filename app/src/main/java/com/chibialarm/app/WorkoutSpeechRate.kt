package com.chibialarm.app

/**
 * Transient workout TTS rate policy. It never writes to the global voice settings.
 */
object WorkoutSpeechRate {
    const val DEFAULT_RATE = 1.0f
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2.0f
    const val STEP = 0.15f

    fun normalize(rate: Float): Float =
        if (rate.isNaN() || rate.isInfinite()) DEFAULT_RATE
        else rate.coerceIn(MIN_RATE, MAX_RATE)

    fun slower(current: Float): Float =
        (normalize(current) - STEP).coerceAtLeast(MIN_RATE)

    fun faster(current: Float): Float =
        (normalize(current) + STEP).coerceAtMost(MAX_RATE)

    fun normal(preferred: Float): Float = normalize(preferred)

    /** Restore a workout-only rate; malformed saved values fall back to the user's preference. */
    fun restore(preferred: Float, saved: Float?): Float {
        val fallback = normalize(preferred)
        val validSaved = saved?.takeIf { !it.isNaN() && !it.isInfinite() }
        return validSaved?.let(::normalize) ?: fallback
    }
}
