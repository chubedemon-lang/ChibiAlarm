package com.chibialarm.app

import android.content.Context
import com.chibialarm.app.rpg.RpgDatabase

/**
 * A local cosmetic selection is NOT an entitlement. Unlocks are derived from
 * the account's durable RPG level on every read and write.
 *
 * The public constructor deliberately preserves the original guest preference
 * filename. Verified accounts MUST use forVerifiedAccount with a subject
 * supplied by trusted authentication, never a display name or unverified claim.
 * This does not implement login or cross-device synchronization.
 */
class ChibiWardrobeStore private constructor(context: Context, preferenceName: String) {
    constructor(context: Context) : this(context, GUEST_PREFERENCES)

    private val prefs = context.applicationContext
        .getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun selected(level: Int): ChibiCosmetics.Outfit =
        ChibiCosmetics.resolveSelection(level, prefs.getString("selected_outfit", null))

    /** Persist only unlocked cosmetics; a stale/modified preference cannot unlock an item. */
    fun equip(level: Int, outfit: ChibiCosmetics.Outfit): Boolean {
        if (!ChibiCosmetics.canEquip(level, outfit)) return false
        return prefs.edit().putString("selected_outfit", outfit.name).commit()
    }

    companion object {
        private const val GUEST_PREFERENCES = "chibi_wardrobe_guest"

        internal fun verifiedPreferenceName(verifiedSubject: String): String {
            // Reuse the exact identity validation and SHA-256 namespace used by Room.
            val dbFile = RpgDatabase.accountDatabaseFile(verifiedSubject)
            return "chibi_wardrobe_account_" +
                dbFile.removePrefix("chibi_rpg_account_").removeSuffix(".db")
        }

        fun forVerifiedAccount(context: Context, verifiedSubject: String): ChibiWardrobeStore =
            ChibiWardrobeStore(context, verifiedPreferenceName(verifiedSubject))

        /**
         * Removes ONLY this verified account's wardrobe. The RPG database is
         * managed separately by RpgDatabase.deleteVerifiedAccount. Call both
         * only after closing the account's screens and active coroutines.
         */
        fun deleteVerifiedAccount(context: Context, verifiedSubject: String): Boolean {
            val name = verifiedPreferenceName(verifiedSubject)
            return context.applicationContext.deleteSharedPreferences(name)
        }
    }
}
