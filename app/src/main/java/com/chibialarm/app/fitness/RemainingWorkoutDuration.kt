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

    data class PreviewStep(val name: String, val beforeSeconds: Int, val afterSeconds: Int)

    /**
     * Read-only comparison for the active and upcoming steps.
     * The active step uses its actual remaining seconds, not its original duration.
     */
    fun preview(
        names: List<String>,
        durations: List<Int>,
        currentIndex: Int,
        remainingSeconds: Int,
        existingShorterFrom: Int
    ): List<PreviewStep> {
        require(names.size == durations.size)
        require(currentIndex in names.indices)
        require(remainingSeconds >= 0)
        require(durations.all { it >= 0 })
        val proposedFrom = minOf(existingShorterFrom, currentIndex)
        return names.indices.drop(currentIndex).map { i ->
            val before = if (i == currentIndex) remainingSeconds
                else seconds(durations[i], i, existingShorterFrom)
            val after = if (i == currentIndex) minOf(remainingSeconds, (remainingSeconds / 2).coerceAtLeast(5))
                else seconds(durations[i], i, proposedFrom)
            PreviewStep(names[i], before, after)
        }
    }
}
