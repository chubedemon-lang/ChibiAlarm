package com.chibialarm.app

import android.content.Context

/**
 * Local GUEST wardrobe only. The RPG dashboard currently opens the legacy guest
 * database; do not reuse this preference store for authenticated accounts.
 *
 * No Room schema migration, network permission, identity data or purchase state.
 * A future verified-account wardrobe must use a separate account-scoped store.
 */
class ChibiWardrobeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chibi_wardrobe_guest", Context.MODE_PRIVATE)

    fun selected(level: Int): ChibiCosmetics.Outfit =
        ChibiCosmetics.resolveSelection(level, prefs.getString("selected_outfit", null))

    /** Persist only unlocked cosmetics; a stale/modified preference cannot unlock an item. */
    fun equip(level: Int, outfit: ChibiCosmetics.Outfit): Boolean {
        if (!ChibiCosmetics.canEquip(level, outfit)) return false
        return prefs.edit().putString("selected_outfit", outfit.name).commit()
    }
}
