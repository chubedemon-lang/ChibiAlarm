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
    private val fadeHandler=Handler(Looper.getMainLooper())
    private val fadeTask=object:Runnable {
        var level=0
        override fun run() {
            level=(level+1).coerceAtMost(20)
            val volume=0.15f+0.85f*(level/20f)
            player?.setVolume(volume,volume)
            if(level<20 && player!=null) fadeHandler.postDelayed(this,1000)
        }
    }
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
        val settings=getSharedPreferences("settings",0)
        val preferred=settings.getString("alarmAudioPath","").orEmpty()
        val savedFile=java.io.File(filesDir,"chibi_voice_alarm.wav")
        val aiFile=if(preferred.isNotBlank())java.io.File(preferred) else savedFile
        val underApp=try { aiFile.canonicalPath.startsWith(filesDir.canonicalPath+java.io.File.separator) }
            catch(_:Exception){ false }
        val useAI=settings.getBoolean("aiVoiceEnabled",false) && underApp && aiFile.isFile
        if(!useAI) {
            speech = TextToSpeech(this) { status ->
                if(status==TextToSpeech.SUCCESS) {
                    speech?.setLanguage(Locale("vi","VN"))
                    speech?.setPitch(settings.getFloat("pitch",1f))
                    speech?.setSpeechRate(settings.getFloat("rate",1f))
                    val voiceName=settings.getString("voice",null)
                    speech?.voices?.firstOrNull { it.name==voiceName }?.let { speech?.voice=it }
                    speech?.speak(settings.getString("message","Dậy thôi bạn ơi!") ?: "Dậy thôi!",
                        TextToSpeech.QUEUE_FLUSH,null,"wake")
                }
            }
        }
        try {
            val ringtone=settings.getString("ringtone","").orEmpty()
            val uri=if(ringtone.isBlank()) android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                else android.net.Uri.parse(ringtone)
            player=MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                if(useAI) setDataSource(aiFile.absolutePath)
                else setDataSource(this@RingActivity,uri)
                isLooping=true;prepare();start()
            }
        }catch(_:Exception) {
            try { player?.release() }catch(_:Exception){}
            player=try { MediaPlayer.create(this,android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)?.apply {
                isLooping=true;start()
            } }catch(_:Exception){ null }
        }
        if(settings.getBoolean("fade_in",true) && player!=null) {
            player?.setVolume(0.15f,0.15f)
            fadeTask.level=0
            fadeHandler.postDelayed(fadeTask,1000)
        }
    }
    private fun stop() {
        fadeHandler.removeCallbacks(fadeTask)
        speech?.stop(); speech?.shutdown(); speech=null
        try { player?.stop() } catch (_:Exception) {}
        try { player?.release() } catch (_:Exception) {}
        player=null
        getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
    override fun onDestroy() { stop(); super.onDestroy() }
}
