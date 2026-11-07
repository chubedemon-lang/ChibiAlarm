package com.chibialarm.app.fitness

/** Pure offline policy for shortening only the unfinished portion of a guided workout. */
object RemainingWorkoutDuration {
    fun seconds(baseSeconds: Int, stepIndex: Int, shorterFromIndex: Int): Int {
        require(baseSeconds >= 0)
        return if (stepIndex >= shorterFromIndex) (baseSeconds / 2).coerceAtLeast(5) else baseSeconds
    }
}
