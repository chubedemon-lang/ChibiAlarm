package com.chibialarm.app

import android.app.*
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.*
import java.io.File

/** Optional cloud voice synthesis. Playback of saved generated audio works offline. */
class AIVoiceActivity:Activity() {
    private var media:MediaPlayer?=null
    private var preview:File?=null
    private val prefs by lazy { getSharedPreferences("settings",0) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=1;setPadding(26,31,26,26);setBackgroundColor(Color.rgb(255,240,249)) }
        root.addView(TextView(this).apply { text="🎧 Gemini Voice PRO";textSize=28f;setTextColor(Color.rgb(85,46,110)) })
        root.addView(TextView(this).apply {
            text="30 mẫu giọng AI • tạo âm thanh qua Gemini • lưu trên máy để báo thức ngoại tuyến. Có thể tiêu tốn hạn mức hoặc phí API."
            textSize=15f;setPadding(0,12,0,12)
        })
        val words=EditText(this).apply {
            setText(prefs.getString("message","Dậy thôi bạn ơi! Hôm nay sẽ là một ngày đáng yêu và tràn đầy năng lượng!"))
            hint="Lời báo thức sẽ được đọc";minLines=2;maxLines=6
        }
        root.addView(words)
        root.addView(TextView(this).apply { text="Chọn giọng";textSize=18f })
        val voice=Spinner(this).apply {
            adapter=ArrayAdapter(this@AIVoiceActivity,android.R.layout.simple_spinner_dropdown_item,GeminiVoice.choices)
        }
        root.addView(voice)
        root.addView(TextView(this).apply { text="Phong cách diễn cảm";textSize=18f })
        val style=Spinner(this).apply {
            adapter=ArrayAdapter(this@AIVoiceActivity,android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Vui nhộn, chibi","Nhẹ nhàng","Năng lượng cao","Tự nhiên","Truyền động lực","Dịu dàng, chậm","Sôi nổi"))
        }
        root.addView(style)
        val status=TextView(this).apply { text="Cần Internet và quyền sử dụng Gemini TTS của API key riêng.";textSize=14f;setPadding(0,12,0,12) }
        root.addView(status)
        val play=Button(this).apply { text="▶ Nghe lại";isEnabled=false }
        val save=Button(this).apply { text="💾 Dùng âm thanh này làm chuông";isEnabled=false }
        val generate=Button(this).apply {
            text="✨ Tạo giọng AI & nghe thử"
            setOnClickListener {
                val key=GeminiSecrets.load(this@AIVoiceActivity)
                if(key.isNullOrBlank()) {
                    AlertDialog.Builder(this@AIVoiceActivity).setTitle("Thiếu Gemini API key")
                        .setMessage("Hãy vào trang chủ > Gemini AI Studio để nhập API key trước.")
                        .setPositiveButton("Đóng",null).show()
                    return@setOnClickListener
                }
                val content=words.text.toString().trim()
                if(content.isBlank()) { words.error="Nhập lời cần đọc";return@setOnClickListener }
                isEnabled=false;play.isEnabled=false;save.isEnabled=false
                status.text="Đang tạo file giọng AI…"
                Thread {
                    try {
                        val wav=GeminiVoice.create(this@AIVoiceActivity,key,content,
                            GeminiVoice.choices[voice.selectedItemPosition],GeminiVoice.styles[style.selectedItemPosition])
                        runOnUiThread {
                            isEnabled=true;preview=wav;play.isEnabled=true;save.isEnabled=true
                            status.text="✓ Đã tạo âm thanh. Có thể phát thử trước khi lưu."
                            listen(wav)
                        }
                    }catch(e:Exception){runOnUiThread {
                        isEnabled=true;status.text="Tạo giọng thất bại: "+(e.message ?: "Lỗi mạng")
                    }}
                }.start()
            }
        }
        root.addView(generate)
        play.setOnClickListener { preview?.let { listen(it) } }
        root.addView(play)
        root.addView(Button(this).apply { text="■ Dừng phát";setOnClickListener { stopSound() } })
        save.setOnClickListener {
            try {
                val wav=preview ?: return@setOnClickListener
                GeminiVoice.useAsAlarm(this,wav)
                prefs.edit().putString("aiVoiceText",words.text.toString().take(400)).apply()
                status.text="✓ Đã lưu giọng AI. Báo thức sẽ phát file này ngay cả khi mất Internet."
                Toast.makeText(this,"Đã chọn giọng AI cho báo thức",Toast.LENGTH_LONG).show()
            }catch(e:Exception){status.text=e.message ?: "Không lưu được"}
        }
        root.addView(save)
        root.addView(Button(this).apply { text="🎙️ Trở về giọng máy (offline)";setOnClickListener {
            GeminiVoice.disable(this@AIVoiceActivity);status.text="Đã dùng giọng hệ thống."
        } })
        root.addView(TextView(this).apply {
            text="Lưu ý: nếu đổi lời đánh thức, hãy tạo lại file AI. Chỉ văn bản bạn yêu cầu và khóa API được gửi trực tiếp tới Google; không gửi qua máy chủ của Chibi."
            textSize=13f
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun listen(file:File) {
        stopSound()
        try {
            media=MediaPlayer().apply {
                setDataSource(file.absolutePath);prepare();setOnCompletionListener { stopSound() };start()
            }
        }catch(e:Exception){Toast.makeText(this,"Không phát được audio: "+e.message,Toast.LENGTH_LONG).show()}
    }
    private fun stopSound() {
        try { media?.stop() }catch(_:Exception){}
        try {media?.release()}catch(_:Exception){}
        media=null
    }
    override fun onDestroy() { stopSound();super.onDestroy() }
}
