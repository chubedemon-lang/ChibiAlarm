package com.chibialarm.app.rpg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

/**
 * Regression: account deletion is intentionally scoped to one verified RPG
 * database. It must not erase guest data, another account or legacy settings.
 */
@RunWith(RobolectricTestRunner::class)
class RpgAccountDeletionBoundaryTest {
    @Test fun deletingAccountPreservesGuestAndOtherAccountProgress() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val nonce = UUID.randomUUID().toString()
        val removedId = "test:$nonce:removed"
        val retainedId = "test:$nonce:retained"
        val guest = RpgDatabase.get(context)
        val guestBefore = guest.dao().character()
        val guestItemsBefore = guest.dao().itemStacks()
        val legacy = context.getSharedPreferences("account_deletion_boundary_$nonce", 0)
        legacy.edit().putString("keep", "legacy-value").commit()
        try {
            val removed = RpgDatabase.forVerifiedAccount(context, removedId)
            val retained = RpgDatabase.forVerifiedAccount(context, retainedId)
            removed.dao().insertCharacter(RpgCharacter(name = "Removed", totalXp = 30))
            retained.dao().insertCharacter(RpgCharacter(name = "Retained", totalXp = 70))
            assertTrue(RpgInventoryEngine(removed).grant("herb", 2, "same-receipt"))
            assertTrue(RpgInventoryEngine(retained).grant("herb", 5, "same-receipt"))

            assertTrue(RpgDatabase.deleteVerifiedAccount(context, removedId))
            assertTrue(retained.isOpen)
            assertEquals(70L, retained.dao().character()?.totalXp)
            assertEquals(5L, retained.dao().itemStack("herb")?.quantity)
            assertEquals(guestBefore, guest.dao().character())
            assertEquals(guestItemsBefore, guest.dao().itemStacks())
            assertEquals("legacy-value", legacy.getString("keep", null))

            val recreated = RpgDatabase.forVerifiedAccount(context, removedId)
            assertNull(recreated.dao().character())
            assertNull(recreated.dao().itemStack("herb"))
            assertTrue(RpgInventoryEngine(recreated).grant("herb", 1, "same-receipt"))
            assertEquals(1L, recreated.dao().itemStack("herb")?.quantity)
            assertEquals(5L, retained.dao().itemStack("herb")?.quantity)
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, removedId)
            RpgDatabase.deleteVerifiedAccount(context, retainedId)
            legacy.edit().clear().commit()
        }
    }
}
