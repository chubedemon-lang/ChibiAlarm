package com.chibialarm.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.chibialarm.app.rpg.RpgAchievement
import com.chibialarm.app.rpg.RpgCharacter
import com.chibialarm.app.rpg.RpgDatabase
import com.chibialarm.app.rpg.RpgDataExport
import com.chibialarm.app.rpg.RpgEngine
import com.chibialarm.app.rpg.RpgQuest
import com.chibialarm.app.rpg.RpgRules
import com.chibialarm.app.rpg.RpgItemStack
import com.chibialarm.app.rpg.RpgItemCatalog
import com.chibialarm.app.rpg.RpgInventoryEngine
import com.chibialarm.app.rpg.StreakDay
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Offline-first RPG dashboard. No account or network access is required. */
class RpgActivity : Activity() {
    private val exportRequestCode = 5203
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var refreshJob: Job? = null
    private val database by lazy { RpgDatabase.get(applicationContext) }
    private val sceneState = ChibiSceneState()
    private val petSession = ChibiPetSession()
    private var petClaimPending = false
    private val engine by lazy { RpgEngine(database) }
    private val itemEngine by lazy { RpgInventoryEngine(database) }
    private data class Dashboard(
        val character: RpgCharacter,
        val quests: List<RpgQuest>,
        val achievements: List<RpgAchievement>,
        val growthStreak: Int,
        val dailyClaimed: Boolean,
        val petGameClaimed: Boolean,
        val charmClaimed: Boolean,
        val inventory: List<ChibiInventory.Item>,
        val itemStacks: List<RpgItemStack>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preserve the current room and movement through rotation/process recreation.
        // RPG rewards remain exclusively in Room, not in this transient Bundle.
        savedInstanceState?.let { saved ->
            petSession.restore(LifeData.today(), ChibiPetSession.Snapshot(
                saved.getString("chibi_pet_day").orEmpty(),
                saved.getInt("chibi_pet_taps", 0)
            ))
            val scene = ChibiSceneState.Scene.values().firstOrNull {
                it.name == saved.getString("chibi_scene")
            }
            if (scene != null) {
                sceneState.restore(ChibiSceneState.Snapshot(
                    scene,
                    saved.getFloat("chibi_hero_x", 160f),
                    saved.getFloat("chibi_destination_x", 160f),
                    saved.getBoolean("chibi_facing_right", true),
                    saved.getInt("chibi_garden_growth", 0)
                ))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val snapshot = sceneState.snapshot()
        outState.putString("chibi_scene", snapshot.scene.name)
        outState.putFloat("chibi_hero_x", snapshot.heroX)
        outState.putFloat("chibi_destination_x", snapshot.destinationX)
        outState.putBoolean("chibi_facing_right", snapshot.facingRight)
        outState.putInt("chibi_garden_growth", snapshot.gardenGrowth)
        val petSnapshot = petSession.snapshot(LifeData.today())
        outState.putString("chibi_pet_day", petSnapshot.day)
        outState.putInt("chibi_pet_taps", petSnapshot.taps)
        super.onSaveInstanceState(outState)
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
        // Avoid a stale, slower refresh overwriting a newer reward/quest state.
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    engine.initialize(LifeData.points(applicationContext))
                    val legacyDays = GrowthHabits.activeHistory(applicationContext)
                    val growthStreak = engine.syncGrowth(legacyDays)
                    val today = LifeData.today()
                    engine.syncCheckableProgress("routine:" + today,
                        if (LifeData.count(applicationContext) > 0) 1 else 0)
                    engine.syncProgress("focus:" + today,
                        FocusTimer.sessionsOnDay(applicationContext, today))
                    // Workout journal entries are user-editable. Reconcile deletions before
                    // claiming, just like reversible Routine/Growth check-ins.
                    engine.syncCheckableProgress("fitness:" + today,
                        WorkoutData.history(applicationContext).count { it.date == today })
                    engine.syncCheckableProgress("growth:" + today,
                        legacyDays.count { it.localDate == today && it.completed })
                    val dao = database.dao()
                    Dashboard(dao.character() ?: RpgCharacter(), dao.questsForPeriod(today),
                        dao.achievements(), growthStreak,
                        dao.hasLedgerEntry("daily_login:" + today),
                        dao.hasLedgerEntry("pet_game:" + today),
                        dao.hasLedgerEntry("garden_charm:boost:" + today),
                        ChibiInventory.fromClaims(
                            dao.dailyLoginClaimCount(), dao.petGameClaimCount(),
                            dao.questClaimCount().toLong()
                        ), dao.itemStacks())
                }
                render(data.character, data.quests, data.achievements,
                    data.growthStreak, data.dailyClaimed, data.petGameClaimed,
                    data.charmClaimed, data.inventory, data.itemStacks)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@RpgActivity,
                    "Không tải được RPG: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun editCharacterName(current: String) {
        val input = EditText(this).apply {
            setText(current)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(24))
            hint = "Tên nhân vật"
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("✏️ Đặt tên Chibi")
            .setView(input)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Hủy", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val proposed = input.text.toString().trim()
                if (proposed.length !in 2..24 || proposed.any { Character.isISOControl(it) }) {
                    input.error = "Tên cần 2–24 ký tự hợp lệ"
                } else {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { engine.renameCharacter(proposed) }
                            dialog.dismiss()
                            refresh()
                        } catch (_: Exception) {
                            input.error = "Không thể lưu tên, hãy thử lại"
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    /** Android Storage Access Framework: user chooses the destination, no storage permission. */
    private fun exportRpgData() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "chibi-life-rpg-${LocalDate.now()}.json")
        }
        try {
            startActivityForResult(intent, exportRequestCode)
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được trình chọn nơi lưu tệp", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Android activity result callback remains supported without AndroidX Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportRequestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val currentXp = database.dao().character()?.totalXp?.coerceAtLeast(0L) ?: 0L
                    val currentLevel = RpgRules.level(currentXp)
                    val outfit = ChibiWardrobeStore(applicationContext).selected(currentLevel)
                    val json = RpgDataExport.create(database, outfit)
                    contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(json)
                    } ?: error("Không thể ghi tệp đã chọn")
                }
                Toast.makeText(this@RpgActivity, "Đã xuất dữ liệu RPG", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this@RpgActivity, "Xuất dữ liệu thất bại, vui lòng thử lại", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Local crafting: one herb + one star, no XP/coins or network calls. */
    private fun craftGardenCharm() {
        val receipt = "craft:garden_charm:" + java.util.UUID.randomUUID().toString()
        scope.launch {
            try {
                val crafted = withContext(Dispatchers.IO) {
                    itemEngine.craftGardenCharm(receipt)
                }
                Toast.makeText(this@RpgActivity,
                    if (crafted) "🍀 Đã chế tạo Bùa khu vườn!"
                    else "Chưa đủ 1 thảo mộc và 1 mảnh sao", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (_: Exception) {
                Toast.makeText(this@RpgActivity,
                    "Không lưu được vật phẩm, vui lòng thử lại", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun activateDailyGardenCharm() {
        scope.launch {
            try {
                val activated = withContext(Dispatchers.IO) {
                    engine.activateDailyGardenCharm(LocalDate.parse(LifeData.today()))
                }
                Toast.makeText(this@RpgActivity,
                    if (activated) "🍀 Bùa tỏa sáng! +20 EXP, +1 xu"
                    else "Không có bùa hoặc đã dùng hôm nay", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (_: Exception) {
                Toast.makeText(this@RpgActivity,
                    "Không kích hoạt được bùa, vật phẩm vẫn được giữ", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun claimDailyLogin() {
        scope.launch {
            try {
                val claimed = withContext(Dispatchers.IO) {
                    engine.claimDailyLogin(LocalDate.parse(LifeData.today()))
                }
                Toast.makeText(this@RpgActivity,
                    if (claimed) "Điểm danh thành công! +15 EXP, +3 xu"
                    else "Bạn đã điểm danh hôm nay rồi", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (_: Exception) {
                Toast.makeText(this@RpgActivity,
                    "Không thể lưu điểm danh, hãy thử lại", Toast.LENGTH_LONG).show()
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
                       achievements: List<RpgAchievement>, growthStreak: Int,
                       dailyClaimed: Boolean, petGameClaimed: Boolean,
                       charmClaimed: Boolean, inventory: List<ChibiInventory.Item>,
                       itemStacks: List<RpgItemStack>) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 26, 24, 30)
            setBackgroundColor(Color.rgb(255, 241, 249))
        }
        root.addView(label("🐰 CHIBI LIFE • RPG", 27f))
        root.addView(label("Hành trình trưởng thành của bạn • chơi ngoại tuyến", 14f))
        val hero = card()
        val level = RpgRules.level(character.totalXp)
        val wardrobe = ChibiWardrobeStore(this)
        val equippedOutfit = wardrobe.selected(level)
        val xpSpent = (1 until level).sumOf { RpgRules.xpToNext(it) }
        val xpRequired = RpgRules.xpToNext(level)
        val xpInLevel = (character.totalXp - xpSpent).coerceIn(0L, xpRequired)
        val petProgress = label(
            if (petGameClaimed) "♥ Đã nhận thưởng chơi cùng Chibi hôm nay"
            else "♥ Vuốt ve Chibi 3 lần: ${petSession.progress(LifeData.today())}/3 • +10 EXP, +2 xu", 14f)
        val sceneHint = label(
            if (sceneState.scene == ChibiSceneState.Scene.GARDEN)
                "🌱 Khu vườn • Chạm luống cây để tưới, chạm cửa để về phòng"
            else "🏡 Phòng ngủ • Chạm sàn để đi, cửa để ra vườn", 14f)
        val sceneButton = Button(this).apply {
            text = if (sceneState.scene == ChibiSceneState.Scene.GARDEN)
                "🚪 Về phòng ngủ" else "🚪 Ra khu vườn"
            contentDescription = "Chuyển giữa phòng ngủ và khu vườn"
        }
        val gardenButton = Button(this).apply {
            text = "🌱 Tưới cây • Nhặt nguyên liệu (1 lần/ngày)"
            contentDescription = "Tưới cây khu vườn, tìm một vật phẩm mỗi ngày"
            isEnabled = sceneState.scene == ChibiSceneState.Scene.GARDEN
        }
        val sceneView = ChibiHeroView(this).apply {
            sceneState = this@RpgActivity.sceneState
            outfit = equippedOutfit
            onRoomChanged = { inGarden ->
                sceneHint.text = if (inGarden)
                    "🌱 Khu vườn • Chạm luống cây để tưới, chạm cửa để về phòng"
                else "🏡 Phòng ngủ • Chạm sàn để đi, cửa để ra vườn"
                sceneButton.text = if (inGarden) "🚪 Về phòng ngủ" else "🚪 Ra khu vườn"
                gardenButton.isEnabled = inGarden
            }
            onGardenAction = {
                // The water effect always plays; the inventory reward is once
                // per local calendar day, enforced by a durable Room receipt.
                scope.launch {
                    try {
                        val itemId = withContext(Dispatchers.IO) {
                            itemEngine.forageGarden(LocalDate.parse(LifeData.today()))
                        }
                        val message = if (itemId == null) {
                            "🌱 Đã chăm vườn hôm nay. Mai quay lại tìm vật phẩm nhé!"
                        } else {
                            val item = RpgItemCatalog.describe(itemId)
                            "🌱 Nhặt được ${item.icon} ${item.title}! (1 lần/ngày)"
                        }
                        Toast.makeText(this@RpgActivity, message, Toast.LENGTH_SHORT).show()
                        if (itemId != null) refresh()
                    } catch (_: Exception) {
                        Toast.makeText(this@RpgActivity,
                            "Không lưu được vật phẩm khu vườn", Toast.LENGTH_LONG).show()
                    }
                }
            }
            onSceneAction = { target ->
                val destination = when (target) {
                    ChibiSceneActions.Target.BED -> AlarmSettingsActivity::class.java
                    ChibiSceneActions.Target.DESK -> FocusActivity::class.java
                    ChibiSceneActions.Target.TRAINING -> FitnessActivity::class.java
                    else -> null
                }
                if (destination != null) startActivity(Intent(this@RpgActivity, destination))
            }
            onPet = {
                if (petGameClaimed) {
                    petProgress.text = "♥ Chibi đã nhận quà hôm nay. Mai chơi tiếp nhé!"
                } else if (!petClaimPending) {
                    val taps = petSession.pet(LifeData.today())
                    petProgress.text = "♥ Vuốt ve Chibi: $taps/3"
                    if (taps == 3) {
                        petClaimPending = true
                        scope.launch {
                            try {
                                val rewarded = withContext(Dispatchers.IO) {
                                    engine.claimDailyPetGame(LocalDate.parse(LifeData.today()))
                                }
                                petSession.clear(LifeData.today())
                                petClaimPending = false
                                Toast.makeText(this@RpgActivity,
                                    if (rewarded) "Chibi vui quá! +10 EXP, +2 xu"
                                    else "Đã nhận quà hôm nay rồi", Toast.LENGTH_SHORT).show()
                                refresh()
                            } catch (_: Exception) {
                                petClaimPending = false
                                petSession.clear(LifeData.today())
                                petProgress.text = "Chưa lưu được phần thưởng, thử lại nhé"
                                refresh()
                            }
                        }
                    }
                }
            }
        }
        hero.addView(sceneView)
        hero.addView(sceneHint)
        sceneButton.setOnClickListener { sceneView.toggleScene() }
        hero.addView(sceneButton)
        gardenButton.setOnClickListener { sceneView.waterGarden() }
        hero.addView(gardenButton)
        // Touch alternatives for TalkBack and small-screen precision.
        val quickActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Triple("⏰", "Mở báo thức", AlarmSettingsActivity::class.java),
            Triple("🍅", "Mở Focus", FocusActivity::class.java),
            Triple("🏃", "Mở Fitness", FitnessActivity::class.java)
        ).forEach { (symbol, description, destination) ->
            quickActions.addView(Button(this).apply {
                text = symbol
                contentDescription = description
                setOnClickListener { startActivity(Intent(this@RpgActivity, destination)) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        hero.addView(quickActions)
        hero.addView(petProgress)
        hero.addView(label("🌟 ${character.name} • Cấp $level", 23f))
        val nextOutfitHint = ChibiCosmetics.nextUnlock(level)?.let {
            " • Mở ${it.label} ở cấp ${it.unlockLevel}"
        } ?: " • Đã mở toàn bộ trang phục hiện tại"
        val wardrobeStatus = label("👗 ${equippedOutfit.label}$nextOutfitHint", 14f)
        hero.addView(wardrobeStatus)
        hero.addView(Button(this).apply {
            text = "🎒 Tủ đồ • Chọn trang phục"
            contentDescription = "Mở tủ đồ và chọn trang phục Chibi đã mở khóa"
            setOnClickListener {
                val available = ChibiCosmetics.Outfit.values().toList()
                val labels = available.map {
                    if (ChibiCosmetics.canEquip(level, it)) "✓ ${it.label}"
                    else "🔒 ${it.label} • Mở ở cấp ${it.unlockLevel}"
                }.toTypedArray()
                val selectedIndex = available.indexOf(wardrobe.selected(level))
                AlertDialog.Builder(this@RpgActivity)
                    .setTitle("🎒 Tủ đồ Chibi")
                    .setSingleChoiceItems(labels, selectedIndex) { dialog, index ->
                        val selected = available[index]
                        if (!ChibiCosmetics.canEquip(level, selected)) {
                            Toast.makeText(this@RpgActivity,
                                "Trang phục mở ở cấp ${selected.unlockLevel}", Toast.LENGTH_SHORT).show()
                            return@setSingleChoiceItems
                        }
                        if (wardrobe.equip(level, selected)) {
                            sceneView.outfit = selected
                            wardrobeStatus.text = "👗 ${selected.label}$nextOutfitHint"
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@RpgActivity,
                                "Chưa lưu được trang phục, thử lại nhé", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Đóng", null)
                    .show()
            }
        })
        hero.addView(Button(this).apply {
            text = "🎒 Ba lô • Kỷ vật và vật phẩm"
            contentDescription = "Xem kỷ vật từ lịch sử nhiệm vụ và vật phẩm RPG lưu trong máy"
            setOnClickListener {
                val summary = inventory.joinToString("\n") { item ->
                    "${item.icon} ${item.title}: ${item.count}"
                }
                val durableItems = itemStacks.joinToString("\n") { stack ->
                    val definition = RpgItemCatalog.describe(stack.itemId)
                    "${definition.icon} ${definition.title}: ${stack.quantity}"
                }.ifEmpty { "Chưa có vật phẩm." }
                AlertDialog.Builder(this@RpgActivity)
                    .setTitle("🎒 Ba lô Chibi")
                    .setMessage("VẬT PHẨM LƯU NGOẠI TUYẾN\n" + durableItems +
                        "\n\nKỶ VẬT TỪ NHIỆM VỤ\n" + summary +
                        "\n\nNguyên liệu có thể chế tạo bùa. Chưa hỗ trợ mua bán hoặc giao dịch.")
                    .setPositiveButton("Đóng", null)
                    .show()
            }
        })
        val herbs = itemStacks.firstOrNull { it.itemId == "herb" }?.quantity ?: 0L
        val stars = itemStacks.firstOrNull { it.itemId == "star_fragment" }?.quantity ?: 0L
        hero.addView(label("🌱 Tưới cây trong vườn để nhặt 1 nguyên liệu mỗi ngày. " +
            "Đủ 1 thảo mộc và 1 mảnh sao có thể chế tạo bùa.", 14f))
        hero.addView(Button(this).apply {
            text = "🛠️ Chế tạo Bùa khu vườn • 🌿 $herbs/1 ✨ $stars/1"
            contentDescription = "Chế tạo bùa từ một thảo mộc và một mảnh sao"
            isEnabled = herbs >= 1L && stars >= 1L
            setOnClickListener {
                isEnabled = false
                craftGardenCharm()
            }
        })
        val charms = itemStacks.firstOrNull { it.itemId == "garden_charm" }?.quantity ?: 0L
        hero.addView(Button(this).apply {
            text = if (charmClaimed) "✓ Đã dùng bùa hôm nay"
                else "🍀 Dùng Bùa khu vườn (+20 EXP, +1 xu) • Có $charms"
            contentDescription = if (charmClaimed) "Đã nhận thưởng bùa hôm nay"
                else "Tiêu hao một bùa khu vườn để nhận 20 kinh nghiệm và một xu"
            isEnabled = !charmClaimed && charms >= 1L
            setOnClickListener {
                isEnabled = false
                activateDailyGardenCharm()
            }
        })
        hero.addView(Button(this).apply {
            text = "✏️ Đổi tên Chibi"
            setOnClickListener { editCharacterName(character.name) }
        })
        hero.addView(label("⭐ ${character.totalXp} EXP    🪙 ${character.coins} xu", 17f))
        hero.addView(label("Tiến độ cấp tiếp theo: $xpInLevel / $xpRequired EXP", 14f))
        hero.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = xpRequired.toInt().coerceAtLeast(1)
            progress = xpInLevel.toInt()
        })
        hero.addView(label("🔥 Chuỗi thói quen hiện tại: $growthStreak ngày", 15f))
        hero.addView(Button(this).apply {
            text = if (dailyClaimed) "✓ Đã điểm danh hôm nay"
                else "🎁 Điểm danh (+15 EXP, +3 xu)"
            isEnabled = !dailyClaimed
            contentDescription = if (dailyClaimed) "Đã nhận thưởng điểm danh hôm nay"
                else "Nhận thưởng điểm danh hằng ngày: 15 kinh nghiệm và 3 xu"
            setOnClickListener { claimDailyLogin() }
        })
        hero.addView(label("EXP từ lịch cũ đã được nhập một lần; dữ liệu cũ vẫn được giữ nguyên.", 12f))
        root.addView(hero)
        root.addView(label("📜 Nhiệm vụ hôm nay", 22f))
        if (quests.isEmpty()) root.addView(label("Chưa có nhiệm vụ hôm nay."))
        quests.forEach { quest ->
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
                        "GROWTH" -> "🌱 Mở thói quen"
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
                            "GROWTH" -> GrowthActivity::class.java
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
        root.addView(Button(this).apply {
            text = "📤 Xuất dữ liệu RPG ra JSON"
            contentDescription = "Xuất nhân vật, nhiệm vụ, thành tựu và lịch sử RPG vào tệp riêng"
            setOnClickListener { exportRpgData() }
        })
        root.addView(label("Tệp xuất có thể chứa thông tin thói quen cá nhân. Hãy lưu ở nơi an toàn. Không bao gồm khóa Gemini, lịch sử chat hay dữ liệu báo thức.", 12f))
        root.addView(label("Bản RPG thử nghiệm: nhiệm vụ lấy tiến độ từ lịch, Focus, thói quen và nhật ký tập. Buổi tập được ghi thủ công vẫn được tính; chưa có xác minh từ thiết bị đeo.", 12f))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}
