package com.chibialarm.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDurationChoicesTest {
    @Test fun offersShortAndLongDurationsUsedByTheCoachParser() {
        assertArrayEquals(
            intArrayOf(5, 10, 15, 20, 30, 40, 45, 60, 90, 120),
            WorkoutDurationChoices.minutes
        )
        assertEquals("5 phút", WorkoutDurationChoices.labels.first())
        assertEquals("120 phút", WorkoutDurationChoices.labels.last())
    }

    @Test fun everySupportedDurationRoundTripsWithoutRounding() {
        WorkoutDurationChoices.minutes.forEach { duration ->
            val index = WorkoutDurationChoices.indexForMinutes(duration)
            assertEquals(duration, WorkoutDurationChoices.minutes[index])
        }
    }

    @Test fun naturalLanguageDurationsRemainExactInTheProfileChoices() {
        for (duration in listOf(5, 45, 60, 90, 120)) {
            val parsed = FitnessNaturalRequest.parse("Tao muốn tập sức mạnh $duration phút")
            assertEquals(duration, parsed.minutes)
            val selected = WorkoutDurationChoices.minutes[
                WorkoutDurationChoices.indexForMinutes(parsed.minutes!!)
            ]
            assertEquals("The coach must not silently shorten $duration minutes", duration, selected)
        }
    }

    @Test fun existingPreferenceIndicesKeepTheirOriginalMeaning() {
        val oldMinutes = intArrayOf(10, 15, 20, 30, 40)
        oldMinutes.indices.forEach { index ->
            assertEquals(oldMinutes[index], WorkoutDurationChoices.legacyMinutesForIndex(index))
            assertEquals(index, WorkoutDurationChoices.legacyIndexForMinutes(oldMinutes[index]))
        }
    }

    @Test fun customDurationsKeepAValidLegacyFallbackWithoutReplacingExactValue() {
        assertEquals(4, WorkoutDurationChoices.legacyIndexForMinutes(60))
        assertEquals(40, WorkoutDurationChoices.legacyMinutesForIndex(
            WorkoutDurationChoices.legacyIndexForMinutes(60)
        ))
        assertEquals(60, WorkoutDurationChoices.minutes[
            WorkoutDurationChoices.indexForMinutes(60)
        ])
    }

    @Test fun valuesOutsideSupportedRangeAreBounded() {
        assertEquals(5, WorkoutDurationChoices.minutes[
            WorkoutDurationChoices.indexForMinutes(-100)
        ])
        assertEquals(120, WorkoutDurationChoices.minutes[
            WorkoutDurationChoices.indexForMinutes(999)
        ])
        assertTrue(WorkoutDurationChoices.legacyIndexForMinutes(999) in 0..4)
    }
}
