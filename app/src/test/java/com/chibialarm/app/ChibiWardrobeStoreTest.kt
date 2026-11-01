package com.chibialarm.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChibiWardrobeStoreTest {
    private fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences(
            "chibi_wardrobe_guest", Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test fun legacyPlayersKeepAutomaticOutfitUntilFirstSelection() {
        reset()
        val wardrobe = ChibiWardrobeStore(RuntimeEnvironment.getApplication())
        assertEquals(ChibiCosmetics.Outfit.SCARF, wardrobe.selected(5))
        assertEquals(ChibiCosmetics.Outfit.CROWN, wardrobe.selected(10))
    }

    @Test fun chosenOutfitPersistsAcrossStoreInstancesAndLevelChanges() {
        reset()
        val context = RuntimeEnvironment.getApplication()
        val first = ChibiWardrobeStore(context)
        assertTrue(first.equip(10, ChibiCosmetics.Outfit.STARTER))
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiWardrobeStore(context).selected(10))
        assertTrue(first.equip(10, ChibiCosmetics.Outfit.CROWN))
        assertEquals(ChibiCosmetics.Outfit.CROWN, ChibiWardrobeStore(context).selected(10))
        // A downgraded or corrupt character must not retain a locked item.
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiWardrobeStore(context).selected(1))
    }

    @Test fun lockedOutfitCannotBeSelectedOrPersisted() {
        reset()
        val context = RuntimeEnvironment.getApplication()
        val wardrobe = ChibiWardrobeStore(context)
        assertFalse(wardrobe.equip(4, ChibiCosmetics.Outfit.SCARF))
        assertFalse(wardrobe.equip(9, ChibiCosmetics.Outfit.CROWN))
        assertEquals(ChibiCosmetics.Outfit.STARTER, wardrobe.selected(4))
        assertEquals(null, context.getSharedPreferences("chibi_wardrobe_guest",
            Context.MODE_PRIVATE).getString("selected_outfit", null))
    }

    @Test fun tamperedPreferenceCannotUnlockAnOutfit() {
        reset()
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("chibi_wardrobe_guest", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_outfit", "CROWN").commit()
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiWardrobeStore(context).selected(1))
        prefs.edit().putString("selected_outfit", "UNKNOWN").commit()
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiWardrobeStore(context).selected(10))
    }

    @Test fun verifiedAccountsDoNotShareWardrobeWithEachOtherOrGuest() {
        reset()
        val context = RuntimeEnvironment.getApplication()
        val prefix = java.util.UUID.randomUUID().toString()
        val alice = "auth:$prefix:alice"
        val bob = "auth:$prefix:bob"
        val guest = ChibiWardrobeStore(context)
        val first = ChibiWardrobeStore.forVerifiedAccount(context, alice)
        val second = ChibiWardrobeStore.forVerifiedAccount(context, bob)
        try {
            assertTrue(guest.equip(10, ChibiCosmetics.Outfit.STARTER))
            assertTrue(first.equip(10, ChibiCosmetics.Outfit.SCARF))
            assertTrue(second.equip(10, ChibiCosmetics.Outfit.CROWN))
            assertEquals(ChibiCosmetics.Outfit.STARTER, guest.selected(10))
            assertEquals(ChibiCosmetics.Outfit.SCARF,
                ChibiWardrobeStore.forVerifiedAccount(context, alice).selected(10))
            assertEquals(ChibiCosmetics.Outfit.CROWN, second.selected(10))
            assertFalse(first.equip(1, ChibiCosmetics.Outfit.CROWN))
            assertEquals(ChibiCosmetics.Outfit.STARTER, first.selected(1))
            assertEquals(ChibiCosmetics.Outfit.SCARF, first.selected(10))
        } finally {
            ChibiWardrobeStore.deleteVerifiedAccount(context, alice)
            ChibiWardrobeStore.deleteVerifiedAccount(context, bob)
        }
    }

    @Test fun verifiedAccountErasurePreservesOtherAccountAndGuest() {
        reset()
        val context = RuntimeEnvironment.getApplication()
        val prefix = java.util.UUID.randomUUID().toString()
        val alice = "auth:$prefix:alice"
        val bob = "auth:$prefix:bob"
        val guest = ChibiWardrobeStore(context)
        assertTrue(guest.equip(10, ChibiCosmetics.Outfit.STARTER))
        assertTrue(ChibiWardrobeStore.forVerifiedAccount(context, alice)
            .equip(10, ChibiCosmetics.Outfit.SCARF))
        assertTrue(ChibiWardrobeStore.forVerifiedAccount(context, bob)
            .equip(10, ChibiCosmetics.Outfit.STARTER))
        assertTrue(ChibiWardrobeStore.deleteVerifiedAccount(context, alice))
        assertEquals(ChibiCosmetics.Outfit.CROWN,
            ChibiWardrobeStore.forVerifiedAccount(context, alice).selected(10))
        assertEquals(ChibiCosmetics.Outfit.STARTER,
            ChibiWardrobeStore.forVerifiedAccount(context, bob).selected(10))
        assertEquals(ChibiCosmetics.Outfit.STARTER, guest.selected(10))
        ChibiWardrobeStore.deleteVerifiedAccount(context, bob)
    }

    @Test fun verifiedAccountFilenamesAreOpaqueAndRejectInvalidIdentity() {
        val alice = ChibiWardrobeStore.verifiedPreferenceName("issuer:alice")
        assertEquals(alice, ChibiWardrobeStore.verifiedPreferenceName("issuer:alice"))
        assertFalse(alice.contains("alice"))
        assertFalse(alice.contains("issuer"))
        assertTrue(alice.startsWith("chibi_wardrobe_account_"))
        assertFalse(alice == ChibiWardrobeStore.verifiedPreferenceName("issuer:bob"))
        for (bad in listOf("", " alice", "bad\\nsubject", "x".repeat(129))) {
            try {
                ChibiWardrobeStore.verifiedPreferenceName(bad)
                org.junit.Assert.fail("Expected rejection")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
