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
}
