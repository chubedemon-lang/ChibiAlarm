package com.chibialarm.app.rpg

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RpgRulesTest {
    @Test fun xpThresholds() {
        assertEquals(100L, RpgRules.xpToNext(1))
        assertEquals(280L, RpgRules.xpToNext(5))
        assertEquals(730L, RpgRules.xpToNext(10))
        assertEquals(1, RpgRules.level(99))
        assertEquals(2, RpgRules.level(100))
    }

    @Test fun progressIsBoundedAndDoesNotOverflow() {
        assertEquals(1, RpgRules.progressNext(0, 3, 1))
        assertEquals(3, RpgRules.progressNext(2, 3, 99))
        assertEquals(3, RpgRules.progressNext(Int.MAX_VALUE, 3, Int.MAX_VALUE))
        assertEquals(1, RpgRules.progressNext(-5, 3, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun progressRejectsZeroAmount() {
        RpgRules.progressNext(0, 3, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun progressRejectsInvalidTarget() {
        RpgRules.progressNext(0, 0, 1)
    }

    @Test fun streakCountsFromYesterdayIfTodayIsMissing() {
        val today = LocalDate.of(2026, 11, 1)
        val days = listOf(StreakDay("fitness", "2026-10-31", true),
            StreakDay("fitness", "2026-10-30", true))
        assertEquals(2, RpgRules.streak(days, today))
    }

    @Test fun streakSkipsExcusedDays() {
        val today = LocalDate.of(2026, 10, 31)
        val days = listOf(
            StreakDay("fitness", "2026-10-31", true),
            StreakDay("fitness", "2026-10-30", false, true),
            StreakDay("fitness", "2026-10-29", true)
        )
        assertEquals(2, RpgRules.streak(days, today))
    }

    @Test fun missedDayBreaksStreak() {
        val today = LocalDate.of(2026, 10, 31)
        val days = listOf(
            StreakDay("fitness", "2026-10-31", true),
            StreakDay("fitness", "2026-10-30", false),
            StreakDay("fitness", "2026-10-29", true)
        )
        assertEquals(1, RpgRules.streak(days, today))
    }
}
