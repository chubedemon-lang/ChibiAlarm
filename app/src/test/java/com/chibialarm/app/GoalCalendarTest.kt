package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Calendar-based challenges must not drift at midnight or across DST. */
class GoalCalendarTest {
    private fun millis(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun nextLocalMidnightIsDayTwoEvenWhenOnlyMinutesPassed() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val start = millis(zone, 2026, 11, 1, 23, 59)
        val now = millis(zone, 2026, 11, 2, 0, 1)
        assertEquals(2, GoalData.elapsedAt(LifeGoal(1, "Read", 30, start, emptySet()), now, zone))
    }

    @Test fun daylightSavingDoesNotChangeCalendarDayCount() {
        val zone = ZoneId.of("America/New_York")
        val start = millis(zone, 2026, 3, 7, 23, 30)
        val now = millis(zone, 2026, 3, 9, 0, 15)
        assertEquals(3, GoalData.elapsedAt(LifeGoal(1, "Focus", 30, start, emptySet()), now, zone))
    }

    @Test fun onlyValidDatesWithinChallengeAndNotInFutureCount() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val start = millis(zone, 2026, 11, 1, 22, 0)
        val now = millis(zone, 2026, 11, 3, 8, 0)
        val goal = LifeGoal(1, "Fitness", 3, start, setOf(
            "2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03",
            "2026-11-04", "2026-02-30", "invalid"
        ))
        assertEquals(3, GoalData.completedDaysAt(goal, now, zone))
        assertEquals(3, GoalData.elapsedAt(goal, now, zone))
        assertEquals(2, GoalData.completedDaysAt(goal, millis(zone, 2026, 11, 2, 12, 0), zone))
    }

    @Test fun expiredAndFutureChallengesDoNotCountOutOfWindowMarks() {
        val zone = ZoneId.of("UTC")
        val start = millis(zone, 2026, 1, 1, 12, 0)
        val now = millis(zone, 2026, 2, 1, 12, 0)
        val goal = LifeGoal(1, "Walk", 2, start, setOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-02-01"))
        assertEquals(2, GoalData.completedDaysAt(goal, now, zone))
        assertEquals(32, GoalData.elapsedAt(goal, now, zone))
        val future = goal.copy(started = millis(zone, 2026, 12, 1, 0, 0))
        assertEquals(0, GoalData.completedDaysAt(future, now, zone))
        assertEquals(1, GoalData.elapsedAt(future, now, zone))
    }
}
