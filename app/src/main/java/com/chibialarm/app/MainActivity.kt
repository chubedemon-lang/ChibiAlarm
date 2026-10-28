package com.chibialarm.app
import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import java.util.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

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
    private fun actionCard(icon: String, name: String, detail: String, tint: Int, action: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setPadding(22,19,18,19)
            background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tint, Color.WHITE)).apply { cornerRadius=29f }
            elevation=5f
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=15 }
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text=icon; textSize=31f; gravity=Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(64,72)
        })
        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,0,4,0) }
        labels.addView(TextView(this).apply { text=name; textSize=18f; setTextColor(Color.rgb(77,47,79)); setTypeface(null,1) })
        labels.addView(TextView(this).apply { text=detail; textSize=12f; setTextColor(Color.rgb(116,91,116)); maxLines=2 })
        card.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(TextView(this).apply { text="›"; textSize=30f; setTextColor(Color.rgb(145,95,143)) })
        return card
    }
    private fun render() {
        window.statusBarColor=Color.rgb(252,221,239)
        window.navigationBarColor=Color.rgb(255,238,248)
        val root=LinearLayout(this).apply { orientation=1; setPadding(24,34,24,25); setBackgroundColor(Color.rgb(255,242,249)) }
        val hero=LinearLayout(this).apply {
            orientation=1; gravity=Gravity.CENTER; setPadding(20,27,20,26)
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255,195,223),Color.rgb(222,210,255),Color.rgb(255,232,192))).apply { cornerRadius=40f }
            elevation=6f
        }
        hero.addView(TextView(this).apply { text="🐰 🌸 ✨"; textSize=52f; gravity=Gravity.CENTER })
        hero.addView(TextView(this).apply {
            text="CHIBI LIFE"; textSize=30f; gravity=Gravity.CENTER; setTypeface(null,1); setTextColor(Color.rgb(100,54,106))
        })
        hero.addView(TextView(this).apply {
            text="Trợ lý nhỏ • Mục tiêu lớn"; textSize=15f; gravity=Gravity.CENTER; setTextColor(Color.rgb(107,74,114))
        })
        root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=23 })
        root.addView(actionCard("🌱","Growth Coach","Thói quen, lịch luyện tập & thống kê 7 ngày",Color.rgb(218,247,222)) {
            startActivity(Intent(this,GrowthActivity::class.java))
        })
        root.addView(actionCard("⏰","Thêm báo thức","Đánh thức thông minh • Lặp theo ngày",Color.rgb(255,222,233)) { createAlarm() })
        root.addView(actionCard("✨","Gemini AI Studio","Nhập API key • Sáng tạo lời đánh thức",Color.rgb(232,220,255)) { geminiDialog() })
        root.addView(actionCard("🎙️","Thư viện giọng nói","Chọn và nghe thử giọng tiếng Việt",Color.rgb(218,238,255)) { voiceDialog() })
        root.addView(actionCard("🎭","12 phong cách giọng","Chibi, robot, trầm ấm, hài hước...",Color.rgb(255,239,202)) { styleDialog() })
        root.addView(actionCard("💬","Lời đánh thức","Tự viết nội dung báo thức",Color.rgb(255,228,235)) { messageDialog() })
        root.addView(actionCard("😴","Ngủ thêm","Điều chỉnh thời gian snooze",Color.rgb(224,238,255)) { snoozeDialog() })
        root.addView(actionCard("🔔","Quyền báo thức","Cấp quyền để báo thức đúng giờ",Color.rgb(232,243,228)) {
            if(Build.VERSION.SDK_INT>=31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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
                    tts?.setPitch(prefs.getFloat("pitch",1f)); tts?.setSpeechRate(prefs.getFloat("rate",1f))
                    tts?.speak(prefs.getString("message","Dậy thôi bạn ơi, chibi đang chờ bạn!")!!,TextToSpeech.QUEUE_FLUSH,null,"preview")
                }
                dialog.dismiss()
            }.setPositiveButton("Nghe thử"){_,_->
                tts?.speak(prefs.getString("message","Dậy thôi bạn ơi!")!!,TextToSpeech.QUEUE_FLUSH,null,"preview")
            }.setNegativeButton("Đóng",null).show()
    }
    private fun styleDialog() {
        val names = arrayOf("Tự nhiên", "Chibi dễ thương", "Em bé", "Nữ dịu dàng",
            "Nữ năng động", "Nữ nghiêm khắc", "Nam trầm", "Nam ấm áp",
            "Nam hài hước", "Robot", "Siêu nhanh", "Thì thầm chậm")
        val pitch = floatArrayOf(1f,1.4f,1.7f,1.2f,1.25f,1.05f,.75f,.85f,.95f,.8f,1.1f,.9f)
        val rate = floatArrayOf(1f,1.15f,1.2f,.9f,1.3f,1.1f,.9f,.95f,1.2f,1.1f,1.65f,.7f)
        val selected=prefs.getInt("voiceStyle",0).coerceIn(0,names.lastIndex)
        AlertDialog.Builder(this).setTitle("🎭 Phong cách giọng nói")
            .setSingleChoiceItems(names,selected){ dialog,i ->
                prefs.edit().putInt("voiceStyle",i).putFloat("pitch",pitch[i]).putFloat("rate",rate[i]).apply()
                tts?.setPitch(pitch[i]); tts?.setSpeechRate(rate[i])
                tts?.speak(prefs.getString("message","Dậy thôi bạn ơi!")!!,TextToSpeech.QUEUE_FLUSH,null,"style-preview")
                dialog.dismiss()
            }.setNegativeButton("Đóng",null).show()
    }
    private fun geminiDialog() {
        val panel=LinearLayout(this).apply { orientation=1; setPadding(24,12,24,12) }
        panel.addView(title("🔑 API key Gemini của bạn",16f))
        val keyField=EditText(this).apply {
            hint=if (GeminiSecrets.load(this@MainActivity)!=null) "Đã lưu an toàn • nhập để thay khóa" else "Dán API key từ Google AI Studio"
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        panel.addView(keyField)
        panel.addView(TextView(this).apply {
            text="Mỗi người dùng tự nhập khóa riêng. Khóa được mã hóa bằng Android Keystore trên thiết bị, không gửi về máy chủ của ứng dụng."
            textSize=13f
        })
        val topic=EditText(this).apply {
            hint="Ví dụ: Gọi dậy hài hước như nhân vật chibi"
            setText(prefs.getString("gemini_topic","Chào buổi sáng vui vẻ bằng tiếng Việt"))
        }
        panel.addView(title("✨ Ý tưởng lời đánh thức",16f)); panel.addView(topic)
        panel.addView(Button(this).apply {
            text="🗑️ Xóa API key khỏi máy"
            setOnClickListener {
                GeminiSecrets.clear(this@MainActivity)
                Toast.makeText(this@MainActivity,"Đã xóa khóa đã lưu",Toast.LENGTH_SHORT).show()
                keyField.hint="Dán API key mới"
            }
        })
        AlertDialog.Builder(this).setTitle("🌸 Gemini AI Studio").setView(panel)
            .setPositiveButton("✨ Tạo lời đánh thức"){_,_->
                val newKey=keyField.text.toString().trim()
                if(newKey.isNotEmpty()) GeminiSecrets.save(this,newKey)
                val key=GeminiSecrets.load(this)
                val idea=topic.text.toString().trim().take(400)
                if(key.isNullOrBlank() || idea.isBlank()) {
                    Toast.makeText(this,"Vui lòng nhập API key và ý tưởng",Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putString("gemini_topic",idea).apply()
                    Thread {
                        try {
                            val (message,model)=GeminiService.generate(key,idea)
                            runOnUiThread {
                                AlertDialog.Builder(this).setTitle("🐰 Lời đánh thức • "+model)
                                    .setMessage(message)
                                    .setPositiveButton("💾 Dùng làm báo thức"){_,_->
                                        prefs.edit().putString("message",message).apply()
                                        Toast.makeText(this,"Đã lưu lời đánh thức",Toast.LENGTH_SHORT).show()
                                    }.setNegativeButton("Bỏ qua",null).show()
                            }
                        } catch(e:Exception) {
                            runOnUiThread {
                                AlertDialog.Builder(this).setTitle("Không tạo được lời AI")
                                    .setMessage(e.message ?: "Kiểm tra mạng và API key")
                                    .setPositiveButton("Đóng",null).show()
                            }
                        }
                    }.start()
                }
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
