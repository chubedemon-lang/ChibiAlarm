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
    private var coachQuickRepliesContainer: LinearLayout? = null
    private var speechInput: EditText? = null
    private var speechOutput: TextToSpeech? = null
    private var speechReady = false
    // The most recent coach reply stays in Activity memory only; it is never persisted.
    private val coachSpeechMemory = FitnessCoachSpeechMemory()
    private var speechReplayButton: Button? = null
    private var speechStopButton: Button? = null
    private val speechRequestCode = 4107
    private val libraryStepSpeechRequestCode = 4108
    // Exists only while the exercise-library preview dialog is visible.
    private var libraryStepVoiceCommandHandler: ((WorkoutLibraryVoiceNavigation.Action) -> Unit)? = null
    private var speechSequenceId = 0L
    private var recommendationGoalSpinner: Spinner? = null
    private var recommendationEnergySpinner: Spinner? = null
    private var recommendationEquipmentSpinner: Spinner? = null
    private var recommendationAction: Button? = null
    private val additionalEquipmentCheckBoxes = linkedMapOf<TrainingEquipment, CheckBox>()
    private var kneelingCheckBox: CheckBox? = null

    private fun applySpeechCandidate(candidate: CoachSpeechTranscript.Candidate) {
        val input = speechInput ?: return
        input.setText(candidate.text)
        input.setSelection(input.text.length)
        val message = if (candidate.truncated) {
            "Bản nhận dạng vượt giới hạn ${CoachSpeechTranscript.MAX_CHARS} ký tự và đã được rút gọn. Hãy kiểm tra, sửa văn bản rồi mới gửi."
        } else {
            "Kiểm tra hoặc sửa văn bản nhận dạng rồi nhấn Chibi hiểu yêu cầu. Chibi chưa gửi nội dung này."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun applyLibraryStepSpeechCandidate(candidate: CoachSpeechTranscript.Candidate) {
        if (candidate.truncated) {
            Toast.makeText(
                this,
                "Câu lệnh quá dài. Hãy nói ngắn như “bước tiếp theo” hoặc “đọc lại”.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val action = WorkoutLibraryVoiceNavigation.action(candidate.text)
        if (action == WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED) {
            Toast.makeText(this, "Chibi chưa hiểu lệnh điều khiển bước. Hãy thử nói ngắn hơn.", Toast.LENGTH_LONG).show()
            return
        }
        val handler = libraryStepVoiceCommandHandler
        if (handler == null) {
            Toast.makeText(this, "Hướng dẫn bài tập đã đóng. Hãy mở lại thư viện để dùng giọng nói.", Toast.LENGTH_LONG).show()
            return
        }
        handler(action)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == libraryStepSpeechRequestCode) {
            if (resultCode != RESULT_OK) return
            val candidates = CoachSpeechTranscript.candidates(
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            )
            when {
                candidates.isEmpty() -> Toast.makeText(
                    this,
                    "Không nhận được lệnh rõ ràng. Bạn có thể dùng các nút bước trước/bước tiếp.",
                    Toast.LENGTH_LONG
                ).show()
                candidates.size == 1 -> applyLibraryStepSpeechCandidate(candidates.single())
                else -> AlertDialog.Builder(this)
                    .setTitle("Chọn lệnh Chibi nghe được")
                    .setItems(candidates.mapIndexed { index, candidate ->
                        "${index + 1}. ${candidate.label}"
                    }.toTypedArray()) { _, which ->
                        candidates.getOrNull(which)?.let(::applyLibraryStepSpeechCandidate)
                    }
                    .setNegativeButton("Để tôi chọn bằng nút", null)
                    .show()
            }
            return
        }
        if (requestCode != speechRequestCode || resultCode != RESULT_OK) return

        val candidates = CoachSpeechTranscript.candidates(
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        )
        when {
            candidates.isEmpty() -> Toast.makeText(
                this,
                "Không nhận được câu nói rõ ràng. Bạn có thể gõ thay thế.",
                Toast.LENGTH_LONG
            ).show()
            candidates.size == 1 -> applySpeechCandidate(candidates.single())
            else -> AlertDialog.Builder(this)
                .setTitle("Chọn nội dung Chibi nghe được")
                .setItems(candidates.mapIndexed { index, candidate ->
                    "${index + 1}. ${candidate.label}"
                }.toTypedArray()) { _, which ->
                    candidates.getOrNull(which)?.let(::applySpeechCandidate)
                }
                .setNegativeButton("Để tôi gõ", null)
                .show()
        }
    }

    override fun onDestroy() {
        // Drop transient raw text on finish and configuration recreation.
        coachTranscript.clear()
        coachSpeechMemory.clear()
        libraryStepVoiceCommandHandler = null
        speechReplayButton = null
        speechStopButton = null
        coachTranscriptView = null
        coachTranscriptClearButton = null
        coachQuickRepliesContainer = null
        coachRequestInput = null
        recommendationAction = null
        kneelingCheckBox = null
        historyEffortInsightView = null
        historyEffortAction = null
        effortPlanSuggestion = null
        historySteadyProgressView = null
        historySteadyProgressAction = null
        steadyProgressSuggestion = null
        speechOutput?.stop()
        speechOutput?.shutdown()
        speechOutput = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshWorkoutHistoryInsight()
    }

    /** Queue every bounded chunk so the coach never silently drops the end of a long answer. */
    private fun speakCoachReply(text: String, utterancePrefix: String) {
        val engine = speechOutput ?: return
        val sequence = ++speechSequenceId
        WorkoutSpeechSequence.plan(text).forEachIndexed { index, utterance ->
            engine.speak(
                utterance.text,
                if (utterance.flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "$utterancePrefix-$sequence-$index"
            )
        }
    }

    @JvmOverloads
    private fun sayFollowUp(
        message: String,
        followUp: FitnessNaturalRequest.Result? = null
    ) {
        val spokenMessage = followUp?.let { FitnessCoachFollowUp.spokenPrompt(message, it) } ?: message
        coachSpeechMemory.remember(spokenMessage)
        speechReplayButton?.isEnabled = coachSpeechMemory.hasReply()
        speechStopButton?.isEnabled = speechReady
        val speechText = coachSpeechMemory.replayText(
            voiceEnabled = speechOutput != null,
            engineReady = speechReady
        ) ?: return
        speakCoachReply(speechText, "fitness-follow-up")
    }


    private fun renderCoachTranscript() {
        val panel = coachTranscriptView ?: return
        val rendered = coachTranscript.render()
        panel.text = rendered
        panel.visibility = if (rendered.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        coachTranscriptClearButton?.visibility =
            if (rendered.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }

    /**
     * Render contextual answer chips for the one question Chibi is currently asking.
     * Tapping a chip only fills the editable field; it never sends or applies a plan.
     */
    private fun renderCoachQuickReplies() {
        val container = coachQuickRepliesContainer ?: return
        container.removeAllViews()
        val replies = conversation?.let(FitnessCoachFollowUp::quickReplies).orEmpty()
        if (replies.isEmpty()) {
            container.visibility = android.view.View.GONE
            return
        }

        container.visibility = android.view.View.VISIBLE
        container.addView(txt("Gợi ý trả lời nhanh • chạm để điền, chưa gửi", 13f))
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            contentDescription = "Các câu trả lời gợi ý cho câu hỏi hiện tại của Chibi"
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val spacing = (6 * resources.displayMetrics.density).toInt()
        replies.forEachIndexed { index, reply ->
            val chip = Button(this).apply {
                text = reply.label
                isAllCaps = false
                contentDescription = "Điền câu trả lời ${reply.label}; chưa gửi cho Chibi"
                setOnClickListener {
                    val input = speechInput ?: return@setOnClickListener
                    input.setText(reply.utterance)
                    input.setSelection(input.text.length)
                    input.requestFocus()
                    Toast.makeText(
                        this@FitnessActivity,
                        "Đã điền câu trả lời. Kiểm tra rồi nhấn Chibi hiểu yêu cầu.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.setMargins(spacing, 0, 0, 0)
            row.addView(chip, params)
        }
        scroller.addView(row)
        container.addView(scroller)
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
    private var librarySearchInput: EditText? = null
    private var historySummaryView: TextView? = null
    private var historySuggestionView: TextView? = null
    private var historyRecoveryButton: Button? = null
    private var historyEffortInsightView: TextView? = null
    private var historyEffortAction: Button? = null
    private var coachRequestInput: EditText? = null
    private var effortPlanSuggestion: WorkoutEffortPlanAdvisor.Suggestion? = null
    private var historySteadyProgressView: TextView? = null
    private var historySteadyProgressAction: Button? = null
    private var steadyProgressSuggestion: WorkoutSteadyProgressAdvisor.Suggestion? = null
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
            "coach_avoid_overhead_reach", "coach_avoid_kneeling", "coach_additional_equipment"
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
            avoidKneeling = booleanValue("coach_avoid_kneeling"),
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
            ?: FitnessCoachRestorableProfile.fromBundle(savedInstanceState)?.restore()
        render()
    }

    /**
     * Android may recreate this Activity after reclaiming the process. Restore structured workout
     * choices and session safety switches only; raw chat, recognized speech, and coach replies
     * are deliberately excluded from saved state.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        FitnessCoachRestorableProfile.capture(
            goal = recommendationGoalSpinner?.selectedItemPosition,
            level = if (::level.isInitialized) level.selectedItemPosition else conversation?.level,
            energy = recommendationEnergySpinner?.selectedItemPosition,
            minutes = if (::length.isInitialized) {
                WorkoutDurationChoices.minutes.getOrNull(length.selectedItemPosition)
            } else conversation?.minutes,
            equipment = recommendationEquipmentSpinner?.selectedItemPosition,
            impact = impactRestrictionSpinner?.let { effectiveImpactRestriction() } ?: conversation?.impact,
            avoidWristLoad = wristLoadCheckBox?.isChecked ?: conversation?.avoidWristLoad,
            avoidFloorPositions = floorPositionCheckBox?.isChecked ?: conversation?.avoidFloorPositions,
            avoidOverheadReach = overheadReachCheckBox?.isChecked ?: conversation?.avoidOverheadReach,
            avoidKneeling = kneelingCheckBox?.isChecked ?: conversation?.avoidKneeling,
            additionalEquipment = if (additionalEquipmentCheckBoxes.isNotEmpty()) {
                currentAdditionalEquipment()
            } else conversation?.additionalEquipment ?: emptySet()
        )?.writeTo(outState)
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
            selectedAvoidKneeling = if (kneelingCheckBox?.isChecked == true || conversation?.avoidKneeling != null)
                kneelingCheckBox?.isChecked else null,
            selectedAdditionalEquipment = currentAdditionalEquipment().takeIf {
                it.isNotEmpty() || conversation?.additionalEquipment != null
            }
        )

    private fun effectiveImpactRestriction(): Int =
        FitnessCoachSessionSnapshot.effectiveImpact(
            conversation?.impact, impactRestrictionSpinner?.selectedItemPosition ?: 0
        )

    /** Reads coarse totals and structured effort labels; free-text journal notes never enter the advisor. */
    /** Reads only bounded totals and structured self-reported effort labels; journal notes never enter the advisor. */
    private fun refreshWorkoutHistoryInsight() {
        val workouts = WorkoutData.history(this)
        val summary = WorkoutHistoryAdvisor.summarize(workouts)
        // A repeated HARD/VERY_HARD pattern must occur in two valid sessions inside this same
        // seven-day window before it can offer a lighter-session choice.
        val effortSuggestion = WorkoutEffortPlanAdvisor.suggest(workouts, lookbackDays = 7)
        effortPlanSuggestion = effortSuggestion
        val latestEffortSummary = WorkoutEffortHistoryConversation.latestSummary(workouts)
        val effortInsightText = listOfNotNull(latestEffortSummary, effortSuggestion?.message)
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }
        val steadySuggestion = WorkoutSteadyProgressAdvisor.suggest(workouts)
        steadyProgressSuggestion = steadySuggestion
        historySteadyProgressView?.apply {
            visibility = if (steadySuggestion != null) android.view.View.VISIBLE else android.view.View.GONE
            text = steadySuggestion?.message.orEmpty()
        }
        historySteadyProgressAction?.visibility =
            if (steadySuggestion != null) android.view.View.VISIBLE else android.view.View.GONE
        historyEffortInsightView?.apply {
            visibility = if (effortInsightText != null) android.view.View.VISIBLE else android.view.View.GONE
            text = effortInsightText.orEmpty()
        }
        historyEffortAction?.apply {
            visibility = if (effortInsightText != null) android.view.View.VISIBLE else android.view.View.GONE
            text = if (effortSuggestion != null) "💬 Hỏi Chibi về buổi nhẹ hơn"
                else "💬 Hỏi Chibi về check-in gần nhất"
        }
        historySummaryView?.apply {
            visibility = if (summary.sessions > 0) android.view.View.VISIBLE else android.view.View.GONE
            text = "📊 Nhật ký 7 ngày: ${summary.sessions} buổi • ${summary.activeDays} ngày vận động • ${summary.totalMinutes} phút (ghi trên máy)."
        }
        val lighterMessage = WorkoutHistoryAdvisor.lighterSessionMessage(summary, effortSuggestion)
        historySuggestionView?.apply {
            visibility = if (lighterMessage != null) android.view.View.VISIBLE else android.view.View.GONE
            text = lighterMessage.orEmpty()
        }
        historyRecoveryButton?.apply {
            visibility = if (lighterMessage != null) android.view.View.VISIBLE else android.view.View.GONE
            text = if (effortSuggestion != null) "🌿 Xem giáo án nhẹ hơn từ check-in"
                else "🌿 Xem giáo án phục hồi nhẹ"
            contentDescription = "Xem buổi vận động nhẹ theo nhật ký và các bộ lọc an toàn hiện tại"
        }
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
            setSelection((conversation?.level ?: prefs.getInt("fitLevel",0)).coerceIn(0,2))
        }
        root.addView(level)
        root.addView(txt("⏱ Thời lượng bạn muốn",19f))
        length=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,lengths)
            val savedMinutes = conversation?.minutes ?: if (prefs.contains("fitTimeMinutes")) {
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
        val quickReplies = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        coachQuickRepliesContainer = quickReplies
        root.addView(quickReplies)
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
                    .setMessage("Chibi yêu cầu dịch vụ ưu tiên nhận dạng ngoại tuyến và trả tối đa 5 phương án nếu dịch vụ hỗ trợ. Đây chỉ là gợi ý: nhà cung cấp có thể bỏ qua và xử lý âm thanh trực tuyến. Dịch vụ có thể yêu cầu quyền micro. Chibi chỉ nhận văn bản; hãy kiểm tra hoặc sửa trước khi áp dụng giáo án.")
                    .setPositiveButton("Tiếp tục") { _, _ ->
                        val intent = WorkoutSpeechRecognitionRequest.create("Nói yêu cầu tập luyện của bạn")
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
                    speakCoachReply(reply, "fitness-coach-replay")
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
            setSelection((conversation?.goal ?: prefs.getInt("recommendGoal",0)).coerceIn(0,goals.lastIndex))
        }
        root.addView(goalSpinner)
        recommendationGoalSpinner = goalSpinner
        val energySpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,energyLevels)
            setSelection((conversation?.energy ?: prefs.getInt("recommendEnergy",1)).coerceIn(0,2))
        }
        root.addView(energySpinner)
        recommendationEnergySpinner = energySpinner
        root.addView(txt("🏋️ Dụng cụ hiện có",19f))
        val equipmentOptions=arrayOf(
            "Không có dụng cụ",
            "Có thảm tập",
            "Có tạ hoặc dây kháng lực",
            "Có ghế vững chắc (không bánh xe)",
            "Có tạ đơn (không gồm dây)",
            "Có dây kháng lực (không gồm tạ)"
        )
        val equipmentSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,equipmentOptions)
            setSelection((conversation?.equipment ?: prefs.getInt("recommendEquipment",0)).coerceIn(0,equipmentOptions.lastIndex))
        }
        root.addView(equipmentSpinner)
        recommendationEquipmentSpinner = equipmentSpinner
        equipmentSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                persistNonSensitiveProfile()
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it,categories[filter.selectedItemPosition]) }
                }
            }
        }
        watchProfileSelection(level)
        watchProfileSelection(length)
        watchProfileSelection(goalSpinner)
        watchProfileSelection(energySpinner)
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
                    persistNonSensitiveProfile()
                    if (::filter.isInitialized) {
                        programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                    }
                }
            }
            additionalEquipmentCheckBoxes[equipment] = checkBox
            additionalEquipmentContainer.addView(checkBox)
        }
        root.addView(additionalEquipmentContainer)
        persistNonSensitiveProfile()
        root.addView(txt("🦵 Mức độ vận động phù hợp",19f))
        val impactOptions=arrayOf("Không có hạn chế được báo","Ưu tiên bài ít tác động, không nhảy","Đang đau hoặc có chấn thương")
        val impactSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,impactOptions)
            setSelection(conversation?.impact ?: 0) // Restored only from this Activity's transient state
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
                renderCoachQuickReplies()
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
                renderCoachQuickReplies()
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                }
            }
        }
        floorPositionCheckBox = floorPositionRestriction
        root.addView(floorPositionRestriction)
        val kneelingRestriction = CheckBox(this).apply {
            text = "Tránh động tác phải quỳ gối (vẫn có thể nằm ngửa)"
            contentDescription = "Chỉ lọc các động tác có điểm tựa trên hai gối; không đồng nghĩa với tránh mọi tư thế nằm sàn"
            isChecked = conversation?.avoidKneeling == true
            setOnCheckedChangeListener { _, checked ->
                val previous = conversation
                conversation = if (previous == null) {
                    FitnessNaturalRequest.Result(
                        reply = if (checked) "Đã chọn tránh động tác phải quỳ gối." else "Đã bỏ lựa chọn tránh quỳ gối.",
                        avoidKneeling = checked
                    )
                } else previous.copy(avoidKneeling = checked)
                renderCoachQuickReplies()
                if (::filter.isInitialized) {
                    programContainer?.let { drawPrograms(it, categories[filter.selectedItemPosition]) }
                }
            }
        }
        kneelingCheckBox = kneelingRestriction
        root.addView(kneelingRestriction)
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
                renderCoachQuickReplies()
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
                val prompt = effortPlanSuggestion?.prompt
                    ?: "Mức độ gắng sức buổi gần nhất thế nào?"
                val input = coachRequestInput ?: return@setOnClickListener
                input.setText(prompt)
                input.setSelection(input.text.length)
                input.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        historyEffortAction = effortAction
        root.addView(effortAction)
        val steadyProgressInsight = txt("", 14f).apply {
            visibility = android.view.View.GONE
            background = panel(Color.WHITE)
            setPadding(14, 12, 14, 12)
            contentDescription = "Gợi ý tiến triển dựa trên ba buổi tự đánh giá nhẹ hoặc vừa sức"
        }
        historySteadyProgressView = steadyProgressInsight
        root.addView(steadyProgressInsight)
        val steadyProgressAction = Button(this).apply {
            text = "🌱 Xem bước tiến triển có kiểm soát"
            isAllCaps = false
            visibility = android.view.View.GONE
            contentDescription = "Mở bản xem trước mục tiêu mỗi hiệp cho buổi kế tiếp; không tự thay đổi giáo án"
            setOnClickListener { showSteadyProgressionPreview() }
        }
        historySteadyProgressAction = steadyProgressAction
        root.addView(steadyProgressAction)
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
            restored.avoidKneeling?.let { kneelingCheckBox?.isChecked = it }
            naturalRequest.hint = FitnessCoachFollowUp.nextQuestion(restored)
            root.addView(txt("🐰 Đã giữ lựa chọn trong phiên hiện tại sau khi xoay màn hình. Kiểm tra lại trước khi xem giáo án.",14f))
        }
        renderCoachQuickReplies()
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
                        return@setOnClickListener
                    }
                    val adjustmentAnswer = WorkoutPlanAdjustmentConversation.answer(
                        input = request,
                        profile = currentWorkoutConstraintProfile(),
                        equipmentSelection = recommendationEquipmentSpinner?.selectedItemPosition ?: 0,
                        additionalEquipment = currentAdditionalEquipment(),
                        level = level.selectedItemPosition,
                        goal = goalSpinner.selectedItemPosition,
                        impact = effectiveImpactRestriction()
                    )
                    if (adjustmentAnswer != null) {
                        // Preview only: chat suggestions never mutate the selected or active workout.
                        coachTranscript.addUser(request)
                        coachTranscript.addChibi(adjustmentAnswer)
                        renderCoachTranscript()
                        sayFollowUp(adjustmentAnswer)
                        naturalRequest.text.clear()
                        return@setOnClickListener
                    }
                    val exerciseAnswer = WorkoutExerciseConversation.answer(request)
                    if (exerciseAnswer != null) {
                        // Answer only from the curated catalog; no journal notes or raw audio are read.
                        coachTranscript.addUser(request)
                        coachTranscript.addChibi(exerciseAnswer)
                        renderCoachTranscript()
                        sayFollowUp(exerciseAnswer)
                        naturalRequest.text.clear()
                        return@setOnClickListener
                    }
                    val parsed=FitnessCoachConversation.merge(conversation, FitnessCoachConversation.parseReply(conversation, request))
                    conversation=parsed
                    renderCoachQuickReplies()
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
                    parsed.avoidKneeling?.let { kneelingCheckBox?.isChecked = it }
                    parsed.additionalEquipment?.let { setAdditionalEquipmentSelection(it) }
                    sayFollowUp(coachMessage, parsed)
                    val planReady =
                        FitnessCoachFollowUp.nextQuestionType(parsed) == FitnessCoachFollowUp.Question.COMPLETE &&
                            parsed.impact != 2
                    val coachDialog = AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("🐰 Chibi đã nghe")
                        .setMessage(coachMessage)
                        .setPositiveButton(if (planReady) "Xem giáo án" else "Chưa đủ thông tin", null)
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
                                .setMessage("Chibi yêu cầu ưu tiên nhận dạng ngoại tuyến và tối đa 5 phương án nếu dịch vụ hỗ trợ; nhà cung cấp có thể bỏ qua và xử lý âm thanh trực tuyến. Chibi chỉ nhận văn bản; bạn có thể sửa trước khi gửi.")
                                .setPositiveButton("Tiếp tục") { _, _ ->
                                    val speechIntent = WorkoutSpeechRecognitionRequest.create(
                                        FitnessCoachFollowUp.nextQuestion(parsed)
                                    )
                                    try { startActivityForResult(speechIntent, speechRequestCode) }
                                    catch (_: android.content.ActivityNotFoundException) {
                                        Toast.makeText(this@FitnessActivity, "Không có dịch vụ nhận dạng. Hãy nhập câu trả lời bằng bàn phím.", Toast.LENGTH_LONG).show()
                                        naturalRequest.requestFocus()
                                    }
                                }
                                .setNegativeButton("Hủy", null)
                                .show()
                        }
                        .create()
                    coachDialog.setOnShowListener {
                        val previewButton = coachDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        previewButton.isEnabled = planReady
                        if (planReady) {
                            previewButton.setOnClickListener {
                                coachDialog.dismiss()
                                val action = recommendationAction
                                if (action == null) {
                                    Toast.makeText(this@FitnessActivity,
                                        "Chưa mở được bộ tạo giáo án. Hãy thử lại từ màn hình Fitness.",
                                        Toast.LENGTH_LONG).show()
                                } else {
                                    action.performClick()
                                }
                            }
                        }
                    }
                    coachDialog.show()
                }
            }
        })
        val recommendationButton = Button(this).apply {
            text="🐰 Gợi ý chương trình phù hợp"
            setOnClickListener {
                val goal=goalSpinner.selectedItemPosition
                val energy=energySpinner.selectedItemPosition
                val equipment=equipmentSpinner.selectedItemPosition
                val impact=effectiveImpactRestriction()
                persistNonSensitiveProfile()
                val planPreferences = FitnessPlanAdvisor.Preferences(
                    goal, energy, level.selectedItemPosition, mins[length.selectedItemPosition], equipment, impact,
                    currentAdditionalEquipment()
                )
                val recommendation = FitnessPlanAdvisor.recommend(planPreferences)
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
                val additionalEquipment = planPreferences.additionalEquipment
                val filteredPrograms = TrainingCatalog.programs.associate { candidate ->
                    val constrained = WorkoutConstraintFilter.apply(candidate, constraintProfile)
                    candidate.id to (constrained to WorkoutEquipmentFilter.apply(
                        constrained.program, equipment, additionalEquipment
                    ))
                }
                val baseProgram = TrainingCatalog.byId(recommendation.programId)
                val targetMinutes = planPreferences.minutes
                val hasResistanceEquipment = equipment in setOf(2, 4, 5) ||
                    additionalEquipment.any {
                        it == TrainingEquipment.DUMBBELL || it == TrainingEquipment.RESISTANCE_BAND
                    }
                val eligibleCandidates = TrainingCatalog.programs.filter { candidate ->
                    (candidate.id != "advanced" || level.selectedItemPosition >= 2) &&
                        (candidate.id != "weighted" ||
                            (hasResistanceEquipment && level.selectedItemPosition >= 1)) &&
                        filteredPrograms[candidate.id]?.second?.canStart == true
                }.map { candidate -> filteredPrograms.getValue(candidate.id).second.program }
                val selection = WorkoutPlanCandidateSelector.select(
                    preferred = baseProgram,
                    candidates = eligibleCandidates,
                    preferences = planPreferences,
                    constraints = constraintProfile
                )
                if (selection == null) {
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("Chưa có giáo án phù hợp")
                        .setMessage(WorkoutPlanCandidateSelector.noSelectionNotice(planPreferences))
                        .setPositiveButton("Đã hiểu", null)
                        .show()
                    return@setOnClickListener
                }
                val basePair = filteredPrograms[selection.program.id]
                    ?: error("Selected compatible program is missing from the catalog")
                val eligibleAlternatives = eligibleCandidates.filter {
                    it.id != basePair.second.program.id
                }
                val workoutHistory = WorkoutData.history(this@FitnessActivity)
                val historySuggestion = if (selection.usedFallback || targetMinutes <= 10) null else {
                    WorkoutHistoryAdvisor.suggestVariety(
                        basePair.second.program,
                        workoutHistory,
                        eligibleAlternatives
                    )
                }
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
                    targetMinutes,
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
                val profileSummary = FitnessPlanProfileSummary.format(
                    goalLabel = goals[goal],
                    energyLabel = energyLevels[energy],
                    levelLabel = levels[level.selectedItemPosition],
                    minutes = targetMinutes,
                    equipmentSelection = equipment,
                    additionalEquipment = additionalEquipment,
                    impact = impact,
                    constraints = constraintProfile
                ) + selection.notice.takeIf { it.isNotBlank() }?.let { "\n\n" + it }.orEmpty()
                val muscleFocusSummary = WorkoutMusclePlanSummary.format(planned.moves)
                val spokenMuscleFocusSummary = WorkoutMusclePlanSummary.spoken(planned.moves)
                val effortReviewWarnings = WorkoutEffortHistoryConversation.planReview(
                    planned.moves,
                    workoutHistory
                )
                val effortReviewSummary = if (effortReviewWarnings.isEmpty()) "" else {
                    "\n\n🧭 Check-in trước đây cần xem lại:\n" +
                        effortReviewWarnings.joinToString("\n") +
                        "\nCác nhãn là cảm nhận do bạn tự chọn; Chibi không tự xóa, đổi bài hay thay đổi liều lượng."
                }
                val recommendationMessage = profileSummary + "\n\n" + durationSummary +
                    (muscleFocusSummary?.let { "\n\n" + it } ?: "") + "\n\n" +
                    recommendation.explanation +
                    (historySuggestion?.let { "\n\n📚 " + it.reason } ?: "") +
                    effortReviewSummary +
                    "\n\nGợi ý offline theo quy tắc trên máy, không phải AI chẩn đoán hay tư vấn y khoa."
                val spokenProfileSummary = FitnessPlanSpeechSummary.withEffortReview(
                    summary = FitnessPlanSpeechSummary.fromProfile(
                        profileSummary = profileSummary,
                        programName = p.name,
                        estimatedMinutes = planned.estimatedMinutes,
                        exceedsTarget = planned.exceedsTarget,
                        prescribedRestSeconds = planned.prescribedRestSeconds,
                        muscleFocusSummary = spokenMuscleFocusSummary
                    ),
                    warnings = effortReviewWarnings
                )
                val dialogContent = LinearLayout(this@FitnessActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val summaryText = TextView(this@FitnessActivity).apply {
                        text = recommendationMessage
                        textSize = 14f
                        val inset = (16 * resources.displayMetrics.density).toInt()
                        setPadding(inset, inset, inset, inset)
                    }
                    addView(summaryText)
                    addView(Button(this@FitnessActivity).apply {
                        text = "🔊 Nghe tóm tắt hồ sơ"
                        isAllCaps = false
                        contentDescription = if (effortReviewWarnings.isEmpty()) {
                            "Đọc thành tiếng mục tiêu, trình độ, thời lượng, dụng cụ và bộ lọc vận động; không bắt đầu buổi tập"
                        } else {
                            "Đọc thành tiếng hồ sơ kế hoạch và lưu ý từ check-in trước đây; không bắt đầu buổi tập"
                        }
                        setOnClickListener {
                            if (speechOutput == null || !speechReady) {
                                Toast.makeText(
                                    this@FitnessActivity,
                                    "Hãy bật giọng Chibi ở phần hội thoại phía trên rồi nhấn nghe lại.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                sayFollowUp(spokenProfileSummary)
                            }
                        }
                    })
                }
                val dialogScroll = ScrollView(this@FitnessActivity).apply {
                    isFillViewport = true
                    addView(dialogContent)
                }
                AlertDialog.Builder(this@FitnessActivity).setTitle("🐰 Chibi đề xuất: "+p.name)
                    .setView(dialogScroll)
                    .setPositiveButton("▶ Bắt đầu"){_,_->startProgram(p, constraintProfile)}
                    .setNeutralButton("📖 Xem từng bước") { _, _ ->
                        showProgramPreview(p, planned, constraintProfile, equipment, additionalEquipment)
                    }
                    .setNegativeButton("Chọn bài khác",null).show()
            }
        }
        recommendationAction = recommendationButton
        root.addView(recommendationButton)
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
        val librarySearch = EditText(this).apply {
            hint = "Tìm squat, plank, chống đẩy, giãn cơ…"
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            filters = arrayOf(android.text.InputFilter.LengthFilter(WorkoutLibrarySearch.MAX_QUERY_CHARS))
            contentDescription = "Tìm chương trình và động tác trong thư viện bài tập offline"
        }
        librarySearchInput = librarySearch
        librarySearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val selectedCategory = if (::filter.isInitialized) {
                    categories.getOrElse(filter.selectedItemPosition) { "Tất cả" }
                } else {
                    "Tất cả"
                }
                programContainer?.let { drawPrograms(it, selectedCategory, s?.toString().orEmpty()) }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        root.addView(librarySearch)
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
        planned: WorkoutDurationPlanner.Plan,
        constraints: WorkoutConstraintProfile,
        equipmentSelection: Int,
        additionalEquipment: Set<TrainingEquipment>,
        initialMoveIndex: Int = 0
    ) {
        var planDraft = WorkoutPlanPreviewEditor.start(
            moves = planned.moves,
            targetMinutes = planned.targetMinutes,
            level = level.selectedItemPosition,
            constraints = constraints,
            equipmentSelection = equipmentSelection,
            additionalEquipment = additionalEquipment
        ) ?: run {
            Toast.makeText(this, "Giáo án không còn khớp dụng cụ hoặc hạn chế vận động. Hãy tạo bản xem trước mới.", Toast.LENGTH_LONG).show()
            return
        }
        var preview = WorkoutLibraryPreview.start(
            planDraft.moves,
            initialMoveIndex.coerceIn(0, planDraft.moves.lastIndex.coerceAtLeast(0))
        ) ?: run {
            Toast.makeText(this, "Chưa có động tác phù hợp để xem trước.", Toast.LENGTH_LONG).show()
            return
        }
        var stepPreview = WorkoutLibraryStepPreview.start(preview.currentMove)
        // Library narration is opt-in and scoped to this preview dialog only.
        var autoSpeakOnStepChange = false
        var lastRenderedStepPosition: Int? = null

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
        val equipment = txt("", 14f)
        val muscleFocus = txt("", 14f)
        val selectionRationale = txt("", 14f).apply {
            tag = "fitness-library-selection-rationale"
            contentDescription = "Giải thích dựa trên nhóm chương trình, dụng cụ, bộ lọc và nhãn thư viện; không phải đánh giá tư thế"
        }
        val rest = txt("", 14f)
        val stepPosition = txt("", 13f)
        val stepInstruction = txt("", 16f)
        val stepPrevious = Button(this).apply {
            text = "← Bước trước"
            isAllCaps = false
            contentDescription = "Đọc bước hướng dẫn trước"
        }
        val stepNext = Button(this).apply {
            text = "Bước tiếp →"
            isAllCaps = false
            contentDescription = "Đọc bước hướng dẫn tiếp theo"
        }
        val stepNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(stepPrevious, LinearLayout.LayoutParams(0, -2, 1f))
            addView(stepNext, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val stepVoiceCommand = Button(this).apply {
            text = "🎙 Nói lệnh điều khiển bước"
            isAllCaps = false
            contentDescription = "Chạm để mở nhận dạng giọng nói cho hướng dẫn; Chibi không nghe nền"
            setOnClickListener {
                AlertDialog.Builder(this@FitnessActivity)
                    .setTitle("Quyền riêng tư giọng nói")
                    .setMessage(
                        "Chỉ mở dịch vụ nhận dạng sau khi bạn chọn Tiếp tục. Chibi không nghe nền và không tự ghi âm. " +
                            "Chibi chỉ nhận văn bản; dịch vụ nhận dạng bên ngoài có thể xử lý âm thanh trực tuyến dù đã yêu cầu ưu tiên ngoại tuyến. " +
                            "Dịch vụ có thể yêu cầu quyền microphone theo hệ thống. Bạn có thể hủy bất cứ lúc nào."
                    )
                    .setPositiveButton("Tiếp tục") { _, _ ->
                        speechOutput?.stop()
                        val request = WorkoutSpeechRecognitionRequest.create(
                            "Nói lệnh: bước tiếp theo, bước trước, đọc lại hoặc đọc lưu ý an toàn"
                        )
                        try {
                            startActivityForResult(request, libraryStepSpeechRequestCode)
                        } catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(
                                this@FitnessActivity,
                                "Thiết bị chưa có dịch vụ nhận dạng giọng nói. Hãy dùng các nút điều hướng.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }
        val stepAutoSpeak = CheckBox(this).apply {
            tag = "fitness-library-step-auto-speak"
            text = if (speechReady && speechOutput != null) {
                "🔊 Tự đọc bước khi chuyển"
            } else {
                "🔊 Tự đọc bước khi chuyển (bật giọng Chibi trước)"
            }
            isChecked = false
            isEnabled = speechReady && speechOutput != null
            contentDescription = if (isEnabled) {
                "Bật hoặc tắt đọc tự động khi chuyển bước; chỉ dùng TTS, không mở microphone"
            } else {
                "Đọc tự động cần bật giọng Chibi ở màn hình Fitness trước; không mở microphone"
            }
            setOnCheckedChangeListener { _, checked ->
                autoSpeakOnStepChange = checked && speechReady && speechOutput != null
                if (!autoSpeakOnStepChange) speechOutput?.stop()
            }
        }
        val stepSpeak = Button(this).apply {
            text = "🔊 Nghe bước này"
            isAllCaps = false
            contentDescription = "Đọc thành tiếng bước hướng dẫn đang chọn"
            setOnClickListener {
                if (speechReady) {
                    sayFollowUp(stepPreview.spokenCue())
                } else {
                    Toast.makeText(
                        this@FitnessActivity,
                        "Bật giọng Chibi trước để nghe bước hướng dẫn.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        val easier = txt("", 14f)
        val caution = txt("", 14f)
        val durationSummary = txt("", 13f)
        val previous = Button(this).apply { text = "← Bài trước" }
        val next = Button(this).apply { text = "Bài tiếp →" }
        val substitute = Button(this).apply {
            text = "🔁 Thay động tác đang xem"
            isAllCaps = false
        }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
            addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val speak = Button(this).apply {
            text = "🔊 Nghe toàn bộ hướng dẫn"
            isAllCaps = false
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
        content.addView(durationSummary)
        content.addView(heading)
        content.addView(pose)
        content.addView(phaseCaption)
        content.addView(txt(
            "Hình minh họa chuyển động, không phân tích kỹ thuật hoặc đánh giá tư thế thực tế.",
            12f
        ))
        content.addView(dose)
        content.addView(equipment)
        content.addView(muscleFocus)
        content.addView(selectionRationale)
        content.addView(rest)
        content.addView(stepPosition)
        content.addView(stepInstruction)
        content.addView(stepNavigation)
        content.addView(stepVoiceCommand)
        content.addView(stepAutoSpeak)
        content.addView(stepSpeak)
        content.addView(easier)
        content.addView(caution)
        content.addView(speak)
        content.addView(substitute)
        content.addView(navigation)
        content.addView(txt(program.caution, 12f))
        content.addView(txt(
            "Bài thay thế chỉ được đưa vào bản xem trước sau khi chọn. Chibi không sửa nhật ký cũ, không tự bắt đầu và không tự cộng EXP.",
            12f
        ))

        fun renderStepPreview() {
            val previousPosition = lastRenderedStepPosition
            val selected = stepPreview
            stepPosition.text = "📖 Bước ${selected.position}/${selected.size}"
            stepInstruction.text = selected.currentStep
            stepInstruction.contentDescription =
                "Bước ${selected.position} trên ${selected.size}: ${selected.currentStep}"
            stepPrevious.isEnabled = selected.canGoPrevious
            stepNext.isEnabled = selected.canGoNext

            // Pin the original vector illustration to the phase named by this exact
            // instruction. Ambiguous setup/hold steps stay neutral rather than guessing.
            val phase = selected.illustrationPhase()
            pose.showGuideFrame(phase ?: false)
            phaseCaption.text = if (phase == null) {
                "↔ Tư thế minh họa chung cho bước này"
            } else {
                "↔ " + WorkoutPoseMotion.phaseCue(pose.pose, phase)
            }
            val shouldSpeak = WorkoutStepGuide.shouldSpeakOnNavigation(
                enabled = autoSpeakOnStepChange && speechReady && speechOutput != null,
                previousPosition = previousPosition,
                nextPosition = selected.position
            )
            lastRenderedStepPosition = selected.position
            if (shouldSpeak) {
                speakCoachReply(selected.spokenCue(), "fitness-library-step")
            }
        }

        fun renderPreview() {
            val move = preview.currentMove
            position.text = "Bài ${preview.position}/${preview.size}"
            val durationText = "⏱ Ước tính mới: ${WorkoutPlanPreviewEditor.durationLabel(planDraft.estimatedSeconds)} • mục tiêu: ${planDraft.targetMinutes} phút"
            durationSummary.text = if (planDraft.exceedsTarget) {
                "$durationText\n⚠️ Bản xem trước dài hơn mục tiêu; Chibi không tự cắt hiệp bắt buộc."
            } else durationText
            substitute.isEnabled = WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index).isNotEmpty()
            substitute.text = if (substitute.isEnabled) "🔁 Thay động tác đang xem" else "Không có biến thể đã duyệt phù hợp"
            heading.text = move.name
            pose.pose = WorkoutPoseGuide.classify(move.name)
            // A new exercise starts at step one without treating the exercise change as step navigation.
            lastRenderedStepPosition = null
            stepPreview = WorkoutLibraryStepPreview.start(move)
            renderStepPreview()
            dose.text = "🎯 Mục tiêu: ${move.dose}"
            equipment.text = "🧰 ${WorkoutContextCoach.equipmentCue(move)}"
            muscleFocus.text = "💪 ${WorkoutContextCoach.muscleCue(move)}"
            val rationaleEquipment = TrainingEquipmentAvailability.fromPreference(planDraft.equipmentSelection) +
                planDraft.additionalEquipment
            selectionRationale.text = WorkoutMoveSelectionExplanation.explain(
                program = program,
                move = move,
                position = preview.position,
                total = preview.size,
                constraints = planDraft.constraints,
                availableEquipment = rationaleEquipment
            ) ?: "Chibi chưa thể xác nhận lý do phù hợp cho động tác này. Hãy tạo lại bản xem trước để kiểm tra dụng cụ và hạn chế vận động."
            selectionRationale.contentDescription = selectionRationale.text.toString()
            rest.text = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
                ?: "⏱ Nghỉ thêm khi cần; động tác này không có cấu trúc hiệp lặp."
            easier.text = "🌿 Dễ hơn: ${move.easier}"
            caution.text = "⚠️ Lưu ý: ${move.avoid}"
            previous.isEnabled = preview.canGoPrevious
            next.isEnabled = preview.canGoNext
        }

        stepPrevious.setOnClickListener {
            stepPreview = stepPreview.previous()
            renderStepPreview()
        }
        stepNext.setOnClickListener {
            stepPreview = stepPreview.next()
            renderStepPreview()
        }
        previous.setOnClickListener {
            preview = preview.previous()
            renderPreview()
        }
        next.setOnClickListener {
            preview = preview.next()
            renderPreview()
        }
        val applyAlternativeToPreview: (Int, List<TrainingMove>) -> Unit = { selected, options ->
            val chosen = options.getOrNull(selected)
            when {
                options.isEmpty() -> Toast.makeText(
                    this@FitnessActivity,
                    "Chưa có biến thể phù hợp với dụng cụ và hạn chế hiện tại.",
                    Toast.LENGTH_LONG
                ).show()
                chosen == null -> Toast.makeText(
                    this@FitnessActivity,
                    "Chibi chỉ tìm thấy ${options.size} phương án. Hãy chọn số từ 1 đến ${options.size}.",
                    Toast.LENGTH_LONG
                ).show()
                else -> {
                    val updated = WorkoutPlanPreviewEditor.replace(planDraft, preview.index, chosen)
                    if (updated == null) {
                        Toast.makeText(this@FitnessActivity, "Phương án đã thay đổi hoặc không còn phù hợp. Hãy xem lại giáo án.", Toast.LENGTH_LONG).show()
                    } else {
                        val updatedPreview = WorkoutLibraryPreview.start(updated.moves, preview.index)
                        if (updatedPreview == null) {
                            Toast.makeText(this@FitnessActivity, "Không thể mở lại bản xem trước. Giáo án hiện tại được giữ nguyên.", Toast.LENGTH_LONG).show()
                        } else {
                            planDraft = updated
                            preview = updatedPreview
                            stepPreview = WorkoutLibraryStepPreview.start(preview.currentMove)
                            renderPreview()
                            sayFollowUp(
                                "Đã thêm ${chosen.name} vào bản xem trước. Thời lượng ước tính ${WorkoutPlanPreviewEditor.durationLabel(planDraft.estimatedSeconds)}. Chưa bắt đầu buổi tập."
                            )
                        }
                    }
                }
            }
        }

        substitute.setOnClickListener {
            val options = WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index)
            if (options.isEmpty()) {
                Toast.makeText(this@FitnessActivity, "Chưa có biến thể phù hợp với dụng cụ và hạn chế hiện tại.", Toast.LENGTH_LONG).show()
            } else {
                // The same curated options shown in the picker are read aloud when
                // Vietnamese TTS is already enabled; no microphone or network is opened.
                if (speechReady && speechOutput != null) {
                    WorkoutAlternativeSpeech.summary(options)?.let(::sayFollowUp)
                }
                val labels = options.map { "${it.name} • ${it.dose}" }.toTypedArray()
                AlertDialog.Builder(this@FitnessActivity)
                    .setTitle("Chọn bài thay thế đã duyệt")
                    .setItems(labels) { _, selected ->
                        applyAlternativeToPreview(selected, options)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("📚 ${program.name} • thư viện bài tập")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("▶ Bắt đầu", null)
            .setNegativeButton("Đóng", null)
            .create()
        libraryStepVoiceCommandHandler = { action ->
            val move = preview.currentMove
            when (action) {
                WorkoutLibraryVoiceNavigation.Action.FIRST -> {
                    stepPreview = stepPreview.first()
                    renderStepPreview()
                }
                WorkoutLibraryVoiceNavigation.Action.NEXT -> {
                    if (stepPreview.canGoNext) {
                        stepPreview = stepPreview.next()
                        renderStepPreview()
                    } else Toast.makeText(this@FitnessActivity, "Đang ở bước cuối.", Toast.LENGTH_SHORT).show()
                }
                WorkoutLibraryVoiceNavigation.Action.PREVIOUS -> {
                    if (stepPreview.canGoPrevious) {
                        stepPreview = stepPreview.previous()
                        renderStepPreview()
                    } else Toast.makeText(this@FitnessActivity, "Đang ở bước đầu.", Toast.LENGTH_SHORT).show()
                }
                WorkoutLibraryVoiceNavigation.Action.REPEAT -> {
                    if (speechReady) sayFollowUp(stepPreview.spokenCue())
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi trước để đọc bước.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.AUTO_SPEAK_ON -> {
                    if (stepAutoSpeak.isEnabled) stepAutoSpeak.isChecked = true
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi ở màn hình Fitness trước.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.AUTO_SPEAK_OFF -> {
                    stepAutoSpeak.isChecked = false
                    speechOutput?.stop()
                }
                WorkoutLibraryVoiceNavigation.Action.SPEAK_SUMMARY -> {
                    val restCue = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
                        ?: "Nghỉ thêm khi cần; động tác này không có cấu trúc hiệp lặp."
                    if (speechReady) sayFollowUp("${move.name}. Mục tiêu: ${move.dose}. $restCue")
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi trước để đọc mục tiêu.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.SPEAK_EASIER -> {
                    if (speechReady) sayFollowUp(WorkoutStepGuide.spokenEasierCue(move.name, move.easier))
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi trước để đọc phương án nhẹ hơn.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.SPEAK_SAFETY_NOTE -> {
                    if (speechReady) sayFollowUp(WorkoutStepGuide.spokenSafetyNoteCue(move.name, move.avoid))
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi trước để đọc lưu ý.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.SPEAK_EXERCISE -> {
                    if (speechReady) sayFollowUp(WorkoutContextCoach.script(move))
                    else Toast.makeText(this@FitnessActivity, "Bật giọng Chibi trước để nghe hướng dẫn.", Toast.LENGTH_LONG).show()
                }
                WorkoutLibraryVoiceNavigation.Action.OPEN_ALTERNATIVES -> substitute.performClick()
                WorkoutLibraryVoiceNavigation.Action.SELECT_ALTERNATIVE_1 ->
                    applyAlternativeToPreview(0, WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index))
                WorkoutLibraryVoiceNavigation.Action.SELECT_ALTERNATIVE_2 ->
                    applyAlternativeToPreview(1, WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index))
                WorkoutLibraryVoiceNavigation.Action.SELECT_ALTERNATIVE_3 ->
                    applyAlternativeToPreview(2, WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index))
                WorkoutLibraryVoiceNavigation.Action.SELECT_ALTERNATIVE_4 ->
                    applyAlternativeToPreview(3, WorkoutPlanPreviewEditor.alternatives(planDraft, preview.index))
                WorkoutLibraryVoiceNavigation.Action.CLOSE -> dialog.dismiss()
                WorkoutLibraryVoiceNavigation.Action.SAFETY -> {
                    speechOutput?.stop()
                    Toast.makeText(
                        this@FitnessActivity,
                        "Đã nhận diện dấu hiệu khó chịu. Nếu đang vận động, hãy dừng lại; không cố tập tiếp theo hướng dẫn này.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED -> {
                    Toast.makeText(this@FitnessActivity, "Lệnh này không áp dụng trong thư viện bài tập.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!WorkoutPlanPreviewEditor.isValid(planDraft)) {
                    Toast.makeText(this@FitnessActivity, "Bản xem trước đã cũ hoặc không còn hợp lệ. Hãy mở lại trước khi bắt đầu.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                startProgram(program, constraints, previewDraft = planDraft)
            }
        }
        dialog.setOnDismissListener {
            libraryStepVoiceCommandHandler = null
            pose.motionEnabled = false
            speechOutput?.stop()
        }
        renderPreview()
        dialog.show()
    }

    private fun drawPrograms(
        container: LinearLayout,
        category: String,
        query: String = librarySearchInput?.text?.toString().orEmpty()
    ) {
        container.removeAllViews()
        val matches = WorkoutLibrarySearch.filter(TrainingCatalog.programs, category, query)
        if (query.isNotBlank()) {
            container.addView(txt("Tìm thấy ${matches.size} chương trình phù hợp", 12f))
        }
        if (matches.isEmpty()) {
            container.addView(txt(
                if (query.isBlank()) "Danh mục này chưa có chương trình."
                else "Chưa tìm thấy chương trình hoặc động tác phù hợp. Thử từ khóa khác hoặc chọn “Tất cả”.",
                14f
            ))
            return
        }
        matches.forEach { match ->
            val program = match.program
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
            val matchedNames = match.matchingMoves.map { it.name }.toSet()
            val visibleMatches = planned.moves.filter { it.name in matchedNames }
            val previewIndex = WorkoutLibrarySearch.previewIndex(match, planned.moves)
            card.addView(txt(program.subtitle+" • "+planned.moves.size+" động tác phù hợp • khoảng "+planned.estimatedMinutes+" phút",13f))
            if (query.isNotBlank() && match.matchingMoves.isNotEmpty()) {
                if (visibleMatches.isNotEmpty()) {
                    val labels = visibleMatches.take(3).joinToString(" • ") { "${it.name} (${it.dose})" }
                    val more = if (visibleMatches.size > 3) " • +${visibleMatches.size - 3} động tác" else ""
                    card.addView(txt("Động tác khớp trong giáo án hiện tại: $labels$more",12f))
                } else {
                    card.addView(txt("Động tác khớp từ khóa hiện không còn trong giáo án sau khi áp dụng thời lượng, dụng cụ và hạn chế.",12f))
                }
            }
            if (constrained.notice.isNotBlank()) card.addView(txt(constrained.notice,12f))
            if (equipped.notice.isNotBlank()) card.addView(txt(equipped.notice,12f))
            if (!equipped.canStart) card.addView(txt("Không còn động tác chính phù hợp với dụng cụ đã chọn.",12f))
            val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply {
                text = if (query.isNotBlank() && visibleMatches.isNotEmpty()) "📖 Xem bài khớp" else "📖 Xem bài"
                isAllCaps = false
                isEnabled = equipped.canStart
                setOnClickListener {
                    showProgramPreview(
                        equipped.program,
                        planned,
                        constraints,
                        recommendationEquipmentSpinner?.selectedItemPosition ?: 0,
                        currentAdditionalEquipment(),
                        initialMoveIndex = previewIndex
                    )
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
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
    /** Persist only non-sensitive workout preferences in the existing device-local guest store. */
    private fun persistNonSensitiveProfile() {
        if (!::level.isInitialized || !::length.isInitialized) return
        val goal = recommendationGoalSpinner?.selectedItemPosition ?: return
        val energy = recommendationEnergySpinner?.selectedItemPosition ?: return
        val equipment = recommendationEquipmentSpinner?.selectedItemPosition ?: return
        val minutes = WorkoutDurationChoices.minutes.getOrNull(length.selectedItemPosition) ?: return
        val extras = currentAdditionalEquipment()
        val editor = prefs.edit()
            .putInt("recommendGoal", goal)
            .putInt("recommendEnergy", energy)
            .putInt("recommendEquipment", equipment)
            .putInt("fitLevel", level.selectedItemPosition)
            .putInt("fitTime", WorkoutDurationChoices.legacyIndexForMinutes(minutes))
            .putInt("fitTimeMinutes", minutes)
        // The spinner listeners can fire before the additional-equipment controls exist.
        // Do not erase a previously saved tool selection during that first render pass.
        if (additionalEquipmentCheckBoxes.isNotEmpty()) {
            editor.putString(
                "recommendAdditionalEquipment",
                extras.sortedBy { it.ordinal }.joinToString(",") { it.name }
            )
        }
        editor.apply()
    }

    private fun watchProfileSelection(spinner: Spinner) {
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                persistNonSensitiveProfile()
            }
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
        WorkoutConstraintProfile.fromSessionSelections(
            impact = effectiveImpactRestriction(),
            avoidWristLoad = wristLoadCheckBox?.isChecked == true,
            avoidFloorPositions = floorPositionCheckBox?.isChecked == true,
            avoidOverheadReach = overheadReachCheckBox?.isChecked == true,
            avoidKneeling = kneelingCheckBox?.isChecked == true
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

    private data class ProgressionCandidate(
        val program: TrainingProgram,
        val constraints: WorkoutConstraintProfile,
        val proposal: WorkoutProgressionPreview.Proposal
    )

    private fun prefillSteadyProgressPrompt(suggestion: WorkoutSteadyProgressAdvisor.Suggestion) {
        val input = coachRequestInput ?: return
        input.setText(suggestion.prompt)
        input.setSelection(input.text.length)
        input.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showSteadyProgressionPreview() {
        val suggestion = steadyProgressSuggestion ?: return
        if (effectiveImpactRestriction() == 2) {
            AlertDialog.Builder(this)
                .setTitle("🐰 Tạm dừng bước tiến triển")
                .setMessage("Bạn đã báo đang đau hoặc có chấn thương. Chibi không đề xuất tăng mục tiêu tập trong tình trạng này.")
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }

        val candidates = buildSteadyProgressionCandidates(suggestion)
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Chưa đủ dữ liệu cho bản xem trước")
                .setMessage(
                    "Chibi đã thấy ba ngày check-in nhẹ hoặc vừa sức, nhưng chưa có đủ số lần hoặc thời lượng tự khai báo " +
                        "cho một bài trong giáo án hiện tại. Hãy hoàn thành Guided Workout và ghi giá trị từng hiệp. " +
                        "Chibi sẽ không tự đoán số lần hoặc tăng mục tiêu khi thiếu dữ liệu."
                )
                .setPositiveButton("Hỏi Chibi") { _, _ -> prefillSteadyProgressPrompt(suggestion) }
                .setNegativeButton("Đóng", null)
                .show()
            return
        }

        if (candidates.size == 1) {
            showProgressionCandidate(candidates.single())
            return
        }

        val labels = candidates.map { candidate ->
            "${candidate.program.name} • ${candidate.proposal.targetValue} ${candidate.proposal.unit}/hiệp"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chọn chương trình cho buổi kế tiếp")
            .setItems(labels) { _, which ->
                candidates.getOrNull(which)?.let { showProgressionCandidate(it) }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun buildSteadyProgressionCandidates(
        suggestion: WorkoutSteadyProgressAdvisor.Suggestion
    ): List<ProgressionCandidate> {
        val history = WorkoutData.history(this)
        val constraints = currentWorkoutConstraintProfile()
        val equipment = recommendationEquipmentSpinner?.selectedItemPosition ?: 0
        val additionalEquipment = currentAdditionalEquipment()
        val selectedLevel = level.selectedItemPosition
        val selectedMinutes = mins[length.selectedItemPosition]
        val extension = currentExtensionPreferences(constraints)

        return TrainingCatalog.programs.mapNotNull { original ->
            val constrained = WorkoutConstraintFilter.apply(original, constraints)
            val equipped = WorkoutEquipmentFilter.apply(
                constrained.program, equipment, additionalEquipment
            )
            if (!equipped.canStart) return@mapNotNull null
            val planned = WorkoutDurationPlanner.build(
                equipped.program, selectedLevel, selectedMinutes, extension
            )
            val proposal = WorkoutProgressionPreview.propose(
                suggestion, history, planned.moves
            ) ?: return@mapNotNull null
            ProgressionCandidate(original, constraints, proposal)
        }
    }

    private fun showProgressionCandidate(candidate: ProgressionCandidate) {
        val proposal = candidate.proposal
        val message = buildString {
            append(candidate.program.name)
            append("\n\n")
            append(proposal.exerciseName)
            append("\nBuổi gần nhất: ${proposal.previousValue} ${proposal.unit}/hiệp ở tất cả hiệp đã ghi.")
            append("\nBuổi kế tiếp: ${proposal.targetValue} ${proposal.unit}/hiệp (+${proposal.targetValue - proposal.previousValue}), trong dải ${proposal.prescribedMinReps}–${proposal.prescribedMaxReps} ${proposal.unit}.")
            append("\nGiữ ${proposal.requiredSets} hiệp tối thiểu (tối đa ${proposal.maximumSets}), nghỉ ${proposal.restSeconds} giây.")
            append("\nKhông tăng tải, không thêm hiệp và không đổi dụng cụ.")
            append("\n\nĐây là mục tiêu thử từ số lần và cảm nhận tự khai báo, không đo kỹ thuật hoặc khả năng hồi phục. Nếu thấy khó chịu, hãy giảm hoặc dừng.")
        }
        AlertDialog.Builder(this)
            .setTitle("🌱 Xem trước bước tiến triển")
            .setMessage(message)
            .setPositiveButton("Dùng cho buổi tới") { _, _ ->
                startProgram(candidate.program, candidate.constraints, proposal)
            }
            .setNeutralButton("Hỏi Chibi") { _, _ ->
                steadyProgressSuggestion?.let(::prefillSteadyProgressPrompt)
            }
            .setNegativeButton("Giữ nguyên", null)
            .show()
    }

    private fun startProgram(
        p: TrainingProgram,
        constraints: WorkoutConstraintProfile = currentWorkoutConstraintProfile(),
        progression: WorkoutProgressionPreview.Proposal? = null,
        previewDraft: WorkoutPlanPreviewEditor.Draft? = null
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
        var initialSubstitutions: String? = null
        if (previewDraft != null) {
            val currentPlan = WorkoutDurationPlanner.build(
                equipped.program, l, m, currentExtensionPreferences(constraints)
            )
            val expected = WorkoutPlanPreviewEditor.start(
                moves = currentPlan.moves,
                targetMinutes = currentPlan.targetMinutes,
                level = l,
                constraints = constraints,
                equipmentSelection = equipmentSelection,
                additionalEquipment = additionalEquipment
            )
            if (expected == null || !WorkoutPlanPreviewEditor.isValid(previewDraft) ||
                expected.originalMoves != previewDraft.originalMoves ||
                previewDraft.targetMinutes != m || previewDraft.level != l ||
                previewDraft.constraints != constraints ||
                previewDraft.equipmentSelection != equipmentSelection ||
                previewDraft.additionalEquipment != additionalEquipment
            ) {
                Toast.makeText(this, "Giáo án hoặc lựa chọn đã thay đổi. Hãy mở lại bản xem trước.", Toast.LENGTH_LONG).show()
                return
            }
            val encoded = WorkoutPlanPreviewEditor.encodeSubstitutions(previewDraft)
            val decoded = WorkoutMoveSubstitution.decode(
                encoded, expected.originalMoves, constraints
            )
            val available = TrainingEquipmentAvailability.fromPreference(equipmentSelection) + additionalEquipment
            if (decoded.size != previewDraft.substitutions.size ||
                decoded.any { !TrainingEquipmentAvailability.allows(it.value, available) }
            ) {
                Toast.makeText(this, "Bài thay thế không còn hợp lệ với dụng cụ hiện tại.", Toast.LENGTH_LONG).show()
                return
            }
            initialSubstitutions = encoded
        }
        if (progression != null) {
            val planned = WorkoutDurationPlanner.build(
                equipped.program, l, m, currentExtensionPreferences(constraints)
            )
            val refreshed = steadyProgressSuggestion?.let {
                WorkoutProgressionPreview.propose(it, WorkoutData.history(this), planned.moves)
            }
            if (refreshed != progression) {
                Toast.makeText(this, "Nhật ký hoặc giáo án đã thay đổi. Hãy mở lại bản xem trước.", Toast.LENGTH_LONG).show()
                return
            }
            val checkpoint = getSharedPreferences("chibi_workout_checkpoint_guest", MODE_PRIVATE)
            val checkpointAge = System.currentTimeMillis() - checkpoint.getLong("savedAt", 0L)
            if (!checkpoint.getString("program", null).isNullOrBlank() &&
                checkpointAge in 0L..604_800_000L
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Có buổi tập đang chờ")
                    .setMessage("Hãy khôi phục hoặc kết thúc buổi tập đã lưu trước khi bắt đầu buổi mới với mục tiêu tiến triển.")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
                return
            }
        }
        prefs.edit()
            .putInt("fitLevel", l)
            // Keep the old index key readable while preserving the exact custom duration.
            .putInt("fitTime", WorkoutDurationChoices.legacyIndexForMinutes(m))
            .putInt("fitTimeMinutes", m)
            .apply()
        val launchIntent = Intent(this,GuidedWorkoutActivity::class.java)
            .putExtra("program",p.id).putExtra("level",l).putExtra("minutes",m)
            .putExtra("avoid_wrist_load", constraints.avoidWristLoad)
            .putExtra("avoid_floor_positions", constraints.avoidFloorPositions)
            .putExtra("avoid_overhead_reach", constraints.avoidOverheadReach)
            .putExtra("avoid_kneeling", constraints.avoidKneeling)
            .putExtra("extension_goal", recommendationGoalSpinner?.selectedItemPosition ?: 0)
            .putExtra("extension_energy", recommendationEnergySpinner?.selectedItemPosition ?: 1)
            .putExtra("extension_equipment", equipmentSelection)
            .putStringArrayListExtra("extension_additional_equipment", ArrayList(additionalEquipment.map { it.name }))
            .putExtra("extension_impact", effectiveImpactRestriction())
        if (progression != null) {
            launchIntent.putExtra("progression_exercise", progression.exerciseName)
                .putExtra("progression_reps", progression.targetValue)
                .putExtra("progression_unit", progression.unit)
        }
        initialSubstitutions?.let { launchIntent.putExtra("initial_substitutions", it) }
        startActivity(launchIntent)
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
        val sessionConstraints = currentWorkoutConstraintProfile()
        if (sessionConstraints.avoidWristLoad ||
            sessionConstraints.avoidFloorPositions ||
            sessionConstraints.avoidOverheadReach ||
            sessionConstraints.avoidJumping || sessionConstraints.avoidKneeling) {
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
