package com.chibialarm.app

/**
 * Pure, bounded extension rules for an already-running rest countdown.
 * Rest remains separate from exercise time and can never exceed ten minutes.
 */
object WorkoutRestAdjustment {
    const val MAX_REST_MILLIS = 600_000L

    data class Preview(
        val beforeRemainingMs: Long,
        val requestedSeconds: Int,
        val afterRemainingMs: Long
    ) {
        val actualAddedMs: Long
            get() = (afterRemainingMs - beforeRemainingMs).coerceAtLeast(0L)

        val wasCapped: Boolean
            get() = actualAddedMs < requestedSeconds.toLong() * 1_000L
    }

    /**
     * Returns a preview rather than mutating the timer. The caller must show it
     * and obtain confirmation before applying the new countdown.
     */
    fun previewExtension(currentRemainingMs: Long, addedSeconds: Int): Preview? {
        if (currentRemainingMs !in 0L..MAX_REST_MILLIS || addedSeconds !in 1..600) {
            return null
        }
        val requestedMs = addedSeconds.toLong() * 1_000L
        val capacityMs = MAX_REST_MILLIS - currentRemainingMs
        val actualAddedMs = minOf(requestedMs, capacityMs)
        if (actualAddedMs <= 0L) return null
        return Preview(
            beforeRemainingMs = currentRemainingMs,
            requestedSeconds = addedSeconds,
            afterRemainingMs = currentRemainingMs + actualAddedMs
        )
    }
}
