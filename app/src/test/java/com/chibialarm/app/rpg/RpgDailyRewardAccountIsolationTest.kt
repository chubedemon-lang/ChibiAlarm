package com.chibialarm.app.rpg

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression: daily RPG rewards are scoped to the verified subject, not the
 * device or the local calendar date alone. No authentication is implemented here.
 */
@RunWith(RobolectricTestRunner::class)
class RpgDailyRewardAccountIsolationTest {
    @Test fun sameDayClaimsRemainIndependentAfterLogoutAndReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val suffix = UUID.randomUUID().toString()
        val aliceId = "test:$suffix:alice"
        val bobId = "test:$suffix:bob"
        val day = LocalDate.of(2026, 11, 4)
        try {
            val alice = RpgDatabase.forVerifiedAccount(context, aliceId)
            val bob = RpgDatabase.forVerifiedAccount(context, bobId)
            RpgEngine(alice).initialize(500, day)
            RpgEngine(bob).initialize(500, day)
            assertTrue(RpgEngine(alice).claimDailyLogin(day))
            assertTrue(RpgEngine(bob).claimDailyLogin(day))
            assertTrue(RpgEngine(alice).claimDailyPetGame(day))
            assertTrue(RpgEngine(bob).claimDailyPetGame(day))
            assertFalse(RpgEngine(alice).claimDailyLogin(day))
            assertFalse(RpgEngine(bob).claimDailyPetGame(day))
            for (db in listOf(alice, bob)) {
                assertEquals(25L, db.dao().character()!!.totalXp)
                assertEquals(5L, db.dao().character()!!.coins)
            }
            RpgDatabase.closeVerifiedAccount(aliceId)
            val reopened = RpgDatabase.forVerifiedAccount(context, aliceId)
            assertFalse(RpgEngine(reopened).claimDailyLogin(day))
            assertFalse(RpgEngine(reopened).claimDailyPetGame(day))
            assertEquals(25L, reopened.dao().character()!!.totalXp)
            assertEquals(25L, bob.dao().character()!!.totalXp)
            assertTrue(RpgEngine(reopened).claimDailyLogin(day.plusDays(1)))
            assertEquals(40L, reopened.dao().character()!!.totalXp)
            assertEquals(25L, bob.dao().character()!!.totalXp)
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, aliceId)
            RpgDatabase.deleteVerifiedAccount(context, bobId)
        }
    }
}
