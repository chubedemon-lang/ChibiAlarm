package com.chibialarm.app.fitness

/** Pure offline policy for shortening only the unfinished portion of a guided workout. */
object RemainingWorkoutDuration {
    fun seconds(baseSeconds: Int, stepIndex: Int, shorterFromIndex: Int): Int {
        require(baseSeconds >= 0)
        return if (stepIndex >= shorterFromIndex) (baseSeconds / 2).coerceAtLeast(5) else baseSeconds
    }
    /** Never extend an almost-finished active step when the user requests shorter time. */
    fun shortenActiveMs(remainingMs: Long): Long {
        require(remainingMs >= 0L)
        return minOf(remainingMs, (remainingMs / 2).coerceAtLeast(5_000L))
    }

    /**
     * Convert a live timer value to the estimator's bounded, round-up seconds.
     * A partial second must remain visible in a time estimate, not disappear.
     */
    fun ceilSeconds(milliseconds: Long): Int {
        val bounded = milliseconds.coerceIn(0L, 600_000L)
        return ((bounded + 999L) / 1_000L).toInt()
    }

    data class PreviewStep(val name: String, val beforeSeconds: Int, val afterSeconds: Int)

    /**
     * Read-only comparison for the active and upcoming steps.
     * The current step is supplied in milliseconds so the preview uses the exact
     * same shortening rule as the live timer, including the five-second floor.
     */
    fun preview(
        names: List<String>,
        durations: List<Int>,
        currentIndex: Int,
        remainingMilliseconds: Long,
        existingShorterFrom: Int
    ): List<PreviewStep> {
        require(names.size == durations.size)
        require(currentIndex in names.indices)
        require(remainingMilliseconds >= 0L)
        require(durations.all { it >= 0 })
        val currentBeforeSeconds = ceilSeconds(remainingMilliseconds)
        val currentAfterSeconds = ceilSeconds(shortenActiveMs(remainingMilliseconds))
        val proposedFrom = minOf(existingShorterFrom, currentIndex)
        return names.indices.drop(currentIndex).map { i ->
            val before = if (i == currentIndex) currentBeforeSeconds
                else seconds(durations[i], i, existingShorterFrom)
            val after = if (i == currentIndex) currentAfterSeconds
                else seconds(durations[i], i, proposedFrom)
            PreviewStep(names[i], before, after)
        }
    }
}
