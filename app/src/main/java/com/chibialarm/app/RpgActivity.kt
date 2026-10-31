package com.chibialarm.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.chibialarm.app.rpg.RpgAchievement
import com.chibialarm.app.rpg.RpgCharacter
import com.chibialarm.app.rpg.RpgDatabase
import com.chibialarm.app.rpg.RpgEngine
import com.chibialarm.app.rpg.RpgQuest
import com.chibialarm.app.rpg.RpgRules
import com.chibialarm.app.rpg.StreakDay
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Offline-first RPG dashboard. No account or network access is required. */
class RpgActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database by lazy { RpgDatabase.get(applicationContext) }
    private val engine by lazy { RpgEngine(database) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun label(value: String, size: Float = 17f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(83, 54, 96))
        setPadding(8, 10, 8, 10)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 15, 18, 15)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 30f
        }
        elevation = 3f
    }

    private fun refresh() {
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    engine.initialize(LifeData.points(applicationContext))
                    val legacyDays = getSharedPreferences("growth", MODE_PRIVATE).all
                        .mapNotNull { (key, value) ->
                            if (value != true || !key.startsWith("done:") ||
                                key.length <= 16 || key[15] != ':') null
                            else {
                                val date = key.substring(5, 15)
                                if (runCatching { LocalDate.parse(date) }.isFailure) null
                                else StreakDay("growth:" + key.substring(16), date, completed = true)
                            }
                        }
                    engine.migrateGrowth(legacyDays)
                    val today = LifeData.today()
                    engine.syncProgress("routine:" + today,
                        if (LifeData.count(applicationContext) > 0) 1 else 0)
                    engine.syncProgress("focus:" + today,
                        getSharedPreferences("focus", MODE_PRIVATE).getInt("sessions:" + today, 0))
                    engine.syncProgress("fitness:" + today,
                        WorkoutData.history(applicationContext).count { it.date == today })
                    val dao = database.dao()
                    Triple(dao.character() ?: RpgCharacter(), dao.quests(), dao.achievements())
                }
                render(data.first, data.second, data.third)
            } catch (error: Exception) {
                Toast.makeText(this@RpgActivity,
                    "Không tải được RPG: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun claim(quest: RpgQuest) {
        scope.launch {
            try {
                val changed = withContext(Dispatchers.IO) {
                    engine.claimQuest(quest.id)
                }
                if (!changed) Toast.makeText(this@RpgActivity,
                    "Nhiệm vụ đã được cập nhật hoặc không còn khả dụng", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (error: Exception) {
                Toast.makeText(this@RpgActivity,
                    "Lỗi lưu tiến độ: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(character: RpgCharacter, quests: List<RpgQuest>,
                       achievements: List<RpgAchievement>) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 26, 24, 30)
            setBackgroundColor(Color.rgb(255, 241, 249))
        }
        root.addView(label("🐰 CHIBI LIFE • RPG", 27f))
        root.addView(label("Hành trình trưởng thành của bạn • chơi ngoại tuyến", 14f))
        val hero = card()
        hero.addView(label("🌟 ${character.name} • Cấp ${RpgRules.level(character.totalXp)}", 23f))
        hero.addView(label("⭐ ${character.totalXp} EXP    🪙 ${character.coins} xu", 17f))
        hero.addView(label("EXP từ lịch cũ đã được nhập một lần; dữ liệu cũ vẫn được giữ nguyên.", 12f))
        root.addView(hero)
        root.addView(label("📜 Nhiệm vụ hôm nay", 22f))
        val today = com.chibialarm.app.LifeData.today()
        val current = quests.filter { it.periodKey == today }
        if (current.isEmpty()) root.addView(label("Chưa có nhiệm vụ hôm nay."))
        current.forEach { quest ->
            val panel = card()
            panel.addView(label(quest.title, 18f))
            panel.addView(label("${quest.progress}/${quest.target} • +${quest.rewardXp} EXP • +${quest.rewardCoins} xu", 14f))
            panel.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = quest.target.coerceAtLeast(1)
                progress = quest.progress
            })
            val button = Button(this).apply {
                text = when (quest.state) {
                    "CLAIMED" -> "✓ Đã nhận thưởng"
                    "COMPLETED" -> "🎁 Nhận thưởng"
                    else -> when (quest.category) {
                        "ROUTINE" -> "📆 Mở lịch sinh hoạt"
                        "FOCUS" -> "🍅 Mở Focus"
                        else -> "🏃 Mở nhật ký tập luyện"
                    }
                }
                isEnabled = quest.state != "CLAIMED"
                setOnClickListener {
                    if (quest.state == "COMPLETED") claim(quest)
                    else if (quest.state == "ACTIVE") {
                        val destination = when (quest.category) {
                            "ROUTINE" -> RoutineActivity::class.java
                            "FOCUS" -> FocusActivity::class.java
                            else -> WorkoutActivity::class.java
                        }
                        startActivity(Intent(this@RpgActivity, destination))
                    }
                }
            }
            panel.addView(button)
            root.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        }
        root.addView(label("🏅 Thành tựu", 22f))
        achievements.forEach { achievement ->
            root.addView(label(
                (if (achievement.unlockedAt != null) "🏆 " else "🔒 ") +
                    achievement.title + " • ${achievement.progress}/${achievement.target}", 16f))
        }
        root.addView(Button(this).apply {
            text = "📆 Mở lịch hoạt động"
            setOnClickListener { startActivity(Intent(this@RpgActivity, RoutineActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "🏃 Mở Chibi Fitness"
            setOnClickListener { startActivity(Intent(this@RpgActivity, FitnessActivity::class.java)) }
        })
        root.addView(label("Bản RPG thử nghiệm: nhiệm vụ lấy tiến độ từ lịch, số phiên Focus và nhật ký tập. Buổi tập được ghi thủ công vẫn được tính; chưa có xác minh từ thiết bị đeo.", 12f))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}
