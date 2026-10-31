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
