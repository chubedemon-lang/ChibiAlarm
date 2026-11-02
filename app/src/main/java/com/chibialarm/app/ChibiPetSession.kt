package com.chibialarm.app

/**
 * Transient progress for the three-pet mini-game. The reward itself is granted
 * only by RpgEngine's durable, once-per-day Room ledger; this state never
 * grants EXP, coins or unlocks.
 *
 * Keeping this small state outside the View prevents an unrelated dashboard
 * refresh or screen rotation from discarding the first two interactions.
 */
internal class ChibiPetSession {
    data class Snapshot(val day: String, val taps: Int)

    private var day = ""
    private var taps = 0

    private fun resetOnDayChange(today: String) {
        if (day != today) {
            day = today
            taps = 0
        }
    }

    fun progress(today: String): Int {
        resetOnDayChange(today)
        return taps
    }

    fun pet(today: String): Int {
        resetOnDayChange(today)
        taps = (taps + 1).coerceAtMost(3)
        return taps
    }

    fun clear(today: String) {
        day = today
        taps = 0
    }

    /** A pending third tap is not a reward; replay safely after recreation. */
    fun snapshot(today: String): Snapshot {
        resetOnDayChange(today)
        return Snapshot(day, if (taps == 3) 0 else taps)
    }

    /** Treat Bundle values as untrusted and never restore yesterday's progress. */
    fun restore(today: String, saved: Snapshot) {
        day = today
        taps = if (saved.day == today && saved.taps in 0..2) saved.taps else 0
    }
}
