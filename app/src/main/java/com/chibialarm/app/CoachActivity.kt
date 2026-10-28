package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Direct Gemini BYOK coach. AI only proposes schedules; user explicitly approves changes. */
class CoachActivity:Activity() {
    private val memory by lazy { getSharedPreferences("coach_history",0) }
    private lateinit var messages:LinearLayout
    private lateinit var prompt:EditText
    private lateinit var send:Button
    private var history=JSONArray()
    private fun text(s:String,size:Float=16f)=TextView(this).apply { text=s;textSize=size;setTextColor(Color.rgb(82,55,99));setPadding(7,7,7,7) }
    private fun key():String? {
        val k=GeminiSecrets.load(this)
        if(k.isNullOrBlank()) AlertDialog.Builder(this).setTitle("🔑 Cần Gemini API key")
            .setMessage("Hãy nhập API key trong mục Gemini AI Studio ở trang chủ. Không cần gửi khóa cho ai.")
            .setPositiveButton("Đã hiểu",null).show()
        return k
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        history=try { JSONArray(memory.getString("messages","[]")) }catch(_:Exception){ JSONArray() }
        val root=LinearLayout(this).apply { orientation=1;setPadding(17,18,17,14);setBackgroundColor(Color.rgb(250,241,255)) }
        root.addView(text("✨ Gemini Life Coach",26f))
        root.addView(text("Trợ lý AI giúp lập kế hoạch. Lịch chỉ thay đổi sau khi bạn xác nhận.",13f))
        val buttons=LinearLayout(this).apply { orientation=0 }
        buttons.addView(Button(this).apply { text="🗓 Tạo lịch AI";setOnClickListener { askPlan() } },LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(Button(this).apply { text="🗑 Xóa chat";setOnClickListener {
            history=JSONArray();memory.edit().remove("messages").apply();redraw()
        } })
        root.addView(buttons)
        messages=LinearLayout(this).apply { orientation=1 }
        root.addView(ScrollView(this).apply { addView(messages) },LinearLayout.LayoutParams(-1,0,1f))
        val composer=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
        prompt=EditText(this).apply {
            hint="Chibi ơi, giúp mình…";minLines=1;maxLines=4
        }
        composer.addView(prompt,LinearLayout.LayoutParams(0,-2,1f))
        send=Button(this).apply { text="➤";setOnClickListener { chat() } }
        composer.addView(send);root.addView(composer)
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
    private fun append(role:String,value:String) {
        history.put(JSONObject().put("role",role).put("text",value.take(2500)))
        while(history.length()>14) history.remove(0)
        memory.edit().putString("messages",history.toString()).apply();redraw()
    }
    private fun chat() {
        val k=key() ?: return
        val question=prompt.text.toString().trim().take(900)
        if(question.isEmpty())return
        prompt.setText("")
        append("user",question)
        send.isEnabled=false
        val context=(0 until history.length()).joinToString("\n") { i ->
            val item=history.getJSONObject(i)
            (if(item.optString("role")=="user") "Người dùng: " else "Trợ lý: ")+item.optString("text")
        }
        Thread {
            try {
                val answer=GeminiService.generatePrompt(k,
                    "Bạn là trợ lý Chibi Life thân thiện, nói tiếng Việt, đưa gợi ý thực tế và an toàn về quản lý thời gian, thói quen, vận động nhẹ. Không tự nhận đã tạo lịch, không chẩn đoán bệnh, không đưa kế hoạch quá sức. Tối đa 170 từ mỗi câu trả lời.\n"+context).first
                runOnUiThread { append("assistant",answer);send.isEnabled=true }
            } catch(e:Exception) { runOnUiThread {
                send.isEnabled=true
                AlertDialog.Builder(this).setTitle("Chưa kết nối được Gemini")
                    .setMessage(e.message ?: "Kiểm tra mạng và API key")
                    .setPositiveButton("Đóng",null).show()
            } }
        }.start()
    }
    private fun askPlan() {
        val k=key() ?: return
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
        val dialog=AlertDialog.Builder(this).setTitle("✨ Lập kế hoạch cùng Gemini")
            .setView(box).setPositiveButton("Tạo bản nháp",null).setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val detail=field.text.toString().trim().take(1500)
                if(detail.isBlank()) { field.error="Mô tả mục tiêu của bạn";return@setOnClickListener }
                dialog.dismiss()
                val progress=ProgressDialog.show(this,"🐰 Chibi đang nghĩ…","Đang chuẩn bị bản nháp để bạn duyệt",true,false)
                val span=days
                Thread {
                    try {
                        val instruction="""Bạn là AI lập lịch cá nhân lành mạnh cho người trưởng thành.
Trả về CHỈ MỘT JSON object hợp lệ, không markdown, cấu trúc:
{"title":"mục tiêu ngắn","routines":[{"title":"tên 1","category":"🏃 Sức khỏe","hour":7,"minute":30,"duration":20}, {"title":"tên 2","category":"📚 Học tập","hour":19,"minute":0,"duration":30}]}
Tạo 3 tới 6 hoạt động thực tế, không trùng giờ rõ rệt, giờ 0..23, phút 0..59, thời lượng 5..120, tiếng Việt. Không phải lời khuyên y tế. Thời gian chương trình: $span ngày.
Thông tin người dùng: $detail"""
                        val response=GeminiService.generatePrompt(k,instruction).first
                        val left=response.indexOf('{');val right=response.lastIndexOf('}')
                        if(left<0 || right<=left) throw IllegalStateException("AI không trả về bản nháp JSON hợp lệ.")
                        val json=JSONObject(response.substring(left,right+1))
                        val arr=json.getJSONArray("routines")
                        val draft=mutableListOf<Routine>()
                        for(i in 0 until arr.length().coerceAtMost(8)) {
                            val r=arr.getJSONObject(i)
                            val name=r.optString("title").trim().take(60)
                            val h=r.optInt("hour",-1);val m=r.optInt("minute",-1)
                            val duration=r.optInt("duration",30)
                            if(name.isNotBlank() && h in 0..23 && m in 0..59 && duration in 5..120)
                                draft.add(Routine(LifeData.nextId()+i,name,r.optString("category","📚 Học tập").take(25),h,m,duration))
                        }
                        if(draft.isEmpty())throw IllegalStateException("Bản nháp không có hoạt động hợp lệ.")
                        val goalTitle=json.optString("title","Kế hoạch mới").take(80)
                        runOnUiThread {
                            progress.dismiss()
                            val preview=draft.sortedWith(compareBy({it.hour},{it.minute}))
                                .joinToString("\n") { it.label()+"  •  "+it.title+" ("+it.duration+"p)" }
                            AlertDialog.Builder(this).setTitle("🗓 Duyệt kế hoạch $span ngày")
                                .setMessage("🎯 $goalTitle\n\n$preview\n\nLịch cũ sẽ được giữ nguyên. Chỉ thêm lịch sau khi bạn đồng ý.")
                                .setPositiveButton("✓ Đồng ý thêm lịch"){_,_->
                                    LifeData.save(this,LifeData.routines(this)+draft)
                                    GoalData.save(this,GoalData.goals(this)+LifeGoal(LifeData.nextId()+99,goalTitle,span,System.currentTimeMillis(),emptySet()))
                                    append("assistant","Đã thêm kế hoạch $span ngày: $goalTitle, gồm ${draft.size} hoạt động.")
                                    Toast.makeText(this,"Đã thêm lịch và mục tiêu!",Toast.LENGTH_LONG).show()
                                }.setNegativeButton("Bỏ qua",null).show()
                        }
                    }catch(e:Exception){runOnUiThread {
                        progress.dismiss();AlertDialog.Builder(this).setTitle("Chưa tạo được kế hoạch")
                            .setMessage(e.message ?: "Hãy thử lại").setPositiveButton("Đóng",null).show()
                    }}
                }.start()
            }
        }
        dialog.show()
    }
}
