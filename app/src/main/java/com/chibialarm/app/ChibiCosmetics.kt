package com.chibialarm.app

/**
 * Cosmetic progression derived from durable character level, never from tap counts.
 * No new Room tables, preferences, network calls or paid entitlements are needed.
 */
object ChibiCosmetics {
    enum class Outfit(val label: String, val unlockLevel: Int) {
        STARTER("Trang phục tân thủ", 1),
        SCARF("Khăn choàng nhà thám hiểm", 5),
        CROWN("Vương miện ngôi sao", 10)
    }

    fun unlocked(level: Int): List<Outfit> {
        require(level >= 1) { "Invalid character level" }
        return Outfit.values().filter { level >= it.unlockLevel }
    }

    fun equipped(level: Int): Outfit = unlocked(level).last()

    /**
     * Backward-compatible default: existing players keep their highest unlocked
     * cosmetic until they explicitly choose one. Invalid or now-locked saved
     * values are never treated as entitlements.
     */
    fun resolveSelection(level: Int, savedName: String?): Outfit {
        val available = unlocked(level)
        if (savedName == null) return available.last()
        val selected = Outfit.values().firstOrNull { it.name == savedName }
        return if (selected != null && selected in available) selected else Outfit.STARTER
    }

    fun canEquip(level: Int, outfit: Outfit): Boolean = outfit in unlocked(level)

    fun nextUnlock(level: Int): Outfit? {
        require(level >= 1) { "Invalid character level" }
        return Outfit.values().firstOrNull { it.unlockLevel > level }
    }
}
