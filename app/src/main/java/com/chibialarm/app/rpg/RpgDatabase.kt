package com.chibialarm.app.rpg

import android.content.Context
import java.security.MessageDigest
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "rpg_character")
data class RpgCharacter(
    @PrimaryKey val id: String = "local-player",
    val name: String = "Tân Binh",
    val totalXp: Long = 0,
    val coins: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rpg_quest", indices = [Index(value = ["templateId", "periodKey"], unique = true)])
data class RpgQuest(
    @PrimaryKey val id: String,
    val templateId: String,
    val periodKey: String,
    val title: String,
    val category: String,
    val target: Int = 1,
    val progress: Int = 0,
    val rewardXp: Int,
    val rewardCoins: Int = 0,
    val state: String = "ACTIVE"
)

@Entity(tableName = "rpg_xp_ledger", indices = [Index(value = ["dedupeKey"], unique = true)])
data class XpLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val questId: String,
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rpg_achievement")
data class RpgAchievement(
    @PrimaryKey val id: String,
    val title: String,
    val progress: Int = 0,
    val target: Int = 1,
    val unlockedAt: Long? = null
)

@Entity(tableName = "rpg_streak_day", primaryKeys = ["habitId", "localDate"])
data class StreakDay(
    val habitId: String,
    val localDate: String,
    val completed: Boolean,
    val excused: Boolean = false
)

/**
 * Durable non-currency item stacks. Distinct from ledger-derived keepsakes.
 * Each authenticated account uses a separate Room database; the guest uses
 * the original chibi_rpg.db. Item receipts prevent duplicate offline grants.
 */
@Entity(tableName = "rpg_item_stack")
data class RpgItemStack(
    @PrimaryKey val itemId: String,
    val quantity: Long,
    val updatedAt: Long
)

@Entity(tableName = "rpg_item_receipt")
data class RpgItemReceipt(
    @PrimaryKey val dedupeKey: String,
    val itemId: String,
    val quantity: Long,
    val createdAt: Long
)

@Dao
interface RpgDao {
    @Query("SELECT * FROM rpg_character WHERE id = :id LIMIT 1")
    suspend fun character(id: String = "local-player"): RpgCharacter?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCharacter(character: RpgCharacter): Long

    @Update suspend fun updateCharacter(character: RpgCharacter)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuest(quest: RpgQuest): Long

    @Query("SELECT * FROM rpg_quest WHERE id = :id LIMIT 1")
    suspend fun quest(id: String): RpgQuest?

    @Update suspend fun updateQuest(quest: RpgQuest)

    @Query("SELECT * FROM rpg_quest ORDER BY periodKey DESC, title ASC")
    suspend fun quests(): List<RpgQuest>

    /** Dashboard query: do not materialize every historical daily quest on each launch. */
    @Query("SELECT * FROM rpg_quest WHERE periodKey = :periodKey ORDER BY title ASC")
    suspend fun questsForPeriod(periodKey: String): List<RpgQuest>

    @Query("SELECT * FROM rpg_xp_ledger ORDER BY id ASC")
    suspend fun ledgerEntries(): List<XpLedger>

    /** Avoid reading the full ledger merely to check a daily claim. */
    @Query("SELECT EXISTS(SELECT 1 FROM rpg_xp_ledger WHERE dedupeKey = :dedupeKey)")
    suspend fun hasLedgerEntry(dedupeKey: String): Boolean

    /** Count claims in SQLite instead of materializing unbounded history. */
    @Query("SELECT COUNT(*) FROM rpg_xp_ledger WHERE dedupeKey LIKE 'quest:%'")
    suspend fun questClaimCount(): Int

    /** Backpack keepsakes use existing immutable reward claims; no new DB tables. */
    @Query("SELECT COUNT(*) FROM rpg_xp_ledger WHERE dedupeKey GLOB 'daily_login:*'")
    suspend fun dailyLoginClaimCount(): Long

    @Query("SELECT COUNT(*) FROM rpg_xp_ledger WHERE dedupeKey GLOB 'pet_game:*'")
    suspend fun petGameClaimCount(): Long

    @Query("SELECT * FROM rpg_streak_day ORDER BY localDate ASC, habitId ASC")
    suspend fun allStreakDays(): List<StreakDay>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedger(entry: XpLedger): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: RpgAchievement): Long

    @Query("SELECT * FROM rpg_achievement ORDER BY id ASC")
    suspend fun achievements(): List<RpgAchievement>

    @Update suspend fun updateAchievement(achievement: RpgAchievement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordStreak(day: StreakDay)

    @Query("SELECT * FROM rpg_streak_day WHERE habitId = :habitId ORDER BY localDate DESC")
    suspend fun streakDays(habitId: String): List<StreakDay>

    @Query("SELECT * FROM rpg_item_stack WHERE quantity > 0 ORDER BY itemId ASC")
    suspend fun itemStacks(): List<RpgItemStack>

    @Query("SELECT * FROM rpg_item_stack WHERE itemId = :itemId LIMIT 1")
    suspend fun itemStack(itemId: String): RpgItemStack?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemStack(stack: RpgItemStack): Long

    @Update suspend fun updateItemStack(stack: RpgItemStack)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemReceipt(receipt: RpgItemReceipt): Long

    @Query("SELECT COUNT(*) FROM rpg_item_receipt WHERE dedupeKey = :key")
    suspend fun itemReceiptCount(key: String): Int

    @Query("UPDATE rpg_item_stack SET quantity = quantity - :amount, updatedAt = :updatedAt WHERE itemId = :itemId AND quantity >= :amount")
    suspend fun consumeItem(itemId: String, amount: Long, updatedAt: Long): Int
}

@Database(
    entities = [RpgCharacter::class, RpgQuest::class, XpLedger::class, RpgAchievement::class, StreakDay::class,
        RpgItemStack::class, RpgItemReceipt::class],
    version = 2,
    exportSchema = true
)
abstract class RpgDatabase : RoomDatabase() {
    abstract fun dao(): RpgDao

    /** Only the original guest database may import the legacy shared-preference XP.
     * A verified account must never inherit device-wide guest progress implicitly.
     * This is set before an account database is exposed to callers.
     */
    internal var allowLegacyXpImport: Boolean = true
        private set

    internal fun markVerifiedAccountStorage() {
        allowLegacyXpImport = false
    }

    companion object {
        /** Additive, non-destructive v1 → v2 migration: existing XP and quests survive. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rpg_item_stack` (`itemId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `rpg_item_receipt` (`dedupeKey` TEXT NOT NULL, `itemId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
            }
        }

        @Volatile private var instance: RpgDatabase? = null
        // Cache by opaque database filename, not the account identifier itself.
        private val accountInstances = mutableMapOf<String, RpgDatabase>()

        /**
         * Legacy guest database. Its filename must never change: existing offline
         * players keep their XP, streaks and quests across an app update.
         */
        fun get(context: Context): RpgDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, RpgDatabase::class.java, "chibi_rpg.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * A separate offline RPG database for an AUTHENTICATED account subject.
         * The caller must obtain [verifiedSubject] from a trusted identity verifier,
         * never from a user-editable name, email field or unverified token claim.
         *
         * This is storage isolation, NOT login/authentication. Guest data remains in
         * chibi_rpg.db; it is deliberately not copied into another account without
         * explicit consent and a separately tested import flow.
         */
        fun forVerifiedAccount(context: Context, verifiedSubject: String): RpgDatabase {
            val file = accountDatabaseFile(verifiedSubject)
            return synchronized(this) {
                accountInstances.getOrPut(file) {
                    Room.databaseBuilder(
                        context.applicationContext, RpgDatabase::class.java, file
                    ).addMigrations(MIGRATION_1_2).build().also {
                        it.markVerifiedAccountStorage()
                    }
                }
            }
        }

        /**
         * Logout/session switch: release the cached account database handle WITHOUT
         * deleting its on-disk RPG progress. The next verified login opens the
         * same file and preserves inventory, XP and quests.
         *
         * The caller must cancel outstanding DAO work before calling this.
         * This is not account erasure and does not touch guest or other accounts.
         */
        fun closeVerifiedAccount(verifiedSubject: String) {
            val file = accountDatabaseFile(verifiedSubject)
            synchronized(this) {
                accountInstances.remove(file)?.close()
            }
        }

        /**
         * Explicit local RPG erasure for an authenticated account after logout.
         * Do not call while an account's screens or coroutines still use its DAO.
         * Never deletes the guest database or any other account's database.
         * This does not erase independent Fitness/Focus/Alarm preferences.
         */
        fun deleteVerifiedAccount(context: Context, verifiedSubject: String): Boolean {
            val file = accountDatabaseFile(verifiedSubject)
            return synchronized(this) {
                accountInstances.remove(file)?.close()
                context.applicationContext.deleteDatabase(file)
            }
        }

        internal fun accountDatabaseFile(verifiedSubject: String): String {
            require(verifiedSubject.isNotBlank() && verifiedSubject.length <= 128 &&
                    verifiedSubject == verifiedSubject.trim() &&
                    verifiedSubject.none { Character.isISOControl(it) }) {
                "Invalid verified account subject"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifiedSubject.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "chibi_rpg_account_${digest}.db"
        }
    }
}
