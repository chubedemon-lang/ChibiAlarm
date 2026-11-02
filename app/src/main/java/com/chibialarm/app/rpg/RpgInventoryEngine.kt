package com.chibialarm.app.rpg

import androidx.room.withTransaction
import java.time.LocalDate

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
                // A damaged negative stack must never be "repaired" by a new
                // grant: doing so could silently create spendable items.
                // Throw inside the transaction to roll back its receipt too.
                require(existing.quantity >= 0L) { "Corrupt inventory balance" }
                val total = Math.addExact(existing.quantity, amount)
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

    /**
     * One deterministic, offline garden find per local date. A single receipt
     * makes retries, rotation and concurrent taps safe. This is a local toy
     * economy; changing the device clock can change available days.
     */
    suspend fun forageGarden(today: LocalDate = LocalDate.now()): String? {
        val itemId = RpgGardenRules.dailyFind(today)
        val claimed = grant(itemId, 1L, "garden:forage:$today")
        return if (claimed) itemId else null
    }

    /**
     * Turn one herb and one star fragment into a non-tradeable garden charm.
     * All debits, output and deduplication are in the same Room transaction.
     * Failed crafts never consume ingredients or reserve a receipt.
     */
    suspend fun craftGardenCharm(dedupeKey: String): Boolean {
        validateReceiptKey(dedupeKey)
        require(dedupeKey.startsWith("craft:garden_charm:")) { "Invalid craft receipt" }
        return db.withTransaction {
            val dao = db.dao()
            if (dao.itemReceiptCount(dedupeKey) != 0) return@withTransaction false
            if ((dao.itemStack("herb")?.quantity ?: 0L) < 1L ||
                (dao.itemStack("star_fragment")?.quantity ?: 0L) < 1L) {
                return@withTransaction false
            }
            val now = System.currentTimeMillis()
            if (dao.insertItemReceipt(RpgItemReceipt(
                    dedupeKey, "garden_charm", 1L, now)) == -1L) return@withTransaction false
            check(dao.consumeItem("herb", 1L, now) == 1)
            check(dao.consumeItem("star_fragment", 1L, now) == 1)
            val existing = dao.itemStack("garden_charm")
            if (existing == null) {
                check(dao.insertItemStack(RpgItemStack("garden_charm", 1L, now)) != -1L)
            } else {
                dao.updateItemStack(existing.copy(
                    quantity = Math.addExact(existing.quantity, 1L), updatedAt = now))
            }
            true
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
/** Deterministic offline drops; no server, random monetization or hidden purchases. */
object RpgGardenRules {
    fun dailyFind(today: LocalDate): String =
        if (today.toEpochDay() % 2L == 0L) "herb" else "star_fragment"
}

object RpgItemCatalog {
    data class Definition(val title: String, val icon: String, val description: String)
    fun describe(itemId: String): Definition = when (itemId) {
        "camp_map" -> Definition("Bản đồ khu vườn", "🗺️", "Vật phẩm khởi đầu, lưu ngoại tuyến")
        "star_fragment" -> Definition("Mảnh sao", "✨", "Nguyên liệu chế tạo bùa khu vườn")
        "herb" -> Definition("Thảo mộc", "🌿", "Nguyên liệu chế tạo bùa khu vườn")
        "garden_charm" -> Definition("Bùa khu vườn", "🍀", "Kỷ vật chế tạo từ thảo mộc và mảnh sao")
        else -> Definition("Vật phẩm", "🎒", "Vật phẩm chưa có mô tả")
    }
}
