package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChibiDayCycleTest {
    @Test fun daySunsetAndNightBoundariesAreDeterministic() {
        val expected = mapOf(
            0 to ChibiDayCycle.Phase.NIGHT,
            5 to ChibiDayCycle.Phase.NIGHT,
            6 to ChibiDayCycle.Phase.DAY,
            16 to ChibiDayCycle.Phase.DAY,
            17 to ChibiDayCycle.Phase.SUNSET,
            19 to ChibiDayCycle.Phase.SUNSET,
            20 to ChibiDayCycle.Phase.NIGHT,
            23 to ChibiDayCycle.Phase.NIGHT
        )
        expected.forEach { (hour, phase) ->
            assertEquals("Hour $hour", phase, ChibiDayCycle.phase(hour))
        }
    }

    @Test fun everyValidHourHasExactlyOnePhase() {
        (0..23).forEach { ChibiDayCycle.phase(it) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeHour() { ChibiDayCycle.phase(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHourPastMidnight() { ChibiDayCycle.phase(24) }
}
