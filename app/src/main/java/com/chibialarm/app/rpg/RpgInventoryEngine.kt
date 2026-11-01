package com.chibialarm.app.rpg

import androidx.room.withTransaction

/**
 * Offline-first, durable item stacks. Item receipts are unique per database,
 * so repeated calls, process restarts and concurrent coroutines cannot duplicate
 * a grant. This engine does not issue XP, currency or paid entitlements.
 *
 * Call only from trusted local gameplay rules; it is not a server economy.
 */
class RpgInventoryEngine(private val db: RpgDatabase) {
    suspend fun grant(itemId: String, amount: Long, dedupeKey: String): Boolean {
        validateItemId(itemId)
        validateReceiptKey(dedupeKey)
        require(amount in 1..1_000_000L) { "Invalid grant amount" }
        return db.withTransaction {
            val dao = db.dao()
            val receipt = RpgItemReceipt(dedupeKey, itemId, amount, System.currentTimeMillis())
            if (dao.insertItemReceipt(receipt) == -1L) return@withTransaction false
            val existing = dao.itemStack(itemId)
            if (existing == null) {
                dao.insertItemStack(RpgItemStack(itemId, amount, receipt.createdAt))
            } else {
                val total = Math.addExact(existing.quantity, amount)
                require(total >= 0L) { "Corrupt inventory balance" }
                dao.updateItemStack(existing.copy(quantity = total, updatedAt = receipt.createdAt))
            }
            true
        }
    }

    /** Atomic compare-and-decrement: never permits a negative stack. */
    suspend fun consume(itemId: String, amount: Long): Boolean {
        validateItemId(itemId)
        require(amount in 1..1_000_000L) { "Invalid consumption amount" }
        return db.withTransaction {
            db.dao().consumeItem(itemId, amount, System.currentTimeMillis()) == 1
        }
    }

    suspend fun stacks(): List<RpgItemStack> = db.dao().itemStacks()

    companion object {
        fun validateItemId(value: String) {
            require(value.matches(Regex("[a-z][a-z0-9_]{0,63}"))) { "Invalid item ID" }
        }

        fun validateReceiptKey(value: String) {
            require(value.length in 1..128 && value.matches(Regex("[a-z0-9:_-]+"))) {
                "Invalid inventory receipt key"
            }
        }
    }
}

/** Local labels only. Unknown future item IDs are displayed safely, never executed. */
object RpgItemCatalog {
    data class Definition(val title: String, val icon: String, val description: String)
    fun describe(itemId: String): Definition = when (itemId) {
        "camp_map" -> Definition("Bản đồ khu vườn", "🗺️", "Vật phẩm khởi đầu, lưu ngoại tuyến")
        "star_fragment" -> Definition("Mảnh sao", "✨", "Nguyên liệu dành cho các hoạt động sau này")
        "herb" -> Definition("Thảo mộc", "🌿", "Nguyên liệu dành cho các hoạt động sau này")
        else -> Definition("Vật phẩm", "🎒", "Vật phẩm chưa có mô tả")
    }
}
