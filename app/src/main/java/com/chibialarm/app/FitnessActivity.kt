package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.view.Gravity
import android.widget.*

/** Catalog-driven free coach: selectable tracks, difficulty, guided session, weekly reminder. */
class FitnessActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("chibi_fitness_offline",0) }
    private var conversation: FitnessNaturalRequest.Result? = null
    // Raw wording is shown only in this Activity's transient chat panel; it is never snapshotted.
    private val coachTranscript = FitnessCoachChatTranscript()
    private var coachTranscriptView: TextView? = null
    private var coachTranscriptClearButton: Button? = null
    private var speechInput: EditText? = null
    private var speechOutput: TextToSpeech? = null
    private var speechReady = false
    // The most recent coach reply stays in Activity memory only; it is never persisted.
    private val coachSpeechMemory = FitnessCoachSpeechMemory()
    private var speechReplayButton: Button? = null
    private var speechStopButton: Button? = null
    private val speechRequestCode = 4107
    private var recommendationGoalSpinner: Spinner? = null
    private var recommendationEnergySpinner: Spinner? = null
    private var recommendationEquipmentSpinner: Spinner? = null
    private val additionalEquipmentCheckBoxes = linkedMapOf<TrainingEquipment, CheckBox>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != speechRequestCode || resultCode != RESULT_OK) return
        val recognized = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim().orEmpty()
        if (recognized.isNotEmpty()) {
            speechInput?.setText(recognized)
            speechInput?.setSelection(recognized.length)
            Toast.makeText(this, "Kiểm tra lời nhận dạng rồi nhấn Chibi hiểu yêu cầu", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        // Drop transient raw text on finish and configuration recreation.
        coachTranscript.clear()
        coachSpeechMemory.clear()
        speechReplayButton = null
        speechStopButton = null
        coachTranscriptView = null
        coachTranscriptClearButton = null
        coachRequestInput = null
        historyEffortInsightView = null
        historyEffortAction = null
        effortPlanSuggestion = null
        speechOutput?.stop()
        speechOutput?.shutdown()
        speechOutput = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshWorkoutHistoryInsight()
    }

    private fun sayFollowUp(message: String) {
        coachSpeechMemory.remember(message)
        speechReplayButton?.isEnabled = coachSpeechMemory.hasReply()
        speechStopButton?.isEnabled = speechReady
        val speechText = coachSpeechMemory.replayText(
            voiceEnabled = speechOutput != null,
            engineReady = speechReady
        ) ?: return
        speechOutput?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "fitness-follow-up")
    }

    private fun renderCoachTranscript() {
        val panel = coachTranscriptView ?: return
        val rendered = coachTranscript.render()
        panel.text = rendered
        panel.visibility = if (rendered.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        coachTranscriptClearButton?.visibility =
            if (rendered.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private lateinit var root:LinearLayout
    private lateinit var level:Spinner
    private lateinit var length:Spinner
    private lateinit var filter:Spinner
    private var impactRestrictionSpinner: Spinner? = null
    private var wristLoadCheckBox: CheckBox? = null
    private var floorPositionCheckBox: CheckBox? = null
    private var overheadReachCheckBox: CheckBox? = null
    private var programContainer: LinearLayout? = null
    private var historySummaryView: TextView? = null
    private var historySuggestionView: TextView? = null
    private var historyRecoveryButton: Button? = null
    private var historyEffortInsightView: TextView? = null
    private var historyEffortAction: Button? = null
    private var coachRequestInput: EditText? = null
    private var effortPlanSuggestion: WorkoutEffortPlanAdvisor.Suggestion? = null
    private val levels=arrayOf("🌱 Mới bắt đầu","🌿 Đang tập đều","🔥 Có kinh nghiệm")
    private val lengths=WorkoutDurationChoices.labels
    private val mins=WorkoutDurationChoices.minutes
    private val categories=arrayOf("Tất cả","Toàn thân","Sức mạnh","Cardio","Linh hoạt","Vận động nhẹ","Phục hồi nhẹ","Thử thách")
    private fun txt(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(73,59,88));setPadding(7,6,7,7)
    }
    private fun panel(bg:Int)=GradientDrawable().apply { setColor(bg);cornerRadius=33f }
    /** Restore only validated, coarse selections handed off by the offline coach. */
    private fun restoreCoachHandoff(source: Intent): FitnessNaturalRequest.Result? {
        val keys = listOf(
            "coach_goal", "coach_level", "coach_energy", "coach_minutes",
            "coach_equipment", "coach_impact",
            "coach_avoid_wrist_load", "coach_avoid_floor_positions",
            "coach_avoid_overhead_reach", "coach_additional_equipment"
        )
        if (keys.none(source::hasExtra)) return null
        fun intValue(key: String): Int? =
            if (source.hasExtra(key)) source.getIntExtra(key, Int.MIN_VALUE) else null
        fun booleanValue(key: String): Boolean? =
            if (source.hasExtra(key)) source.getBooleanExtra(key, false) else null
        return FitnessCoachSessionSnapshot(
            goal = intValue("coach_goal"),
            level = intValue("coach_level"),
            energy = intValue("coach_energy"),
            minutes = intValue("coach_minutes"),
            equipment = intValue("coach_equipment"),
            impact = intValue("coach_impact"),
            avoidWristLoad = booleanValue("coach_avoid_wrist_load"),
            avoidFloorPositions = booleanValue("coach_avoid_floor_positions"),
            avoidOverheadReach = booleanValue("coach_avoid_overhead_reach"),
            additionalEquipment = source.getStringArrayListExtra("coach_additional_equipment")
                ?.mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
                ?.toSet()
        ).restore()
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Retain only coarse selections across a configuration change. Raw speech/text is never kept.
        conversation = FitnessCoachSessionSnapshot.restore(lastNonConfigurationInstance as? FitnessCoachSessionSnapshot)
            ?: restoreCoachHandoff(intent)
        render()
    }

    /** The safety selection survives rotation in memory only; it is not written to disk or backup. */
    override fun onRetainNonConfigurationInstance(): Any? =
        FitnessCoachSessionSnapshot.capture(
            conversation,
            impactRestrictionSpinner?.selectedItemPosition,
            selectedAvoidWristLoad = if (wristLoadCheckBox?.isChecked == true || conversation?.avoidWristLoad != null)
                wristLoadCheckBox?.isChecked else null,
            selectedAvoidFloorPositions = if (floorPositionCheckBox?.isChecked == true || conversation?.avoidFloorPositions != null)
                floorPositionCheckBox?.isChecked else null,
            selectedAvoidOverheadReach = if (overheadReachCheckBox?.isChecked == true || conversation?.avoidOverheadReach != null)
                overheadReachCheckBox?.isChecked else null,
            selectedAdditionalEquipment = currentAdditionalEquipment().takeIf {
                it.isNotEmpty() || conversation?.additionalEquipment != null
            }
        )

    private fun effectiveImpactRestriction(): Int =
        FitnessCoachSessionSnapshot.effectiveImpact(
            conversation?.impact, impactRestrictionSpinner?.selectedItemPosition ?: 0
        )

    /** Reads coarse totals and structured effort labels; free-text journal notes never enter the advisor. */
    private fun refreshWorkoutHistoryInsight() {
        val workouts = WorkoutData.history(this)
        val summary = WorkoutHistoryAdvisor.summarize(workouts)
        val effortSuggestion = WorkoutEffortPlanAdvisor.suggest(workouts)
        effortPlanSuggestion = effortSuggestion
        historyEffortInsightView?.apply {
            visibility = if (effortSuggestion != null) android.view.View.VISIBLE else android.view.View.GONE
            text = effortSuggestion?.message.orEmpty()
        }
        historyEffortAction?.visibility = if (effortSuggestion != null) android.view.View.VISIBLE else android.view.View.GONE
        historySummaryView?.apply {
            visibility = if (summary.sessions > 0) android.view.View.VISIBLE else android.view.View.GONE
            text = "📊 Nhật ký 7 ngày: ${summary.sessions} buổi • ${summary.activeDays} ngày vận động • ${summary.totalMinutes} phút (ghi trên máy)."
        }
        historySuggestionView?.apply {
            visibility = if (summary.suggestLighterSession) android.view.View.VISIBLE else android.view.View.GONE
            text = "🐰 Nhật ký cho thấy bạn đã vận động ${summary.activeDays}/7 ngày và ghi ${summary.totalMinutes} phút. Bạn có thể cân nhắc nghỉ hoặc chọn buổi phục hồi nhẹ. Đây chỉ là gợi ý từ nhật ký tự ghi, không phải đánh giá hồi phục hay tư vấn y khoa."
        }
        historyRecoveryButton?.visibility = if (summary.suggestLighterSession) android.view.View.VISIBLE else android.view.View.GONE
    }
    private fun render() {
        root=LinearLayout(this).apply {
            orientation=1;setPadding(22,26,22,28);setBackgroundColor(Color.rgb(246,250,249))
        }
        val hero=LinearLayout(this).apply {
            orientation=1;gravity=Gravity.CENTER;setPadding(22,24,22,24)
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(215,248,226),Color.rgb(230,223,255))).apply { cornerRadius=35f }
        }
        hero.addView(txt("🐰 CHIBI FITNESS PRO",27f))
        hero.addView(txt("13 chương trình • miễn phí • offline • không cần phòng gym",14f))
        root.addView(hero)
        root.addView(txt("🎚 Trình độ của bạn",19f))
        level=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,levels)
            setSelection(prefs.getInt("fitLevel",0).coerceIn(0,2))
        }
        root.addView(level)
        root.addView(txt("⏱ Thời lượng bạn muốn",19f))
        length=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,lengths)
            val savedMinutes = if (prefs.contains("fitTimeMinutes")) {
                prefs.getInt("fitTimeMinutes", 20)
            } else {
                WorkoutDurationChoices.legacyMinutesForIndex(prefs.getInt("fitTime", 2))
            }
            setSelection(WorkoutDurationChoices.indexForMinutes(savedMinutes))
        }
        root.addView(length)
        root.addView(txt("💬 Nói với Chibi về buổi tập",19f))
        val naturalRequest=EditText(this).apply {
            hint="Ví dụ: Hôm nay mệt, muốn giãn cơ 10 phút, không dụng cụ"
            minLines=2
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription="Yêu cầu giáo án bằng tiếng Việt"
        }
        root.addView(naturalRequest)
        speechInput = naturalRequest
        coachRequestInput = naturalRequest
        val transcriptPanel = txt("", 14f).apply {
            visibility = android.view.View.GONE
            maxHeight = (190 * resources.displayMetrics.density).toInt()
            background = panel(Color.WHITE)
            contentDescription = "Lịch sử hội thoại tạm thời với Chibi"
            setPadding(14, 12, 14, 12)
        }
        coachTranscriptView = transcriptPanel
        root.addView(transcriptPanel)
        val clearTranscript = Button(this).apply {
            text = "🧹 Xóa lời thoại tạm"
            isAllCaps = false
            visibility = android.view.View.GONE
            contentDescription = "Xóa văn bản hội thoại khỏi bộ nhớ màn hình; giữ nguyên lựa chọn và cảnh báo an toàn"
            setOnClickListener {
                coachTranscript.clear()
                coachSpeechMemory.clear()
                speechOutput?.stop()
                speechReplayButton?.isEnabled = false
                renderCoachTranscript()
            }
        }
        coachTranscriptClearButton = clearTranscript
        root.addView(clearTranscript)
        root.addView(Button(this).apply {
            text = "🎙 Nhập bằng giọng nói (dịch vụ nhận dạng trên máy)"
            isAllCaps = false
            setOnClickListener {
                // An external recognizer owns microphone access; this Activity never records audio.
                // Depending on the installed recognizer, speech may be processed online.
                AlertDialog.Builder(this@FitnessActivity)
                    .setTitle("Quyền riêng tư giọng nói")
                    .setMessage("Chibi sẽ mở dịch vụ nhận dạng giọng nói của thiết bị. Dịch vụ đó có thể yêu cầu quyền micro và có thể gửi âm thanh lên mạng tùy cài đặt. Chibi chỉ nhận văn bản trả về; hãy kiểm tra trước khi áp dụng giáo án.")
                    .setPositiveButton("Tiếp tục") { _, _ ->
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói yêu cầu tập luyện của bạn")
                        }
                        try { startActivityForResult(intent, speechRequestCode) }
                        catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(this@FitnessActivity, "Thiết bị chưa có dịch vụ nhận dạng giọng nói. Hãy nhập bằng bàn phím.", Toast.LENGTH_LONG).show()
                        }
                    }.setNegativeButton("Hủy", null).show()
            }
        })
        root.addView(Button(this).apply {
            text = "🔊 Bật giọng Chibi đọc câu hỏi"
            isAllCaps = false
            setOnClickListener {
                if (speechOutput == null) {
                    speechOutput = TextToSpeech(this@FitnessActivity) { status ->
                        speechReady = status == TextToSpeech.SUCCESS &&
                            speechOutput?.setLanguage(Locale("vi", "VN")) != TextToSpeech.LANG_MISSING_DATA &&
                            speechOutput?.language != null
                        speechStopButton?.isEnabled = speechReady
                        if (!speechReady) runOnUiThread {
                            Toast.makeText(this@FitnessActivity, "Chưa có giọng tiếng Việt TTS trên thiết bị", Toast.LENGTH_LONG).show()
                        }
                    }
                    text = "🔊 Đọc câu hỏi: bật (nhấn để tắt)"
                } else {
                    speechOutput?.stop(); speechOutput?.shutdown(); speechOutput = null
                    speechReady = false
                    speechStopButton?.isEnabled = false
                    text = "🔊 Bật giọng Chibi đọc câu hỏi"
                }
            }
        })
        val speechActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val replayButton = Button(this).apply {
            text = "🔁 Đọc lại lời Chibi"
            contentDescription = "Đọc lại câu trả lời gần nhất của Chibi; không bật microphone"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                val reply = coachSpeechMemory.replayText(
                    voiceEnabled = speechOutput != null,
                    engineReady = speechReady
                )
                if (reply == null) {
                    val message = if (!coachSpeechMemory.hasReply()) {
                        "Chibi chưa có câu trả lời để đọc lại."
                    } else {
                        "Hãy bật giọng Chibi và chờ engine tiếng Việt sẵn sàng."
                    }
                    Toast.makeText(this@FitnessActivity, message, Toast.LENGTH_LONG).show()
                } else {
                    speechOutput?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "fitness-coach-replay")
                }
            }
        }
        speechReplayButton = replayButton
        val stopButton = Button(this).apply {
            text = "■ Dừng đọc"
            contentDescription = "Dừng giọng đọc hiện tại; không thay đổi buổi tập"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { speechOutput?.stop() }
        }
        speechStopButton = stopButton
        speechActions.addView(replayButton, LinearLayout.LayoutParams(0, -2, 1f))
        speechActions.addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(speechActions)
        root.addView(txt("🤖 Chibi gợi ý buổi tập hôm nay",20f))
        val goals= arrayOf("Tăng sức mạnh","Tăng vận động tim mạch","Giãn cơ & linh hoạt","Giảm mỏi do ngồi nhiều","Mới bắt đầu","Thử thách mới")
        val energyLevels=arrayOf("Hôm nay hơi mệt","Cảm thấy bình thường","Rất nhiều năng lượng")
        val goalSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,goals)
            setSelection(prefs.getInt("recommendGoal",0).coerceIn(0,goals.lastIndex))
        }
        root.addView(goalSpinner)
        recommendationGoalSpinner = goalSpinner
        val energySpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,energyLevels)
            setSelection(prefs.getInt("recommendEnergy",1).coerceIn(0,2))
        }
        root.addView(energySpinner)
        recommendationEnergySpinner = energySpinner
        root.addView(txt("🏋️ Dụng cụ hiện có",19f))
        val equipmentOptions=arrayOf("Không có dụng cụ","Có thảm tập","Có tạ hoặc dây kháng lực","Có ghế vững chắc (không bánh xe)")
        val equipmentSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,equipmentOptions)
            setSelection(prefs.getInt("recommendEquipment",0).coerceIn(0,3))
        }
        root.addView(equipmentSpinner)
        recommendationEquipmentSpinner = equipmentSpinner
        equipmentSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it,categories[filter.selectedItemPosition]) }
                }
            }
        }
        root.addView(txt("➕ Dụng cụ bổ sung (có thể chọn nhiều)",16f))
        val savedAdditionalEquipment = prefs.getString("recommendAdditionalEquipment", "").orEmpty()
            .split(',')
            .mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
            .toSet()
        val initialAdditionalEquipment = conversation?.additionalEquipment ?: savedAdditionalEquipment
        val additionalEquipmentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        additionalEquipmentCheckBoxes.clear()
        listOf(
            TrainingEquipment.MAT to "Có thêm thảm tập",
            TrainingEquipment.DUMBBELL to "Có thêm tạ đơn",
            TrainingEquipment.RESISTANCE_BAND to "Có thêm dây kháng lực",
            TrainingEquipment.STABLE_CHAIR to "Có thêm ghế vững chắc, không bánh xe"
        ).forEach { (equipment, label) ->
            val checkBox = CheckBox(this).apply {
                text = label
                isChecked = equipment in initialAdditionalEquipment
                setOnCheckedChangeListener { _, _ ->
                    val selected = currentAdditionalEquipment()
                    val previous = conversation
                    conversation = if (previous == null) {
                        FitnessNaturalRequest.Result(reply = "Đã chọn dụng cụ bổ sung.", additionalEquipment = selected)
                    } else previous.copy(additionalEquipment = selected)
                    prefs.edit().putString(
                        "recommendAdditionalEquipment",
                        selected.sortedBy { it.ordinal }.joinToString(",") { it.name }
                    ).apply()
                    if (::filter.isInitialized) {
                        programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                    }
                }
            }
            additionalEquipmentCheckBoxes[equipment] = checkBox
            additionalEquipmentContainer.addView(checkBox)
        }
        root.addView(additionalEquipmentContainer)
        root.addView(txt("🦵 Mức độ vận động phù hợp",19f))
        val impactOptions=arrayOf("Không có hạn chế được báo","Ưu tiên bài ít tác động, không nhảy","Đang đau hoặc có chấn thương")
        val impactSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,impactOptions)
            setSelection(0) // Sensitive movement limitations are session-only
        }
        root.addView(impactSpinner)
        impactRestrictionSpinner = impactSpinner
        val wristLoadRestriction = CheckBox(this).apply {
            text = "Tránh động tác chống tay hoặc cầm tải qua cổ tay"
            contentDescription = "Chỉ áp dụng cho buổi tập hiện tại; không lưu thành hồ sơ sức khỏe"
            isChecked = conversation?.avoidWristLoad == true
            setOnCheckedChangeListener { _, checked ->
                val previous = conversation
                conversation = if (previous == null) {
                    if (checked) FitnessNaturalRequest.Result(
                        reply = "Đã chọn tránh chống tay và tải qua cổ tay.",
                        avoidWristLoad = true
                    ) else null
                } else previous.copy(avoidWristLoad = checked)
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                }
            }
        }
        wristLoadCheckBox = wristLoadRestriction
        root.addView(wristLoadRestriction)
        val floorPositionRestriction = CheckBox(this).apply {
            text = "Tránh bài phải nằm hoặc quỳ sàn"
            contentDescription = "Chỉ lọc động tác đã được xác định cần nằm hoặc quỳ sàn trong buổi hiện tại"
            isChecked = conversation?.avoidFloorPositions == true
            setOnCheckedChangeListener { _, checked ->
                val previous = conversation
                conversation = if (previous == null) {
                    if (checked) FitnessNaturalRequest.Result(
                        reply = "Đã chọn tránh động tác phải nằm hoặc quỳ sàn.",
                        avoidFloorPositions = true
                    ) else null
                } else previous.copy(avoidFloorPositions = checked)
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                }
            }
        }
        floorPositionCheckBox = floorPositionRestriction
        root.addView(floorPositionRestriction)
        val overheadReachRestriction = CheckBox(this).apply {
            text = "Tránh động tác đưa tay hoặc tải lên quá đầu"
            contentDescription = "Chỉ áp dụng cho buổi tập hiện tại; không lưu thành hồ sơ sức khỏe"
            isChecked = conversation?.avoidOverheadReach == true
            setOnCheckedChangeListener { _, checked ->
                val previous = conversation
                conversation = if (previous == null) {
                    FitnessNaturalRequest.Result(
                        reply = if (checked) "Đã chọn tránh động tác đưa tay quá đầu." else "Đã bỏ lựa chọn tránh đưa tay quá đầu.",
                        avoidOverheadReach = checked
                    )
                } else previous.copy(avoidOverheadReach = checked)
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                }
            }
        }
        overheadReachCheckBox = overheadReachRestriction
        root.addView(overheadReachRestriction)
        root.addView(txt("Các hạn chế này chỉ áp dụng cho buổi tập hiện tại, không lưu thành hồ sơ sức khỏe. Nếu đang đau, tê, sưng hoặc có triệu chứng bất thường, hãy dừng tập và xin tư vấn phù hợp.",12f))
        val historySummary = txt("",14f).apply { visibility = android.view.View.GONE }
        historySummaryView = historySummary
        root.addView(historySummary)
        val historySuggestion = txt("",14f).apply { visibility = android.view.View.GONE }
        historySuggestionView = historySuggestion
        root.addView(historySuggestion)
        val historyRecovery = Button(this).apply {
            text = "🌿 Xem giáo án phục hồi nhẹ"
            contentDescription = "Xem buổi vận động nhẹ dựa trên nhật ký 7 ngày; vẫn phải tuân thủ cảnh báo an toàn hiện tại"
            isAllCaps = false
            visibility = android.view.View.GONE
            setOnClickListener { startProgram(TrainingCatalog.byId("recovery")) }
        }
        historyRecoveryButton = historyRecovery
        root.addView(historyRecovery)
        val effortInsight = txt("", 14f).apply { visibility = android.view.View.GONE }
        historyEffortInsightView = effortInsight
        root.addView(effortInsight)
        val effortAction = Button(this).apply {
            text = "💬 Hỏi Chibi về buổi nhẹ hơn"
            isAllCaps = false
            visibility = android.view.View.GONE
            setOnClickListener {
                val suggestion = effortPlanSuggestion ?: return@setOnClickListener
                val input = coachRequestInput ?: return@setOnClickListener
                input.setText(suggestion.prompt)
                input.setSelection(input.text.length)
                input.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        historyEffortAction = effortAction
        root.addView(effortAction)
        // Session-only handoff: no health preferences are written to SharedPreferences.
        // Review all selections before asking for a recommendation.
        if (intent.hasExtra("coach_goal")) goalSpinner.setSelection(intent.getIntExtra("coach_goal",0).coerceIn(0,goals.lastIndex))
        if (intent.hasExtra("coach_energy")) energySpinner.setSelection(intent.getIntExtra("coach_energy",1).coerceIn(0,energyLevels.lastIndex))
        if (intent.hasExtra("coach_equipment")) equipmentSpinner.setSelection(intent.getIntExtra("coach_equipment",0).coerceIn(0,equipmentOptions.lastIndex))
        if (intent.hasExtra("coach_impact")) impactSpinner.setSelection(intent.getIntExtra("coach_impact",0).coerceIn(0,impactOptions.lastIndex))
        if (intent.hasExtra("coach_minutes")) {
            val requested=intent.getIntExtra("coach_minutes",20).coerceIn(5,120)
            length.setSelection(WorkoutDurationChoices.indexForMinutes(requested))
        }
        if (intent.hasExtra("coach_goal") || intent.hasExtra("coach_impact")) {
            root.addView(txt("🐰 Đã điền thông tin từ cuộc trò chuyện. Kiểm tra mục tiêu, thời lượng và hạn chế vận động trước khi chọn giáo án.",14f))
        }
        conversation?.let { restored ->
            restored.goal?.let { goalSpinner.setSelection(it) }
            restored.level?.let { level.setSelection(it) }
            restored.energy?.let { energySpinner.setSelection(it) }
            restored.minutes?.let { requested ->
                length.setSelection(WorkoutDurationChoices.indexForMinutes(requested))
            }
            restored.equipment?.let { equipmentSpinner.setSelection(it) }
            restored.impact?.let { impactSpinner.setSelection(it) }
            restored.avoidWristLoad?.let { wristLoadCheckBox?.isChecked = it }
            restored.avoidFloorPositions?.let { floorPositionCheckBox?.isChecked = it }
            restored.avoidOverheadReach?.let { overheadReachCheckBox?.isChecked = it }
            naturalRequest.hint = FitnessCoachFollowUp.nextQuestion(restored)
            root.addView(txt("🐰 Đã giữ lựa chọn trong phiên hiện tại sau khi xoay màn hình. Kiểm tra lại trước khi xem giáo án.",14f))
        }
        root.addView(Button(this).apply {
            text="💬 Chibi hiểu yêu cầu của tôi"
            setOnClickListener {
                val request=naturalRequest.text.toString()
                if (request.isBlank()) {
                    naturalRequest.error="Hãy mô tả buổi tập bạn muốn"
                } else {
                    val historyAnswer = WorkoutHistoryConversation.answer(
                        request,
                        WorkoutData.history(this@FitnessActivity)
                    )
                    if (historyAnswer != null) {
                        // Answer from structured local journal fields only; notes never enter chat or TTS.
                        coachTranscript.addUser(request)
                        coachTranscript.addChibi(historyAnswer)
                        renderCoachTranscript()
                        sayFollowUp(historyAnswer)
                        naturalRequest.text.clear()
                        AlertDialog.Builder(this@FitnessActivity)
                            .setTitle("📊 Nhật ký tập luyện")
                            .setMessage(historyAnswer)
                            .setPositiveButton("Đã hiểu", null)
                            .setNeutralButton("Hỏi tiếp") { _, _ ->
                                naturalRequest.requestFocus()
                                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                                    ?.showSoftInput(naturalRequest, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            }
                            .show()
                        return@setOnClickListener
                    }
                    val parsed=FitnessCoachConversation.merge(conversation, FitnessCoachConversation.parseReply(conversation, request))
                    conversation=parsed
                    val nextQuestion = FitnessCoachFollowUp.nextQuestion(parsed)
                    val coachMessage = parsed.reply + "\n\n🐰 Chibi hỏi tiếp: " + nextQuestion
                    coachTranscript.addUser(request)
                    coachTranscript.addChibi(coachMessage)
                    renderCoachTranscript()
                    parsed.goal?.let { goalSpinner.setSelection(it) }
                    parsed.level?.let { level.setSelection(it) }
                    parsed.energy?.let { energySpinner.setSelection(it) }
                    parsed.minutes?.let { requested ->
                        val closest=WorkoutDurationChoices.indexForMinutes(requested)
                        length.setSelection(closest)
                    }
                    parsed.equipment?.let { equipmentSpinner.setSelection(it) }
                    parsed.impact?.let { impactSpinner.setSelection(it) }
                    parsed.avoidWristLoad?.let { wristLoadCheckBox?.isChecked = it }
                    parsed.avoidFloorPositions?.let { floorPositionCheckBox?.isChecked = it }
                    parsed.avoidOverheadReach?.let { overheadReachCheckBox?.isChecked = it }
                    parsed.additionalEquipment?.let { setAdditionalEquipmentSelection(it) }
                    sayFollowUp(coachMessage)
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("🐰 Chibi đã nghe")
                        .setMessage(coachMessage)
                        .setPositiveButton("Xem giáo án",null)
                        .setNeutralButton("Trả lời tiếp") { _, _ ->
                            naturalRequest.text.clear()
                            naturalRequest.hint=FitnessCoachFollowUp.nextQuestion(parsed)
                            naturalRequest.requestFocus()
                            naturalRequest.setSelection(naturalRequest.text.length)
                            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                                ?.showSoftInput(naturalRequest, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        .setNegativeButton("🎙 Trả lời bằng giọng nói") { _, _ ->
                            naturalRequest.text.clear()
                            naturalRequest.hint = FitnessCoachFollowUp.nextQuestion(parsed)
                            // The external recognizer may use the network; disclose this again
                            // before every microphone handoff, including follow-up questions.
                            AlertDialog.Builder(this@FitnessActivity)
                                .setTitle("Quyền riêng tư giọng nói")
                                .setMessage("Dịch vụ nhận dạng trên thiết bị có thể yêu cầu micro và xử lý âm thanh trực tuyến. Chibi chỉ nhận văn bản; bạn có thể sửa trước khi gửi.")
                                .setPositiveButton("Tiếp tục") { _, _ ->
                                    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, FitnessCoachFollowUp.nextQuestion(parsed))
                                    }
                                    try { startActivityForResult(speechIntent, speechRequestCode) }
                                    catch (_: android.content.ActivityNotFoundException) {
                                        Toast.makeText(this@FitnessActivity, "Không có dịch vụ nhận dạng. Hãy nhập câu trả lời bằng bàn phím.", Toast.LENGTH_LONG).show()
                                        naturalRequest.requestFocus()
                                    }
                                }
                                .setNegativeButton("Hủy", null)
                                .show()
                        }.show()
                }
            }
        })
        root.addView(Button(this).apply {
            text="🐰 Gợi ý chương trình phù hợp"
            setOnClickListener {
                val goal=goalSpinner.selectedItemPosition
                val energy=energySpinner.selectedItemPosition
                val equipment=equipmentSpinner.selectedItemPosition
                val impact=effectiveImpactRestriction()
                prefs.edit().putInt("recommendGoal",goal).putInt("recommendEnergy",energy)
                    .putInt("recommendEquipment",equipment).apply()
                val recommendation=FitnessPlanAdvisor.recommend(FitnessPlanAdvisor.Preferences(
                    goal,energy,level.selectedItemPosition,mins[length.selectedItemPosition],equipment,impact,
                    currentAdditionalEquipment()))
                if (recommendation.requiresProfessionalReview) {
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("🐰 Tạm dừng kế hoạch tập")
                        .setMessage(recommendation.explanation + "\n\nChibi sẽ không đưa bạn vào buổi tập chỉ bằng một lần xác nhận bỏ qua cảnh báo.")
                        .setPositiveButton("Đã hiểu", null)
                        .setNegativeButton("Đóng", null)
                        .show()
                    return@setOnClickListener
                }
                val constraintProfile = currentWorkoutConstraintProfile()
                // Filter every candidate before history-based variety selection; a past
                // workout must never reintroduce a move requiring unavailable equipment.
                val additionalEquipment = currentAdditionalEquipment()
                val filteredPrograms = TrainingCatalog.programs.associate { candidate ->
                    val constrained = WorkoutConstraintFilter.apply(candidate, constraintProfile)
                    candidate.id to (constrained to WorkoutEquipmentFilter.apply(
                        constrained.program, equipment, additionalEquipment
                    ))
                }
                val baseProgram = TrainingCatalog.byId(recommendation.programId)
                val basePair = filteredPrograms[baseProgram.id]
                    ?: error("Recommended program is missing from the catalog")
                if (!basePair.second.canStart) {
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("Chưa có động tác phù hợp")
                        .setMessage("Chương trình này không còn động tác chính phù hợp với dụng cụ và bộ lọc hiện tại. Hãy chọn dụng cụ khác hoặc xem chương trình khác.")
                        .setPositiveButton("Đã hiểu", null).show()
                    return@setOnClickListener
                }
                val eligibleAlternatives = TrainingCatalog.programs.filter { candidate ->
                    candidate.id != baseProgram.id &&
                        (candidate.id != "advanced" || level.selectedItemPosition >= 2) &&
                        (candidate.id != "weighted" ||
                            ((equipment == 2 || additionalEquipment.any {
                                it == TrainingEquipment.DUMBBELL || it == TrainingEquipment.RESISTANCE_BAND
                            }) && level.selectedItemPosition >= 1)) &&
                        filteredPrograms[candidate.id]?.second?.canStart == true
                }.map { candidate -> filteredPrograms.getValue(candidate.id).second.program }
                val historySuggestion = WorkoutHistoryAdvisor.suggestVariety(
                    basePair.second.program,
                    WorkoutData.history(this@FitnessActivity),
                    eligibleAlternatives
                )
                val selectedProgram = historySuggestion?.program ?: basePair.second.program
                val selectedPair = filteredPrograms[selectedProgram.id] ?: basePair
                val p = selectedPair.second.program
                val constraintResult = selectedPair.first
                val equipmentResult = selectedPair.second
                if (!equipmentResult.canStart) {
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("Chưa có động tác phù hợp")
                        .setMessage("Không còn động tác chính phù hợp với dụng cụ đã chọn. Chibi chưa khởi chạy buổi tập.")
                        .setPositiveButton("Đã hiểu", null).show()
                    return@setOnClickListener
                }
                val planned=WorkoutDurationPlanner.build(
                    equipmentResult.program,
                    level.selectedItemPosition,
                    mins[length.selectedItemPosition],
                    currentExtensionPreferences(constraintProfile)
                )
                val durationSummary = buildString {
                    append("Thời lượng mục tiêu: ${planned.targetMinutes} phút • ")
                    append("ước tính ${planned.estimatedMinutes} phút.")
                    append("\nVận động: ${planned.activeSeconds / 60} phút • ")
                    append("nghỉ giữa hiệp: ${planned.prescribedRestSeconds / 60} phút • ")
                    append("chuyển bài dự kiến: ${planned.transitionSeconds / 60} phút.")
                    append("\nĐây là ước tính từ thư viện, không phải thời gian đo thực tế.")
                    if (planned.extensionMoves.isNotEmpty()) {
                        append("\nKhối bổ sung theo mục tiêu: ")
                        append(planned.extensionMoves.joinToString(" • ") { it.name })
                        append(". Chibi chỉ thêm động tác khác nhóm đã có và vẫn có thể để dư thời gian.")
                    }
                    if (planned.exceedsTarget) {
                        append("\nChuỗi bài tối thiểu đang dài hơn thời lượng bạn chọn; hãy chọn chương trình ngắn hơn.")
                    } else if (planned.unallocatedSeconds >= 60) {
                        val unallocatedMinutes = (planned.unallocatedSeconds + 59) / 60
                        append("\nCòn khoảng ${unallocatedMinutes} phút chưa được phân bổ. Chibi không tự nhân đôi toàn bộ hiệp để lấp thời gian; hãy chọn chương trình dài hơn hoặc bổ sung khối bài phù hợp.")
                    }
                    if (constraintResult.notice.isNotBlank()) append("\n\n").append(constraintResult.notice)
                    if (equipmentResult.notice.isNotBlank()) append("\n\n").append(equipmentResult.notice)
                }
                AlertDialog.Builder(this@FitnessActivity).setTitle("🐰 Chibi đề xuất: "+p.name)
                    .setMessage("Mục tiêu: "+goals[goal]+" • "+energyLevels[energy]+"\n"+
                        "Dụng cụ: "+equipmentOptions[equipment]+"\n"+
                        "Vận động: "+impactOptions[impact]+"\n\n"+
                        durationSummary+"\n\n"+
                        recommendation.explanation+
                        (historySuggestion?.let { "\n\n📚 " + it.reason } ?: "")+
                        "\n\nGợi ý offline theo quy tắc trên máy, không phải AI chẩn đoán hay tư vấn y khoa.")
                    .setPositiveButton("▶ Bắt đầu"){_,_->startProgram(p, constraintProfile)}
                    .setNeutralButton("📖 Xem từng bước") { _, _ ->
                        showProgramPreview(p, planned.moves, constraintProfile)
                    }
                    .setNegativeButton("Chọn bài khác",null).show()
            }
        })
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text="🥗 Dinh dưỡng"
            isAllCaps=false
            setOnClickListener { startActivity(Intent(this@FitnessActivity,NutritionActivity::class.java)) }
        },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply {
            text="📊 Nhật ký tập"
            isAllCaps=false
            setOnClickListener { startActivity(Intent(this@FitnessActivity,WorkoutActivity::class.java)) }
        },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        root.addView(txt("📚 Bộ môn & chương trình",23f))
        root.addView(txt("Chọn chương trình, xem từng động tác và dùng huấn luyện bằng giọng nói.",14f))
        filter=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,categories)
            setSelection(prefs.getInt("fitCategory",0).coerceIn(0,categories.lastIndex))
        }
        root.addView(filter)
        val container=LinearLayout(this).apply { orientation=1 }
        programContainer = container
        root.addView(container)
        filter.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                prefs.edit().putInt("fitCategory",position).apply()
                drawPrograms(container,categories[position])
            }
        }
        root.addView(txt("🛡 Hướng dẫn an toàn",20f))
        root.addView(txt("Mục tiêu sức khỏe chung cho người trưởng thành: tăng dần hoạt động vừa sức và tập sức mạnh từ 2 ngày/tuần. Dùng bài nhẹ khi mới bắt đầu. Dừng tập nếu đau ngực, khó thở khác thường, chóng mặt hoặc đau tăng dần. Người có bệnh nền/chấn thương/mang thai nên hỏi chuyên gia trước khi áp dụng.",14f))
        root.addView(Button(this).apply { text="📖 Nguồn WHO • CDC • NHS";setOnClickListener {
            AlertDialog.Builder(this@FitnessActivity).setTitle("📚 Nguồn tham khảo")
                .setMessage("WHO: who.int/publications/i/item/9789240015128\nCDC: cdc.gov/physical-activity-basics/adding-adults/what-counts.html\nNHS: nhs.uk/live-well/exercise/strength-exercises/\n\nĐây là giáo trình mẫu dựa trên nguyên tắc cơ bản, không phải chẩn đoán hay trị liệu.")
                .setPositiveButton("Đóng",null).show()
        } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    /**
     * Browse one exercise at a time with its animated pose cue, exact dose, rest and
     * numbered instructions. The illustration is informational and never assesses form.
     */
    private fun showProgramPreview(
        program: TrainingProgram,
        moves: List<TrainingMove>,
        constraints: WorkoutConstraintProfile
    ) {
        var preview = WorkoutLibraryPreview.start(moves) ?: run {
            Toast.makeText(this, "Chưa có động tác phù hợp để xem trước.", Toast.LENGTH_LONG).show()
            return
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 12, 20, 16)
        }
        val position = txt("", 14f)
        val heading = txt("", 21f)
        val pose = WorkoutPoseView(this).apply {
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            layoutParams = LinearLayout.LayoutParams(
                -1,
                (140 * resources.displayMetrics.density).toInt()
            )
        }
        val phaseCaption = txt("", 14f)
        pose.onPhaseChanged = { alternate ->
            phaseCaption.text = "↔ " + WorkoutPoseMotion.phaseCue(pose.pose, alternate)
        }
        val dose = txt("", 16f)
        val rest = txt("", 14f)
        val steps = txt("", 14f)
        val easier = txt("", 14f)
        val caution = txt("", 14f)
        val previous = Button(this).apply { text = "← Bài trước" }
        val next = Button(this).apply { text = "Bài tiếp →" }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
            addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val speak = Button(this).apply {
            text = "🔊 Nghe hướng dẫn"
            setOnClickListener {
                if (speechReady) {
                    sayFollowUp(WorkoutContextCoach.script(preview.currentMove))
                } else {
                    Toast.makeText(
                        this@FitnessActivity,
                        "Bật giọng Chibi trước để nghe hướng dẫn.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        content.addView(position)
        content.addView(heading)
        content.addView(pose)
        content.addView(phaseCaption)
        content.addView(txt(
            "Hình minh họa chuyển động, không phân tích kỹ thuật hoặc đánh giá tư thế thực tế.",
            12f
        ))
        content.addView(dose)
        content.addView(rest)
        content.addView(steps)
        content.addView(easier)
        content.addView(caution)
        content.addView(speak)
        content.addView(navigation)
        content.addView(txt(program.caution, 12f))

        fun renderPreview() {
            val move = preview.currentMove
            position.text = "Bài ${preview.position}/${preview.size}"
            heading.text = move.name
            pose.pose = WorkoutPoseGuide.classify(move.name)
            phaseCaption.text = "↔ " + WorkoutPoseMotion.phaseCue(pose.pose, pose.isInAlternatePose)
            dose.text = "🎯 Mục tiêu: ${move.dose}"
            rest.text = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
                ?: "⏱ Nghỉ thêm khi cần; động tác này không có cấu trúc hiệp lặp."
            steps.text = "📖 Hướng dẫn từng bước\n" +
                WorkoutStepGuide.split(move.steps).mapIndexed { index, step ->
                    "${index + 1}. $step"
                }.joinToString("\n")
            easier.text = "🌿 Dễ hơn: ${move.easier}"
            caution.text = "⚠️ Lưu ý: ${move.avoid}"
            previous.isEnabled = preview.canGoPrevious
            next.isEnabled = preview.canGoNext
        }

        previous.setOnClickListener {
            preview = preview.previous()
            renderPreview()
        }
        next.setOnClickListener {
            preview = preview.next()
            renderPreview()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("📚 ${program.name} • thư viện bài tập")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("▶ Bắt đầu", null)
            .setNegativeButton("Đóng", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                startProgram(program, constraints)
            }
        }
        dialog.setOnDismissListener {
            pose.motionEnabled = false
            speechOutput?.stop()
        }
        renderPreview()
        dialog.show()
    }

    private fun drawPrograms(container:LinearLayout,category:String) {
        container.removeAllViews()
        TrainingCatalog.programs.filter { category=="Tất cả" || it.category==category }.forEach { program ->
            val card=LinearLayout(this).apply {
                orientation=1;setPadding(17,14,17,14);background=panel(Color.WHITE)
                elevation=2f
            }
            card.addView(txt(program.name,19f))
            val constraints = currentWorkoutConstraintProfile()
            val constrained = WorkoutConstraintFilter.apply(program, constraints)
            val equipped = WorkoutEquipmentFilter.apply(
                constrained.program,
                recommendationEquipmentSpinner?.selectedItemPosition ?: 0,
                currentAdditionalEquipment()
            )
            val planned = WorkoutDurationPlanner.build(
                equipped.program,
                level.selectedItemPosition,
                mins[length.selectedItemPosition],
                currentExtensionPreferences(constraints)
            )
            card.addView(txt(program.subtitle+" • "+planned.moves.size+" động tác phù hợp • khoảng "+planned.estimatedMinutes+" phút",13f))
            if (constrained.notice.isNotBlank()) card.addView(txt(constrained.notice,12f))
            if (equipped.notice.isNotBlank()) card.addView(txt(equipped.notice,12f))
            if (!equipped.canStart) card.addView(txt("Không còn động tác chính phù hợp với dụng cụ đã chọn.",12f))
            val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply { text="📖 Xem bài";isAllCaps=false;isEnabled=equipped.canStart;setOnClickListener {
                showProgramPreview(equipped.program, planned.moves, constraints)
            } },LinearLayout.LayoutParams(0,-2,1f))
            buttons.addView(Button(this).apply { text="▶ Tập ngay";isAllCaps=false;isEnabled=equipped.canStart;setOnClickListener {
                startProgram(equipped.program, constraints)
            } },LinearLayout.LayoutParams(0,-2,1f))
            card.addView(buttons)
            card.addView(Button(this).apply { text="📆 Thêm lịch tuần";isAllCaps=false;setOnClickListener {
                addRoutine(program)
            } })
            container.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=15 })
        }
    }
    private fun currentAdditionalEquipment(): Set<TrainingEquipment> =
        additionalEquipmentCheckBoxes.filterValues { it.isChecked }.keys.toSet()

    private fun setAdditionalEquipmentSelection(selected: Set<TrainingEquipment>) {
        additionalEquipmentCheckBoxes.forEach { (equipment, checkBox) ->
            checkBox.isChecked = equipment in selected
        }
    }

    private fun currentWorkoutConstraintProfile(): WorkoutConstraintProfile =
        WorkoutConstraintProfile(
            avoidWristLoad = wristLoadCheckBox?.isChecked == true,
            avoidFloorPositions = floorPositionCheckBox?.isChecked == true,
            avoidOverheadReach = overheadReachCheckBox?.isChecked == true
        )

    private fun currentExtensionPreferences(
        constraints: WorkoutConstraintProfile = currentWorkoutConstraintProfile()
    ): WorkoutExtensionPreferences = WorkoutExtensionPreferences(
        goal = recommendationGoalSpinner?.selectedItemPosition ?: 0,
        energy = recommendationEnergySpinner?.selectedItemPosition ?: 1,
        equipment = recommendationEquipmentSpinner?.selectedItemPosition ?: 0,
        impact = effectiveImpactRestriction(),
        constraints = constraints,
        additionalEquipment = currentAdditionalEquipment()
    )

    private fun startProgram(
        p: TrainingProgram,
        constraints: WorkoutConstraintProfile = currentWorkoutConstraintProfile()
    ) {
        if (effectiveImpactRestriction() == 2) {
            AlertDialog.Builder(this)
                .setTitle("🐰 Tạm dừng buổi tập")
                .setMessage("Bạn đã báo đang đau hoặc có chấn thương. Chibi không khởi chạy bài tập từ thư viện khi chưa được chuyên gia y tế đánh giá phù hợp.")
                .setPositiveButton("Đã hiểu", null)
                .setNegativeButton("Đóng", null)
                .show()
            return
        }
        val equipmentSelection = recommendationEquipmentSpinner?.selectedItemPosition ?: 0
        val constrained = WorkoutConstraintFilter.apply(p, constraints)
        val additionalEquipment = currentAdditionalEquipment()
        val equipped = WorkoutEquipmentFilter.apply(constrained.program, equipmentSelection, additionalEquipment)
        if (!equipped.canStart) {
            AlertDialog.Builder(this)
                .setTitle("Chưa có động tác phù hợp")
                .setMessage(equipped.notice.ifBlank { "Không còn động tác chính phù hợp với dụng cụ đã chọn." })
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }
        val l=level.selectedItemPosition
        val m=mins[length.selectedItemPosition]
        prefs.edit()
            .putInt("fitLevel", l)
            // Keep the old index key readable while preserving the exact custom duration.
            .putInt("fitTime", WorkoutDurationChoices.legacyIndexForMinutes(m))
            .putInt("fitTimeMinutes", m)
            .apply()
        startActivity(Intent(this,GuidedWorkoutActivity::class.java)
            .putExtra("program",p.id).putExtra("level",l).putExtra("minutes",m)
            .putExtra("avoid_wrist_load", constraints.avoidWristLoad)
            .putExtra("avoid_floor_positions", constraints.avoidFloorPositions)
            .putExtra("avoid_overhead_reach", constraints.avoidOverheadReach)
            .putExtra("extension_goal", recommendationGoalSpinner?.selectedItemPosition ?: 0)
            .putExtra("extension_energy", recommendationEnergySpinner?.selectedItemPosition ?: 1)
            .putExtra("extension_equipment", equipmentSelection)
            .putStringArrayListExtra("extension_additional_equipment", ArrayList(additionalEquipment.map { it.name }))
            .putExtra("extension_impact", effectiveImpactRestriction()))
    }
    private fun addRoutine(p:TrainingProgram) {
        if (!WorkoutEquipmentFilter.apply(
                p,
                recommendationEquipmentSpinner?.selectedItemPosition ?: 0,
                currentAdditionalEquipment()
            ).canStart) {
            Toast.makeText(this, "Chưa có động tác phù hợp với dụng cụ đã chọn; chưa tạo lịch tuần.", Toast.LENGTH_LONG).show()
            return
        }
        if (currentWorkoutConstraintProfile().avoidWristLoad ||
            currentWorkoutConstraintProfile().avoidFloorPositions ||
            currentWorkoutConstraintProfile().avoidOverheadReach) {
            Toast.makeText(this, "Các hạn chế vận động chỉ áp dụng cho buổi hiện tại; lịch tuần chưa lưu bộ lọc này.", Toast.LENGTH_LONG).show()
            return
        }
        if (effectiveImpactRestriction() == 2) {
            AlertDialog.Builder(this)
                .setTitle("🐰 Chưa thể lên lịch tập")
                .setMessage("Bạn đã báo đang đau hoặc có chấn thương. Chibi không tạo lịch tập định kỳ cho đến khi chuyên gia y tế đánh giá hoạt động phù hợp.")
                .setPositiveButton("Đã hiểu", null)
                .setNegativeButton("Đóng", null)
                .show()
            return
        }
        val current=LifeData.routines(this)
        val trainingDays=if(level.selectedItemPosition==0) ((1 shl 2) or (1 shl 5))
            else ((1 shl 1) or (1 shl 4) or (1 shl 6))
        val r=Routine(LifeData.nextId(),p.name,"🏃 Sức khỏe",18,0,
            mins[length.selectedItemPosition],trainingDays)
        AlertDialog.Builder(this).setTitle("📅 Thêm lịch định kỳ?")
            .setMessage("18:00 • "+(if(level.selectedItemPosition==0) "T3 và T6" else "T2, T5 và T7")+
                "\n${p.name}\nCó thể chỉnh giờ và ngày trong Lịch trình.")
            .setPositiveButton("Thêm lịch"){_,_->
                LifeData.save(this,current+r)
                Toast.makeText(this,"Đã thêm lịch tập.",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Hủy",null).show()
    }
}
