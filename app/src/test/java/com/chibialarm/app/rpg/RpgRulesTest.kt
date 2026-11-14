package com.chibialarm.app.rpg

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RpgRulesTest {
    @Test fun largeLegacyXpRemainsWithinSupportedLevelCap() {
        assertEquals(1000, RpgRules.level(Long.MAX_VALUE))
        assertEquals(1, RpgRules.level(0))
    }

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

    @Test fun observedProgressIsIdempotent() {
        assertEquals(1, RpgRules.progressObserved(0, 2, 1))
        assertEquals(1, RpgRules.progressObserved(1, 2, 1))
        assertEquals(2, RpgRules.progressObserved(1, 2, 20))
        assertEquals(2, RpgRules.progressObserved(2, 2, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun observedProgressRejectsNegativeInput() {
        RpgRules.progressObserved(0, 2, -1)
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

    @Test fun importedXpCanAlreadyReachAchievementLevel() {
        // Thresholds for levels 1 -> 5: 100 + 130 + 170 + 220 = 620.
        assertEquals(4, RpgRules.level(619))
        assertEquals(5, RpgRules.level(620))
    }

    @Test fun uncheckedTodayDoesNotCountButYesterdayStillDoes() {
        val today = LocalDate.of(2026, 11, 1)
        val days = listOf(
            StreakDay("growth:read", "2026-11-01", false),
            StreakDay("growth:read", "2026-10-31", true),
            StreakDay("growth:read", "2026-10-30", true)
        )
        assertEquals(2, RpgRules.streak(days, today))
    }

    @Test fun uncheckedYesterdayBreaksStreakEvenIfEarlierDayCompleted() {
        val today = LocalDate.of(2026, 11, 1)
        val days = listOf(
            StreakDay("growth:read", "2026-11-01", false),
            StreakDay("growth:read", "2026-10-31", false),
            StreakDay("growth:read", "2026-10-30", true)
        )
        assertEquals(0, RpgRules.streak(days, today))
    }

    @Test fun emptyGrowthHistoryHasNoStreak() {
        assertEquals(0, RpgRules.streak(emptyList(), LocalDate.of(2026, 11, 1)))
    }
}
