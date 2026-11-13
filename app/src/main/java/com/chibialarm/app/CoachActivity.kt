package com.chibialarm.app

import android.app.*
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Offline-first fitness coach. Network AI requires a separate authenticated backend. */
class CoachActivity:Activity() {
    private val memory by lazy { getSharedPreferences("coach_history",0) }
    private lateinit var messages:LinearLayout
    private lateinit var prompt:EditText
    private lateinit var send:Button
    private var history=JSONArray()
    private var speaker:TextToSpeech?=null
    private var speakerReady=false
    private var autoSpeak=false
    private var fitnessContext: FitnessNaturalRequest.Result? = null
    private val speechRequest=4401
    // Preserve the active coaching form across rotation, without persisting it to disk.
    private fun restoreFitnessContext(state: Bundle?): FitnessNaturalRequest.Result? {
        if (state?.getBoolean("fitness_context_present") != true) return null
        fun value(key: String): Int? = if (state.containsKey(key)) state.getInt(key) else null
        fun booleanValue(key: String): Boolean? =
            if (state.containsKey(key)) state.getBoolean(key) else null
        return FitnessNaturalRequest.Result(
            goal = value("fitness_goal"),
            level = value("fitness_level"),
            energy = value("fitness_energy"),
            minutes = value("fitness_minutes"),
            equipment = value("fitness_equipment"),
            impact = value("fitness_impact"),
            reply = "",
            avoidWristLoad = booleanValue("fitness_avoid_wrist_load"),
            avoidFloorPositions = booleanValue("fitness_avoid_floor_positions"),
            avoidOverheadReach = booleanValue("fitness_avoid_overhead_reach")
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        fitnessContext?.let { context ->
            outState.putBoolean("fitness_context_present", true)
            context.goal?.let { outState.putInt("fitness_goal", it) }
            context.energy?.let { outState.putInt("fitness_energy", it) }
            context.minutes?.let { outState.putInt("fitness_minutes", it) }
            context.equipment?.let { outState.putInt("fitness_equipment", it) }
            context.impact?.let { outState.putInt("fitness_impact", it) }
            context.level?.let { outState.putInt("fitness_level", it) }
            context.avoidWristLoad?.let { outState.putBoolean("fitness_avoid_wrist_load", it) }
            context.avoidFloorPositions?.let { outState.putBoolean("fitness_avoid_floor_positions", it) }
            context.avoidOverheadReach?.let { outState.putBoolean("fitness_avoid_overhead_reach", it) }
        }
        super.onSaveInstanceState(outState)
    }
    private fun text(s:String,size:Float=16f)=TextView(this).apply { text=s;textSize=size;setTextColor(Color.rgb(82,55,99));setPadding(7,7,7,7) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        fitnessContext = restoreFitnessContext(savedInstanceState)
        history=try { JSONArray(memory.getString("messages","[]")) }catch(_:Exception){ JSONArray() }
        val root=LinearLayout(this).apply { orientation=1;setPadding(17,18,17,14);setBackgroundColor(Color.rgb(250,241,255)) }
        root.addView(text("🐰 Chibi • Huấn luyện viên cá nhân",26f))
        root.addView(text("Nói chuyện bằng giọng Việt hoặc nhập tin nhắn. Huấn luyện offline trên thiết bị; AI trực tuyến chỉ mở khi có backend bảo vệ tài khoản.",13f))
        val buttons=LinearLayout(this).apply { orientation=0 }
        buttons.addView(Button(this).apply { text="🗓 Tạo lịch AI";setOnClickListener { askPlan() } },LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(Button(this).apply { text="🏃 Xem bài tập";setOnClickListener {
            val current=fitnessContext
            val intent=Intent(this@CoachActivity,FitnessActivity::class.java)
            current?.goal?.let { intent.putExtra("coach_goal",it) }
            current?.energy?.let { intent.putExtra("coach_energy",it) }
            current?.minutes?.let { intent.putExtra("coach_minutes",it) }
            current?.equipment?.let { intent.putExtra("coach_equipment",it) }
            current?.impact?.let { intent.putExtra("coach_impact",it) }
            current?.level?.let { intent.putExtra("coach_level",it) }
            current?.avoidWristLoad?.let { intent.putExtra("coach_avoid_wrist_load",it) }
            current?.avoidFloorPositions?.let { intent.putExtra("coach_avoid_floor_positions",it) }
            current?.avoidOverheadReach?.let { intent.putExtra("coach_avoid_overhead_reach",it) }
            startActivity(intent)
        } })
        buttons.addView(Button(this).apply { text="🗑 Xóa chat";setOnClickListener {
            history=JSONArray();fitnessContext=null;memory.edit().remove("messages").apply();redraw()
        } })
        root.addView(buttons)
        val voiceTools=LinearLayout(this).apply { orientation=0 }
        voiceTools.addView(Button(this).apply { text="🎤 Nói";setOnClickListener { explainMicrophoneThenListen() } },LinearLayout.LayoutParams(0,-2,1f))
        voiceTools.addView(Button(this).apply { text="🔊 Bật đọc";setOnClickListener {
            autoSpeak=!autoSpeak;text=if(autoSpeak)"🔇 Tắt đọc" else "🔊 Bật đọc"
            if(!autoSpeak)speaker?.stop()
        } },LinearLayout.LayoutParams(0,-2,1f))
        voiceTools.addView(Button(this).apply { text="■ Dừng";setOnClickListener { speaker?.stop() } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(voiceTools)
        speaker=TextToSpeech(this) { result ->
            speakerReady=result==TextToSpeech.SUCCESS
            if(speakerReady) { speaker?.setLanguage(Locale("vi","VN"))
                val pref=getSharedPreferences("settings",0)
                speaker?.setSpeechRate(pref.getFloat("rate",1f));speaker?.setPitch(pref.getFloat("pitch",1f))
                speaker?.voices?.firstOrNull { it.name==pref.getString("voice","") }?.let { speaker?.voice=it }
            }
        }
        messages=LinearLayout(this).apply { orientation=1 }
        root.addView(ScrollView(this).apply { addView(messages) },LinearLayout.LayoutParams(-1,0,1f))
        val composer=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
        prompt=EditText(this).apply {
            hint="Chibi ơi, giúp mình…";minLines=1;maxLines=4
        }
        composer.addView(prompt,LinearLayout.LayoutParams(0,-2,1f))
        send=Button(this).apply { text="➤";setOnClickListener { chat() } }
        composer.addView(send)
        root.addView(composer)
        setContentView(root);redraw()
    }
    private fun redraw() {
        messages.removeAllViews()
        if(history.length()==0) messages.addView(text("🐰 Chào bạn! Hãy kể mục tiêu, lịch học hoặc giờ giấc mà bạn muốn cải thiện.",17f))
        for(i in 0 until history.length()) {
            val item=history.optJSONObject(i) ?: continue
            val user=item.optString("role")=="user"
            val bubble=TextView(this).apply {
                text=(if(user) "👤 Bạn" else "🐰 Chibi AI")+"\n"+item.optString("text")
                textSize=15f;setTextColor(Color.rgb(70,50,85));setPadding(18,15,18,15)
                background=GradientDrawable().apply {
                    setColor(if(user) Color.rgb(226,242,255) else Color.WHITE);cornerRadius=23f
                }
            }
            messages.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=11 })
        }
    }
    /** Keep journal-derived health details transient; never copy them into chat history storage. */
    private fun persistChatHistory() {
        val durable = JSONArray()
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (!item.optBoolean("ephemeral", false)) durable.put(item)
        }
        memory.edit().putString("messages", durable.toString()).apply()
    }

    private fun append(role:String,value:String,persist:Boolean=true) {
        val item = JSONObject().put("role",role).put("text",value.take(2500))
        if (!persist) item.put("ephemeral",true)
        history.put(item)
        while(history.length()>14) history.remove(0)
        persistChatHistory();redraw()
        if(role=="assistant" && autoSpeak && speakerReady) speaker?.speak(value.take(1100),TextToSpeech.QUEUE_FLUSH,null,"coach-reply")
    }
    private fun chat() {
        val question=prompt.text.toString().trim().take(900)
        if(question.isEmpty()) return
        prompt.setText("")

        // Answer local journal questions before interpreting the message as a new plan reply.
        // The prompt and answer remain visible only for this Activity session and are not persisted.
        val journalReply = WorkoutHistoryConversation.answer(question, WorkoutData.history(this))
        if (journalReply != null) {
            append("user",question,persist=false)
            append(
                "assistant",
                journalReply + "\\n\\n(Đọc từ nhật ký trên thiết bị; câu hỏi và kết quả không được lưu vào lịch sử chat.)",
                persist=false
            )
            return
        }

        append("user",question)
        val next=FitnessCoachConversation.parseReply(fitnessContext,question)
        fitnessContext=FitnessCoachConversation.merge(fitnessContext,next)
        append("assistant",next.reply+"\\n\\n"+FitnessCoachFollowUp.nextQuestion(fitnessContext!!)+"\\n(Chế độ Fitness ngoại tuyến)")
    }
    /**
     * Android's speech recognizer may be provided by another app and may use
     * network processing. Require an explicit tap before each recording.
     */
    private fun explainMicrophoneThenListen() {
        // Avoid transcribing Chibi's own TTS reply.
        speaker?.stop()
        AlertDialog.Builder(this)
            .setTitle("🎤 Nhận dạng giọng nói")
            .setMessage("Chibi sẽ mở dịch vụ nhận dạng giọng nói trên máy. Dịch vụ này có thể dùng Internet và xử lý âm thanh theo chính sách của nhà cung cấp. Chibi chỉ nhận văn bản được trả về. Bạn sẽ được xem và sửa văn bản trước khi gửi. Bạn cũng có thể gõ thay vì dùng mic.")
            .setPositiveButton("Tiếp tục dùng mic") { _, _ -> listen() }
            .setNegativeButton("Để tôi gõ", null)
            .show()
    }

    private fun listen() {
        // Listening is user-initiated, never restarted by TTS callbacks.
        speaker?.stop()
        try {
            val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"vi-VN")
                putExtra(RecognizerIntent.EXTRA_PROMPT,"Nói với Chibi về mục tiêu của bạn")
            }
            startActivityForResult(i,speechRequest)
        } catch(_:Exception) { Toast.makeText(this,"Máy chưa có dịch vụ nhận dạng tiếng Việt. Bạn vẫn có thể gõ tin nhắn.",Toast.LENGTH_LONG).show() }
    }
    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==speechRequest && resultCode==RESULT_OK) {
            val recognized=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if(!recognized.isNullOrBlank()) {
                prompt.setText(recognized)
                prompt.setSelection(prompt.text.length)
                Toast.makeText(this,"Kiểm tra nội dung nhận dạng rồi bấm gửi.",Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onDestroy() { speaker?.stop();speaker?.shutdown();super.onDestroy() }
    private fun askPlan() {
        val field=EditText(this).apply { hint="Ví dụ: Mình làm việc 8h–17h, muốn học tiếng Anh và chạy bộ";minLines=3;maxLines=6 }
        val box=LinearLayout(this).apply { orientation=1;setPadding(22,12,22,12) }
        box.addView(text("Mô tả lịch hiện tại, mục tiêu và thời gian rảnh. Không nhập thông tin nhạy cảm.",14f))
        box.addView(field)
        var days=30
        box.addView(RadioGroup(this).apply {
            orientation=RadioGroup.HORIZONTAL
            addView(RadioButton(this@CoachActivity).apply { id=301; text="30 ngày";isChecked=true })
            addView(RadioButton(this@CoachActivity).apply { id=901; text="90 ngày" })
            setOnCheckedChangeListener { _,id->days=if(id==901)90 else 30 }
        })
        val dialog=AlertDialog.Builder(this).setTitle("✨ Lập kế hoạch ngoại tuyến")
            .setView(box).setPositiveButton("Tạo bản nháp",null).setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val detail=field.text.toString().trim().take(1500)
                if(detail.isBlank()) { field.error="Mô tả mục tiêu của bạn";return@setOnClickListener }
                dialog.dismiss()
                val (title,draft)=FreeCoach.plan(detail,days)
                showFreePlan(title,draft,days)
            }
        }
        dialog.show()
    }
    /** AI only drafts plans; commit routines before claiming an approved plan was saved. */
    private fun saveApprovedPlan(title:String,draft:List<Routine>,span:Int) {
        try {
            LifeData.save(this,LifeData.routines(this)+draft)
        } catch (_:Exception) {
            Toast.makeText(this,"Không lưu được lịch. Dữ liệu cũ vẫn được giữ, hãy thử lại.",Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (!GoalData.save(this,GoalData.goals(this)+
                LifeGoal(LifeData.nextId()+99,title,span,System.currentTimeMillis(),emptySet()))) {
                Toast.makeText(this,"Đã lưu lịch, nhưng chưa lưu được mục tiêu. Hãy kiểm tra lại.",Toast.LENGTH_LONG).show()
                return
            }
            append("assistant","Đã lưu kế hoạch $span ngày: $title, gồm ${draft.size} hoạt động.")
            Toast.makeText(this,"Đã lưu lịch và mục tiêu!",Toast.LENGTH_LONG).show()
        } catch (_:Exception) {
            Toast.makeText(this,"Đã lưu lịch, nhưng chưa lưu được mục tiêu. Hãy kiểm tra lại.",Toast.LENGTH_LONG).show()
        }
    }
    private fun showFreePlan(title:String,draft:List<Routine>,span:Int) {
        val preview=draft.joinToString("\n") { it.label()+" • "+it.title+" ("+it.duration+" phút)" }
        AlertDialog.Builder(this).setTitle("🐰 Lịch mẫu miễn phí $span ngày")
            .setMessage(title+"\n\n"+preview+"\n\nĐây là lịch mẫu, không phải kế hoạch do AI tạo.")
            .setPositiveButton("Lưu lịch"){_,_->
                saveApprovedPlan(title,draft,span)
            }.setNegativeButton("Hủy",null).show()
    }

}
