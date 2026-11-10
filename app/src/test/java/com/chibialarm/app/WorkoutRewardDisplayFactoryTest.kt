package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRewardDisplayFactoryTest {
    @Test fun firstWorkoutShowsAwardAndLevelOneProgress() {
        val display = WorkoutRewardDisplayFactory.create(11, 11L)

        assertEquals(11, display.awardedXp)
        assertEquals(11L, display.totalXp)
        assertEquals(1, display.level)
        assertEquals(11L, display.xpInLevel)
        assertEquals(100L, display.xpRequired)
        assertFalse(display.maxLevelReached)
    }

    @Test fun progressUsesCurrentLevelAfterCrossingThreshold() {
        // Level 1 requires 100 XP; level 2 requires 130 XP.
        val display = WorkoutRewardDisplayFactory.create(5, 105L)

        assertEquals(2, display.level)
        assertEquals(5L, display.xpInLevel)
        assertEquals(130L, display.xpRequired)
    }

    @Test fun previouslyAwardedSessionCanRenderWithoutMintingMoreXp() {
        val display = WorkoutRewardDisplayFactory.create(0, 111L)

        assertEquals(0, display.awardedXp)
        assertEquals(111L, display.totalXp)
        assertEquals(2, display.level)
        assertEquals(11L, display.xpInLevel)
    }

    @Test fun maximumLevelIsExplicitAndProgressNeverExceedsBar() {
        val xpForLevelOneThousand = (1 until 1000).sumOf { level ->
            com.chibialarm.app.rpg.RpgRules.xpToNext(level)
        } + 50_000_000L
        val display = WorkoutRewardDisplayFactory.create(0, xpForLevelOneThousand)

        assertEquals(1000, display.level)
        assertTrue(display.maxLevelReached)
        assertTrue(display.xpInLevel <= display.xpRequired)
    }

    @Test fun invalidRewardOrBalanceIsRejected() {
        for ((award, balance) in listOf(31 to 31L, -1 to 0L, 0 to -1L)) {
            try {
                WorkoutRewardDisplayFactory.create(award, balance)
                throw AssertionError("Invalid reward snapshot should be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected: the display must not mask invalid RPG data.
            }
        }
    }
}
