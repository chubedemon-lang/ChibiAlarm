package com.chibialarm.app
import android.app.*
import android.os.*
import android.graphics.Color
import android.media.*
import android.view.*
import android.widget.*
import kotlin.random.Random

class RingActivity : Activity() {
    private var player: MediaPlayer? = null
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
            text = "Ngủ thêm 5 phút"
            setOnClickListener {
                val id = intent.getIntExtra("id", 0)
                val pi = Scheduler.pending(this@RingActivity, id)
                getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+300000, pi)
                stop(); finish()
            }
        })
        setContentView(root)
        try {
            player = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)?.apply { isLooping = true; start() }
        } catch (_: Exception) {}
    }
    private fun stop() {
        player?.stop(); player?.release(); player = null
        getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
    override fun onDestroy() { stop(); super.onDestroy() }
}
