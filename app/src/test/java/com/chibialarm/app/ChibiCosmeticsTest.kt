package com.chibialarm.app

import org.junit.Assert.assertEquals
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
}
