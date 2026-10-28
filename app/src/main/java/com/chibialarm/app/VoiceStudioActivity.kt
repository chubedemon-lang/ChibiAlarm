package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.*
import android.view.Gravity
import java.util.Locale

/** Voice previews with explicit play/stop and voice availability feedback. */
class VoiceStudioActivity:Activity() {
    private var tts:TextToSpeech?=null
    private var ready=false
    private lateinit var status:TextView
    private lateinit var list:LinearLayout
    private val prefs by lazy { getSharedPreferences("settings",0) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=1; setPadding(24,32,24,24); setBackgroundColor(Color.rgb(255,240,249)) }
        root.addView(TextView(this).apply { text="🎙️ Voice Studio PRO";textSize=27f;setTextColor(Color.rgb(91,51,101)) })
        root.addView(TextView(this).apply { text="Chạm ▶ để nghe thử • Chọn ✓ để lưu giọng";textSize=15f })
        status=TextView(this).apply { text="Đang khởi tạo giọng nói…";textSize=15f;setPadding(0,14,0,14) }
        root.addView(status)
        val sample=EditText(this).apply { setText("Dậy thôi bạn ơi! Hôm nay sẽ là một ngày thật tuyệt vời!"); hint="Câu nghe thử" }
        root.addView(sample)
        root.addView(TextView(this).apply { text="🎚 Cao độ (pitch)";textSize=15f })
        root.addView(SeekBar(this).apply {
            max=150;progress=(prefs.getFloat("pitch",1f)*100).toInt().coerceIn(50,200)-50
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                    if(fromUser) { val p=(value+50)/100f; prefs.edit().putFloat("pitch",p).apply();tts?.setPitch(p) }
                }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply { text="🎚 Tốc độ nói";textSize=15f })
        root.addView(SeekBar(this).apply {
            max=150;progress=(prefs.getFloat("rate",1f)*100).toInt().coerceIn(50,200)-50
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                    if(fromUser) { val r=(value+50)/100f; prefs.edit().putFloat("rate",r).apply();tts?.setSpeechRate(r) }
                }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        val controls=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
        controls.addView(Button(this).apply { text="■ Dừng";setOnClickListener { tts?.stop();status.text="Đã dừng phát thử" } })
        controls.addView(Button(this).apply { text="↻ Tải lại";setOnClickListener { populate(sample.text.toString()) } })
        controls.addView(Button(this).apply { text="⬇ Giọng mới";setOnClickListener {
            try { startActivity(android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            catch(_:Exception){ status.text="Hãy vào Cài đặt Android > Văn bản thành giọng nói để tải giọng tiếng Việt." }
        } })
        root.addView(controls)
        list=LinearLayout(this).apply { orientation=1 }
        root.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        tts=TextToSpeech(this) { result ->
            runOnUiThread {
                ready=result==TextToSpeech.SUCCESS
                if(ready) {
                    tts?.setLanguage(Locale("vi","VN"))
                    tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                        override fun onStart(utteranceId:String) { runOnUiThread { status.text="🔊 Đang phát thử…" } }
                        override fun onDone(utteranceId:String) { runOnUiThread { status.text="✓ Phát thử hoàn tất" } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId:String) { runOnUiThread { status.text="Không phát được. Kiểm tra gói giọng nói trên máy." } }
                    })
                    populate(sample.text.toString())
                } else status.text="Không khởi tạo được Text-to-Speech. Hãy bật công cụ TTS trong cài đặt Android."
            }
        }
    }
    private fun populate(sample:String) {
        list.removeAllViews()
        if(!ready) { status.text="TTS chưa sẵn sàng";return }
        val voices=tts?.voices?.filter { it.locale.language=="vi" }?.sortedWith(compareBy({it.isNetworkConnectionRequired},{it.name})) ?: emptyList()
        status.text=if(voices.isEmpty()) "Không có giọng tiếng Việt. Hãy cài dữ liệu giọng nói trong cài đặt Android." else "Tìm thấy ${voices.size} giọng tiếng Việt"
        val selected=prefs.getString("voice",null)
        voices.forEach { voice ->
            val row=LinearLayout(this).apply { orientation=1;setPadding(14,12,14,12) }
            row.addView(TextView(this).apply {
                text=(if(selected==voice.name) "✓ " else "🎤 ")+voice.name+" • "+(if(voice.isNetworkConnectionRequired) "Cần mạng" else "Ngoại tuyến")
                textSize=16f
            })
            val actions=LinearLayout(this)
            actions.addView(Button(this).apply { text="▶ Nghe thử";setOnClickListener {
                val result=tts?.setVoice(voice)
                if(result==TextToSpeech.ERROR) {
                    status.text="Giọng chưa khả dụng, cần tải dữ liệu hoặc bật mạng."
                } else {
                    tts?.setPitch(prefs.getFloat("pitch",1f))
                    tts?.setSpeechRate(prefs.getFloat("rate",1f))
                    val code=tts?.speak(sample.ifBlank { "Chào buổi sáng!" },TextToSpeech.QUEUE_FLUSH,null,"preview-${System.nanoTime()}")
                    if(code==TextToSpeech.ERROR) status.text="Không thể bắt đầu phát thử."
                }
            } })
            actions.addView(Button(this).apply { text="✓ Chọn";setOnClickListener {
                prefs.edit().putString("voice",voice.name).apply()
                status.text="Đã chọn giọng: "+voice.name
                populate(sample)
            } })
            row.addView(actions);list.addView(row)
        }
    }
    override fun onDestroy() { tts?.stop();tts?.shutdown();super.onDestroy() }
}
