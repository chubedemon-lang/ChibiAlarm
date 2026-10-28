package com.chibialarm.app
import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import java.util.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private var tts: TextToSpeech? = null
    private var voices: List<android.speech.tts.Voice> = emptyList()
    private val prefs by lazy { getSharedPreferences("settings", 0) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("vi","VN"))
                voices = tts?.voices?.filter { it.locale.language == "vi" }?.sortedBy { it.name } ?: emptyList()
            }
        }
        render()
    }
    private fun title(text: String, size: Float = 20f) = TextView(this).apply {
        this.text=text; textSize=size; setTextColor(Color.rgb(113,59,108))
        setPadding(0,16,0,12)
    }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1; setPadding(28,35,28,25); setBackgroundColor(Color.rgb(255,237,248)) }
        root.addView(title("🐰 Chibi Alarm 🌸",30f))
        root.addView(title("Dậy thôi, thế giới đáng yêu đang chờ! ☀️",15f))
        root.addView(Button(this).apply { text="＋ Thêm báo thức"; setOnClickListener { createAlarm() } })
        root.addView(Button(this).apply { text="🎙️ Thư viện giọng nói"; setOnClickListener { voiceDialog() } })
        root.addView(Button(this).apply { text="💬 Sửa lời đánh thức"; setOnClickListener { messageDialog() } })
        root.addView(Button(this).apply { text="😴 Thời gian ngủ thêm"; setOnClickListener { snoozeDialog() } })
        root.addView(Button(this).apply {
            text="🔔 Cấp quyền báo thức chính xác"
            setOnClickListener { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        })
        root.addView(title("⏰ Báo thức của bạn"))
        list=LinearLayout(this).apply { orientation=1 }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }
    private fun createAlarm() {
        val now=Calendar.getInstance()
        TimePickerDialog(this,{_,h,m->
            val id=(System.currentTimeMillis()%Int.MAX_VALUE).toInt()
            val days=booleanArrayOf(true,true,true,true,true,true,true)
            val names=arrayOf("CN","T2","T3","T4","T5","T6","T7")
            AlertDialog.Builder(this).setTitle("🐻 Lặp lại vào ngày nào?")
                .setMultiChoiceItems(names,days){_,which,checked->days[which]=checked}
                .setPositiveButton("Lưu"){_,_->
                    val mask=days.mapIndexed { i,b -> if(b) 1 shl i else 0 }.sum()
                    getSharedPreferences("alarms",0).edit().putString(id.toString(),"$h,$m,$mask").apply()
                    Scheduler.schedule(this,id,h,m); refresh()
                }.setNegativeButton("Hủy",null).show()
        },now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),true).show()
    }
    private fun voiceDialog() {
        val options=if(voices.isEmpty()) listOf("Giọng mặc định (chưa tìm thấy giọng tiếng Việt)") else voices.map { it.name + if(it.isNetworkConnectionRequired) " • Online" else " • Offline" }
        val selected=prefs.getString("voice","")
        val idx=voices.indexOfFirst { it.name==selected }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("🎙️ Chọn giọng tiếng Việt")
            .setSingleChoiceItems(options.toTypedArray(),idx){ dialog,which->
                if(voices.isNotEmpty()) {
                    val voice=voices[which]; prefs.edit().putString("voice",voice.name).apply()
                    tts?.voice=voice
                    tts?.speak(prefs.getString("message","Dậy thôi bạn ơi, chibi đang chờ bạn!")!!,TextToSpeech.QUEUE_FLUSH,null,"preview")
                }
                dialog.dismiss()
            }.setPositiveButton("Nghe thử"){_,_->
                tts?.speak(prefs.getString("message","Dậy thôi bạn ơi!")!!,TextToSpeech.QUEUE_FLUSH,null,"preview")
            }.setNegativeButton("Đóng",null).show()
    }
    private fun messageDialog() {
        val input=EditText(this).apply { setSingleLine(false); setText(prefs.getString("message","Dậy thôi bạn ơi, chibi đang chờ bạn!")) }
        AlertDialog.Builder(this).setTitle("💬 Lời đánh thức").setView(input)
            .setPositiveButton("Lưu"){_,_->prefs.edit().putString("message",input.text.toString()).apply()}
            .setNegativeButton("Hủy",null).show()
    }
    private fun snoozeDialog() {
        val choices=arrayOf("3 phút","5 phút","10 phút","15 phút")
        val values=intArrayOf(3,5,10,15)
        AlertDialog.Builder(this).setTitle("😴 Ngủ thêm")
            .setSingleChoiceItems(choices,values.indexOf(prefs.getInt("snooze",5)).coerceAtLeast(0)){dialog,i->
                prefs.edit().putInt("snooze",values[i]).apply(); dialog.dismiss()
            }.show()
    }
    private fun refresh() {
        list.removeAllViews()
        val alarms=getSharedPreferences("alarms",0)
        for((key,value) in alarms.all) {
            val id=key.toIntOrNull()?:continue
            val p=(value as? String)?.split(",")?:continue
            if(p.size<2)continue
            val h=p[0].toIntOrNull()?:continue; val m=p[1].toIntOrNull()?:continue
            val row=LinearLayout(this).apply { orientation=0; gravity=Gravity.CENTER_VERTICAL }
            row.addView(title(String.format(Locale.getDefault(),"🐻 %02d:%02d",h,m),24f).apply { layoutParams=LinearLayout.LayoutParams(0,100,1f) })
            row.addView(Button(this).apply { text="Xóa"; setOnClickListener { Scheduler.cancel(this@MainActivity,id); alarms.edit().remove(key).apply(); refresh() } })
            list.addView(row)
        }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
