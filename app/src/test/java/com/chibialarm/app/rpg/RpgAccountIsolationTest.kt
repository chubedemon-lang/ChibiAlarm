package com.chibialarm.app.rpg

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RpgAccountIsolationTest {
    @Test fun databaseNamesAreStableOpaqueAndDistinct() {
        val alice = RpgDatabase.accountDatabaseFile("issuer:alice")
        assertEquals(alice, RpgDatabase.accountDatabaseFile("issuer:alice"))
        assertNotEquals(alice, RpgDatabase.accountDatabaseFile("issuer:bob"))
        assertTrue(alice.startsWith("chibi_rpg_account_"))
        assertTrue(alice.endsWith(".db"))
        assertFalse(alice.contains("alice"))
        assertEquals(85, alice.length)
    }

    @Test fun rejectsBlankOrUntrustedSubjectFormats() {
        for (subject in listOf("", " ", " alice", "alice ", "bad\nsubject", "a".repeat(129))) {
            try {
                RpgDatabase.accountDatabaseFile(subject)
                fail("Expected invalid subject rejection")
            } catch (_: IllegalArgumentException) {
                // Invalid identity cannot create a database.
            }
        }
    }

    @Test fun explicitAccountErasureDoesNotDeleteAnotherAccountsRpg() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefix = UUID.randomUUID().toString()
        val firstId = "test:$prefix:erase"
        val secondId = "test:$prefix:keep"
        val first = RpgDatabase.forVerifiedAccount(context, firstId)
        val second = RpgDatabase.forVerifiedAccount(context, secondId)
        first.dao().insertCharacter(RpgCharacter(name = "Erase Me", totalXp = 10L))
        second.dao().insertCharacter(RpgCharacter(name = "Keep Me", totalXp = 20L))
        assertTrue(RpgDatabase.deleteVerifiedAccount(context, firstId))
        val reopened = RpgDatabase.forVerifiedAccount(context, firstId)
        assertNull(reopened.dao().character())
        assertEquals(20L, second.dao().character()?.totalXp)
    }

    @Test fun differentAccountsDoNotShareRpgDataAndGuestDatabaseRemainsSeparate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefix = UUID.randomUUID().toString()
        val first = RpgDatabase.forVerifiedAccount(context, "test:$prefix:one")
        val second = RpgDatabase.forVerifiedAccount(context, "test:$prefix:two")
        val guest = RpgDatabase.get(context)
        assertNotSame(first, second)
        assertNotSame(first, guest)
        assertNotSame(second, guest)
        first.dao().insertCharacter(RpgCharacter(name = "Account One", totalXp = 123L))
        assertEquals(123L, first.dao().character()?.totalXp)
        assertNull(second.dao().character())
        assertSame(first, RpgDatabase.forVerifiedAccount(context, "test:$prefix:one"))
    }
}
