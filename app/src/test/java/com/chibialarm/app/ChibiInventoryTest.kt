package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChibiInventoryTest {
    @Test fun backpackIncludesStarterJournalAndClaimedKeepsakes() {
        val items = ChibiInventory.fromClaims(2, 1, 3)
        assertEquals(listOf("journal", "sunrise", "friendship", "quest_badge"),
            items.map { it.id })
        assertEquals(listOf(1L, 2L, 1L, 3L), items.map { it.count })
    }

    @Test fun zeroClaimsProduceNoInventedRewards() {
        assertEquals(listOf(1L, 0L, 0L, 0L),
            ChibiInventory.fromClaims(0, 0, 0).map { it.count })
    }

    @Test fun rejectsNegativeOrCorruptedCounts() {
        for (counts in listOf(Triple(-1L, 0L, 0L), Triple(0L, -1L, 0L),
            Triple(0L, 0L, -1L))) {
            assertThrows(IllegalArgumentException::class.java) {
                ChibiInventory.fromClaims(counts.first, counts.second, counts.third)
            }
        }
    }

    @Test fun largeCountsAreNotTruncatedToInt() {
        val large = Int.MAX_VALUE.toLong() + 3
        assertEquals(large, ChibiInventory.fromClaims(large, 0, 0)[1].count)
    }
}
