package com.chibialarm.app

/**
 * Offline keepsakes backed by the immutable RPG reward ledger.
 *
 * These are display-only collectibles, not consumable currency, tradeable goods,
 * or additional XP payouts. Reconstructing them from existing claims avoids a
 * second mutable balance that could drift or duplicate after app upgrades.
 */
object ChibiInventory {
    data class Item(val id: String, val title: String, val icon: String, val count: Long)

    fun fromClaims(dailyLogins: Long, petGames: Long, questClaims: Long): List<Item> {
        require(dailyLogins >= 0 && petGames >= 0 && questClaims >= 0) {
            "Ledger counts must be non-negative"
        }
        return listOf(
            Item("journal", "Sổ hành trình", "📖", 1),
            Item("sunrise", "Vé bình minh", "🌅", dailyLogins),
            Item("friendship", "Trái tim tình bạn", "💗", petGames),
            Item("quest_badge", "Huy hiệu nhiệm vụ", "🎖️", questClaims)
        )
    }
}
