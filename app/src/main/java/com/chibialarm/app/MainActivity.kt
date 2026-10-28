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
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1; setPadding(28,35,28,25); setBackgroundColor(Color.rgb(255,237,248)) }
        root.addView(title("🐰 Chibi Alarm 🌸",30f))
        root.addView(title("Dậy thôi, thế giới đáng yêu đang chờ! ☀️",15f))
        root.addView(Button(this).apply { text="＋ Thêm báo thức"; setOnClickListener { createAlarm() } })
        root.addView(Button(this).apply { text="🎙️ Thư viện giọng nói"; setOnClickListener { voiceDialog() } })
        root.addView(Button(this).apply { text="✨ Gemini AI - Lời đánh thức"; setOnClickListener { geminiDialog() } })
        root.addView(Button(this).apply { text="🎭 12 phong cách giọng"; setOnClickListener { styleDialog() } })
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
        val profile=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Gemini 1","Gemini 2","Gemini 3","Gemini 4","Gemini 5"))
            setSelection(prefs.getInt("gemini_profile",1).coerceIn(1,5)-1)
        }
        panel.addView(title("👤 Hồ sơ Gemini",15f)); panel.addView(profile)
        val apiKey=EditText(this).apply {
            hint="Nhập API key (lưu mã hóa trong Android Keystore)"
            setSingleLine(true)
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        panel.addView(title("🔐 Gemini API key",15f)); panel.addView(apiKey)
        val topic=EditText(this).apply {
            hint="Lời đánh thức chibi hài hước, dễ thương..."
            setText(prefs.getString("gemini_topic","Chào buổi sáng theo phong cách chibi vui vẻ"))
        }
        panel.addView(title("💡 Ý tưởng lời đánh thức",15f)); panel.addView(topic)
        profile.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                apiKey.setText("")
                apiKey.hint=if(GeminiKeyVault.read(this@MainActivity,position+1)!=null)
                    "Đã lưu key an toàn • để trống để dùng key cũ" else "Nhập Gemini API key"
            }
        }
        AlertDialog.Builder(this).setTitle("✨ Gemini AI trực tiếp").setView(panel)
            .setNeutralButton("Xóa key"){_,_->
                GeminiKeyVault.save(this,profile.selectedItemPosition+1,"")
                Toast.makeText(this,"Đã xóa key của hồ sơ",Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Tạo lời đánh thức"){_,_->
                val selected=profile.selectedItemPosition+1
                val entered=apiKey.text.toString().trim()
                if(entered.isNotEmpty()) GeminiKeyVault.save(this,selected,entered)
                val key=GeminiKeyVault.read(this,selected)
                val idea=topic.text.toString().trim().take(400)
                if(key.isNullOrBlank() || idea.isBlank()) {
                    Toast.makeText(this,"Hãy nhập API key và ý tưởng",Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putInt("gemini_profile",selected).putString("gemini_topic",idea).apply()
                    Thread {
                        try {
                            val url=URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                            val conn=(url.openConnection() as HttpsURLConnection).apply {
                                requestMethod="POST"; connectTimeout=12000; readTimeout=30000
                                doOutput=true
                                setRequestProperty("Content-Type","application/json")
                                setRequestProperty("x-goog-api-key",key)
                            }
                            val prompt="Viết lời đánh thức tiếng Việt, vui vẻ kiểu chibi, 1-3 câu, không markdown. Chủ đề: "+idea
                            val body=JSONObject().put("contents",org.json.JSONArray().put(
                                JSONObject().put("parts",org.json.JSONArray().put(JSONObject().put("text",prompt)))))
                            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                            val status=conn.responseCode
                            if(status !in 200..299) throw Exception("Gemini HTTP $status (kiểm tra key, hạn mức hoặc thanh toán)")
                            val result=JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            conn.disconnect()
                            val message=result.getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                                .getString("text").trim().take(500)
                            if(message.isBlank()) throw Exception("Gemini trả về nội dung trống")
                            runOnUiThread {
                                prefs.edit().putString("message",message).apply()
                                AlertDialog.Builder(this).setTitle("🌸 Lời đánh thức AI")
                                    .setMessage(message).setPositiveButton("Nghe thử"){_,_->
                                        tts?.speak(message,TextToSpeech.QUEUE_FLUSH,null,"gemini-preview")
                                    }.setNegativeButton("Đóng",null).show()
                            }
                        } catch(e:Exception) {
                            runOnUiThread { Toast.makeText(this,"Gemini: "+e.message,Toast.LENGTH_LONG).show() }
                        }
                    }.start()
                }
            }.setNegativeButton("Hủy",null).show()
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
