package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChibiCosmeticsTest {
    @Test fun outfitsUnlockAtExactLevelBoundaries() {
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiCosmetics.equipped(1))
        assertEquals(ChibiCosmetics.Outfit.STARTER, ChibiCosmetics.equipped(4))
        assertEquals(ChibiCosmetics.Outfit.SCARF, ChibiCosmetics.equipped(5))
        assertEquals(ChibiCosmetics.Outfit.SCARF, ChibiCosmetics.equipped(9))
        assertEquals(ChibiCosmetics.Outfit.CROWN, ChibiCosmetics.equipped(10))
        assertEquals(ChibiCosmetics.Outfit.CROWN, ChibiCosmetics.equipped(1000))
    }

    @Test fun unlocksAreDeterministicAndDoNotRequirePurchases() {
        assertEquals(listOf(ChibiCosmetics.Outfit.STARTER), ChibiCosmetics.unlocked(1))
        assertEquals(listOf(ChibiCosmetics.Outfit.STARTER, ChibiCosmetics.Outfit.SCARF),
            ChibiCosmetics.unlocked(5))
        assertEquals(ChibiCosmetics.Outfit.SCARF, ChibiCosmetics.nextUnlock(1))
        assertEquals(ChibiCosmetics.Outfit.CROWN, ChibiCosmetics.nextUnlock(5))
        assertNull(ChibiCosmetics.nextUnlock(10))
    }

    @Test fun invalidLevelsCannotUnlockCosmetics() {
        assertThrows(IllegalArgumentException::class.java) {
            ChibiCosmetics.equipped(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChibiCosmetics.nextUnlock(-1)
        }
    }
    @Test fun selectedCosmeticMustBeUnlockedEvenIfPreferenceWasEdited() {
        assertEquals(ChibiCosmetics.Outfit.STARTER,
            ChibiCosmetics.resolveSelection(1, "CROWN"))
        assertEquals(ChibiCosmetics.Outfit.STARTER,
            ChibiCosmetics.resolveSelection(10, "UNKNOWN"))
        assertEquals(ChibiCosmetics.Outfit.STARTER,
            ChibiCosmetics.resolveSelection(10, "STARTER"))
        assertEquals(ChibiCosmetics.Outfit.SCARF,
            ChibiCosmetics.resolveSelection(5, "SCARF"))
        assertEquals(ChibiCosmetics.Outfit.CROWN,
            ChibiCosmetics.resolveSelection(10, null))
        assertTrue(ChibiCosmetics.canEquip(10, ChibiCosmetics.Outfit.CROWN))
        assertFalse(ChibiCosmetics.canEquip(9, ChibiCosmetics.Outfit.CROWN))
    }

}
