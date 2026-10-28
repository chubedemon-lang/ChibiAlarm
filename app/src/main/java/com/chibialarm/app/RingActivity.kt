package com.chibialarm.app
import android.app.*
import android.os.*
import android.graphics.Color
import android.media.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.view.*
import android.widget.*
import kotlin.random.Random

class RingActivity : Activity() {
    private var player: MediaPlayer? = null
    private var speech: TextToSpeech? = null
    private var correct = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val a = Random.nextInt(2,12); val b = Random.nextInt(2,12); correct = a+b
        val root = LinearLayout(this).apply { orientation = 1; gravity = Gravity.CENTER; setPadding(30,30,30,30); setBackgroundColor(Color.rgb(255,228,241)) }
        root.addView(TextView(this).apply { text = "🐣 DẬY THÔI! 🌞"; textSize = 32f })
        root.addView(TextView(this).apply { text = "Giải toán để tắt chuông: $a + $b = ?"; textSize = 20f })
        val answer = EditText(this).apply { inputType = 2; hint = "Nhập đáp án" }
        root.addView(answer)
        root.addView(Button(this).apply {
            text = "Tắt báo thức"
            setOnClickListener {
                if (answer.text.toString().toIntOrNull() == correct) { stop(); finish() }
                else Toast.makeText(this@RingActivity, "Sai rồi, thử lại nha!", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "😴 Ngủ thêm"
            setOnClickListener {
                val id = intent.getIntExtra("id", 0)
                Scheduler.snooze(this@RingActivity, id, getSharedPreferences("settings",0).getInt("snooze",5))
                stop(); finish()
            }
        })
        setContentView(root)
        speech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val settings = getSharedPreferences("settings",0)
                speech?.setLanguage(Locale("vi","VN"))
                speech?.setPitch(settings.getFloat("pitch",1f))
                speech?.setSpeechRate(settings.getFloat("rate",1f))
                val voiceName = settings.getString("voice",null)
                speech?.voices?.firstOrNull { it.name == voiceName }?.let { speech?.voice = it }
                speech?.speak(settings.getString("message","Dậy thôi bạn ơi!") ?: "Dậy thôi!", TextToSpeech.QUEUE_FLUSH, null, "wake")
            }
        }
        try {
            player = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)?.apply { isLooping = true; start() }
        } catch (_: Exception) {}
    }
    private fun stop() {
        speech?.stop(); speech?.shutdown(); speech=null
        player?.stop(); player?.release(); player = null
        getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
    override fun onDestroy() { stop(); super.onDestroy() }
}
