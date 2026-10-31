package com.chibialarm.app.rpg

import android.content.Context
import androidx.room.*

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
}

@Database(
    entities = [RpgCharacter::class, RpgQuest::class, XpLedger::class, RpgAchievement::class, StreakDay::class],
    version = 1,
    exportSchema = true
)
abstract class RpgDatabase : RoomDatabase() {
    abstract fun dao(): RpgDao

    companion object {
        @Volatile private var instance: RpgDatabase? = null
        fun get(context: Context): RpgDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, RpgDatabase::class.java, "chibi_rpg.db"
                ).build().also { instance = it }
            }
    }
}
