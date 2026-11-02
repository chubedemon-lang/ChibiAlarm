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

    @Test fun malformedUtf16CannotAliasAnotherAccountDatabase() {
        // Java UTF-8 replaces lone surrogates. Without validation these two
        // different subjects may silently resolve to the same account file.
        for (subject in listOf("issuer:bad\\uD800", "issuer:bad\\uDC00",
                "issuer:bad\\uD800x", "issuer:x\\uDC00y")) {
            val decoded = subject.replace("\\uD800", "\uD800").replace("\\uDC00", "\uDC00")
            try {
                RpgDatabase.accountDatabaseFile(decoded)
                fail("Malformed UTF-16 subject was accepted")
            } catch (_: IllegalArgumentException) {
                // Fail closed before opening a database.
            }
        }
    }

    @Test fun invisibleFormatControlsCannotSpoofVerifiedAccountSubject() {
        for (subject in listOf("issuer:alice\\u202E", "issuer:alice\\u200B",
                "issuer:alice\\u2066")) {
            val decoded = subject.replace("\\u202E", "\u202E")
                .replace("\\u200B", "\u200B").replace("\\u2066", "\u2066")
            try {
                RpgDatabase.accountDatabaseFile(decoded)
                fail("Invisible formatting subject was accepted")
            } catch (_: IllegalArgumentException) {
                // Bidi overrides and zero-width format characters are not identity.
            }
        }
    }

    @Test fun validSupplementaryUnicodeSubjectIsStableAndDistinct() {
        val subject = "issuer:player-\\uD83D\\uDE80"
            .replace("\\uD83D", "\uD83D").replace("\\uDE80", "\uDE80")
        val file = RpgDatabase.accountDatabaseFile(subject)
        assertEquals(file, RpgDatabase.accountDatabaseFile(subject))
        assertNotEquals(file, RpgDatabase.accountDatabaseFile("issuer:player"))
        assertFalse(file.contains("player"))
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
        assertTrue(RpgInventoryEngine(first).grant("star_fragment", 3, "account:first"))
        assertTrue(RpgInventoryEngine(second).grant("herb", 2, "account:second"))
        assertTrue(RpgDatabase.deleteVerifiedAccount(context, firstId))
        val reopened = RpgDatabase.forVerifiedAccount(context, firstId)
        assertNull(reopened.dao().character())
        assertEquals(20L, second.dao().character()?.totalXp)
        assertEquals(2L, second.dao().itemStack("herb")?.quantity)
        assertNull(reopened.dao().itemStack("star_fragment"))
    }

    @Test fun logoutClosesAccountHandleWithoutErasingDurableRpgData() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val subject = "test:${UUID.randomUUID()}:logout"
        val first = RpgDatabase.forVerifiedAccount(context, subject)
        try {
            first.dao().insertCharacter(RpgCharacter(name = "Returning Player", totalXp = 777L))
            assertTrue(RpgInventoryEngine(first).grant("herb", 4L, "test:logout:herbs"))
            RpgDatabase.closeVerifiedAccount(subject)
            assertFalse(first.isOpen)
            val reopened = RpgDatabase.forVerifiedAccount(context, subject)
            assertNotSame(first, reopened)
            assertEquals(777L, reopened.dao().character()?.totalXp)
            assertEquals(4L, reopened.dao().itemStack("herb")?.quantity)
            assertFalse(RpgInventoryEngine(reopened).grant("herb", 4L, "test:logout:herbs"))
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, subject)
        }
    }

    @Test fun closingOneAccountDoesNotCloseOrEraseOtherAccount() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefix = UUID.randomUUID().toString()
        val firstId = "test:${prefix}:logout-first"
        val secondId = "test:${prefix}:stay-signed-in"
        try {
            val first = RpgDatabase.forVerifiedAccount(context, firstId)
            val second = RpgDatabase.forVerifiedAccount(context, secondId)
            first.dao().insertCharacter(RpgCharacter(name = "First", totalXp = 100L))
            second.dao().insertCharacter(RpgCharacter(name = "Second", totalXp = 200L))
            RpgDatabase.closeVerifiedAccount(firstId)
            assertFalse(first.isOpen)
            assertTrue(second.isOpen)
            assertEquals(200L, second.dao().character()?.totalXp)
            assertEquals(100L, RpgDatabase.forVerifiedAccount(context, firstId)
                .dao().character()?.totalXp)
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, firstId)
            RpgDatabase.deleteVerifiedAccount(context, secondId)
        }
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
        assertTrue(RpgInventoryEngine(first).grant("star_fragment", 7, "account:one"))
        assertNull(second.dao().itemStack("star_fragment"))
        assertNull(guest.dao().itemStack("star_fragment"))
        assertEquals(123L, first.dao().character()?.totalXp)
        assertNull(second.dao().character())
        assertSame(first, RpgDatabase.forVerifiedAccount(context, "test:$prefix:one"))
    }

    @Test fun verifiedAccountsNeverImportDeviceWideGrowthHistoryOrUnlockStreaks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefix = UUID.randomUUID().toString()
        val aliceId = "test:$prefix:alice-growth"
        val bobId = "test:$prefix:bob-growth"
        val today = java.time.LocalDate.of(2026, 11, 2)
        val guestHistory = (0L..6L).map {
            StreakDay("growth:private-guest-habit", today.minusDays(it).toString(), true)
        }
        try {
            val alice = RpgDatabase.forVerifiedAccount(context, aliceId)
            val bob = RpgDatabase.forVerifiedAccount(context, bobId)
            RpgEngine(alice).initialize(1000, today)
            RpgEngine(bob).initialize(1000, today)
            assertEquals(0, RpgEngine(alice).syncGrowth(guestHistory, today))
            assertEquals(0, RpgEngine(bob).syncGrowth(guestHistory, today))
            for (database in listOf(alice, bob)) {
                assertTrue(database.dao().allStreakDays().isEmpty())
                assertFalse(database.dao().hasLedgerEntry("migration:growth:v1"))
                assertNull(database.dao().achievements().first { it.id == "streak_7" }.unlockedAt)
                assertEquals(0, database.dao().achievements().first { it.id == "streak_7" }.progress)
            }
            RpgDatabase.closeVerifiedAccount(aliceId)
            val reopened = RpgDatabase.forVerifiedAccount(context, aliceId)
            assertEquals(0, RpgEngine(reopened).syncGrowth(guestHistory, today))
            assertTrue(reopened.dao().allStreakDays().isEmpty())
            assertFalse(reopened.dao().hasLedgerEntry("migration:growth:v1"))
            assertEquals(0L, reopened.dao().character()!!.totalXp)
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, aliceId)
            RpgDatabase.deleteVerifiedAccount(context, bobId)
        }
    }

    @Test fun verifiedAccountsNeverInheritGuestLegacyXpEvenAfterReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefix = UUID.randomUUID().toString()
        val aliceId = "test:$prefix:alice"
        val bobId = "test:$prefix:bob"
        try {
            val alice = RpgDatabase.forVerifiedAccount(context, aliceId)
            val bob = RpgDatabase.forVerifiedAccount(context, bobId)
            val today = java.time.LocalDate.of(2026, 11, 2)

            // A device-wide v1.3 XP snapshot must not become Alice's or Bob's XP.
            RpgEngine(alice).initialize(620, today)
            RpgEngine(bob).initialize(999, today)
            assertEquals(0L, alice.dao().character()!!.totalXp)
            assertEquals(0L, bob.dao().character()!!.totalXp)
            assertFalse(alice.dao().hasLedgerEntry("migration:life_v12:xp90:v1"))
            assertFalse(bob.dao().hasLedgerEntry("migration:life_v12:xp90:v1"))

            assertTrue(RpgEngine(alice).claimDailyLogin(today))
            assertEquals(15L, alice.dao().character()!!.totalXp)
            assertEquals(0L, bob.dao().character()!!.totalXp)
            RpgDatabase.closeVerifiedAccount(aliceId)

            val reopened = RpgDatabase.forVerifiedAccount(context, aliceId)
            RpgEngine(reopened).initialize(2000, today)
            assertEquals(15L, reopened.dao().character()!!.totalXp)
            assertFalse(reopened.dao().hasLedgerEntry("migration:life_v12:xp90:v1"))
            assertFalse(RpgEngine(reopened).claimDailyLogin(today))
        } finally {
            RpgDatabase.deleteVerifiedAccount(context, aliceId)
            RpgDatabase.deleteVerifiedAccount(context, bobId)
        }
    }
}
