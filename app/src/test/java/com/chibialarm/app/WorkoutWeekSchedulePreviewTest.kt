package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutWeekSchedulePreviewTest {
    private val saturday = LocalDate.of(2026, 11, 21)

    @Test fun threeDaysAreSpreadAcrossTheStrictlyUpcomingWeek() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 28)
            ),
            WorkoutWeekSchedulePreview.nextWeekDates(saturday, 3)
        )
    }

    @Test fun mondayInputStillTargetsTheFollowingCalendarWeek() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 2)
            ),
            WorkoutWeekSchedulePreview.nextWeekDates(LocalDate.of(2026, 11, 23), 2)
        )
    }

    @Test fun oneToFiveDayPatternsAreUniqueAndStayInsideOneMondayToSundayWeek() {
        val monday = LocalDate.of(2026, 11, 23)
        for (count in 1..5) {
            val dates = WorkoutWeekSchedulePreview.nextWeekDates(saturday, count)
            assertEquals(count, dates.size)
            assertEquals(count, dates.distinct().size)
            assertTrue(dates.all { !it.isBefore(monday) && !it.isAfter(monday.plusDays(6)) })
        }
    }

    @Test fun formatsVietnameseWeekdayAndDate() {
        assertEquals(
            "Thứ Tư 25/11",
            WorkoutWeekSchedulePreview.format(LocalDate.of(2026, 11, 25))
        )
        assertEquals(
            "Chủ nhật 29/11",
            WorkoutWeekSchedulePreview.format(LocalDate.of(2026, 11, 29))
        )
    }

    @Test fun rejectsZeroOrMoreThanFiveStructuredDays() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutWeekSchedulePreview.nextWeekDates(saturday, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutWeekSchedulePreview.nextWeekDates(saturday, 6)
        }
    }
}
