package com.chibialarm.app

/**
 * Offline scene lighting based only on device-local time. No weather API,
 * location permission, background service or network access is needed.
 */
object ChibiDayCycle {
    enum class Phase { DAY, SUNSET, NIGHT }

    fun phase(hour: Int): Phase {
        require(hour in 0..23) { "Invalid local hour" }
        return when (hour) {
            in 6..16 -> Phase.DAY
            in 17..19 -> Phase.SUNSET
            else -> Phase.NIGHT
        }
    }
}
