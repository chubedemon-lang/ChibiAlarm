package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.widget.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Offline daily reflection, goal tracking and safe adaptive coaching. No API or LLM claims. */
class TransformationActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("transformation_v1",0) }
    private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private fun text(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(77,52,92));setPadding(3,8,3,8)
    }
    private fun section(root:LinearLayout,s:String){root.addView(text(s,20f))}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
    private fun render(){
        val root=LinearLayout(this).apply {orientation=1;setPadding(22,24,22,28);setBackgroundColor(Color.rgb(248,244,255))}
        root.addView(text("🐰 Chibi • Hành trình thay đổi",26f))
        root.addView(text("Trợ lý ngoại tuyến miễn phí • ghi nhận từng bước tiến, không chấm điểm giá trị con người.",14f))
        val goal=EditText(this).apply {
            hint="Mục tiêu quan trọng nhất trong 90 ngày";setText(prefs.getString("goal",""));maxLines=3
        }
        section(root,"🎯 Mục tiêu của mình")
        root.addView(goal)
        root.addView(Button(this).apply {text="💾 Lưu mục tiêu";setOnClickListener {
            prefs.edit().putString("goal",goal.text.toString().trim().take(300)).apply()
            Toast.makeText(this@TransformationActivity,"Đã lưu mục tiêu",Toast.LENGTH_SHORT).show()
        }})
        val data=try{JSONObject(prefs.getString("daily_"+today(),"{}") ?: "{}")}catch(_:Exception){JSONObject()}
        section(root,"🌤 Check-in hôm nay • "+today())
        val energy=Spinner(this).apply {
            adapter=ArrayAdapter(this@TransformationActivity,android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Rất ít năng lượng","Hơi mệt","Bình thường","Khá tốt","Tràn đầy năng lượng"))
            setSelection(data.optInt("energy",2).coerceIn(0,4))
        }
        root.addView(text("Mức năng lượng",15f));root.addView(energy)
        val mood=Spinner(this).apply {
            adapter=ArrayAdapter(this@TransformationActivity,android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Căng thẳng","Không vui","Bình thường","Vui vẻ","Rất vui"))
            setSelection(data.optInt("mood",2).coerceIn(0,4))
        }
        root.addView(text("Tâm trạng",15f));root.addView(mood)
        val sleep=EditText(this).apply {
            hint="Số giờ ngủ đêm qua (không bắt buộc)";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(data.optString("sleep",""))
        }
        root.addView(sleep)
        val exercise=CheckBox(this).apply {text="🏃 Đã vận động hôm nay";isChecked=data.optBoolean("exercise",false)}
        val learning=CheckBox(this).apply {text="📚 Đã học hoặc đọc sách";isChecked=data.optBoolean("learning",false)}
        val meals=CheckBox(this).apply {text="🥗 Đã ăn uống tương đối đều đặn";isChecked=data.optBoolean("meals",false)}
        val water=CheckBox(this).apply {text="💧 Đã chú ý uống nước";isChecked=data.optBoolean("water",false)}
        listOf(exercise,learning,meals,water).forEach{root.addView(it)}
        val note=EditText(this).apply {hint="Điều tốt mình đã làm / trở ngại hôm nay";maxLines=4;setText(data.optString("note",""))}
        root.addView(note)
        root.addView(Button(this).apply {text="✨ Lưu check-in và nhận gợi ý";setOnClickListener {
            val hours=sleep.text.toString().toDoubleOrNull()
            if(hours!=null && hours !in 0.0..24.0){sleep.error="Nhập từ 0 đến 24 giờ";return@setOnClickListener}
            val value=JSONObject().put("energy",energy.selectedItemPosition).put("mood",mood.selectedItemPosition)
                .put("sleep",sleep.text.toString()).put("exercise",exercise.isChecked)
                .put("learning",learning.isChecked).put("meals",meals.isChecked)
                .put("water",water.isChecked).put("note",note.text.toString().take(500))
            prefs.edit().putString("daily_"+today(),value.toString()).apply()
            val guidance=buildString {
                append("🐰 Chibi ghi nhận nỗ lực của bạn!\n\n")
                if(energy.selectedItemPosition<=1)append("🌿 Hôm nay năng lượng thấp: ưu tiên nghỉ ngơi và chọn một việc thật nhỏ, đừng ép tập nặng.\n")
                else if(!exercise.isChecked)append("🚶 Nếu thấy khỏe, thử 10 phút đi bộ hoặc vận động nhẹ.\n")
                else append("💪 Bạn đã vận động, hãy dành thời gian phục hồi nhé.\n")
                if(hours!=null && hours<7)append("🌙 Bạn ngủ ít hơn khoảng 7 giờ. Hãy thử giữ giờ đi ngủ đều đặn nếu có thể.\n")
                if(!learning.isChecked)append("📚 Thử 10–15 phút học hoặc đọc một chủ đề bạn quan tâm.\n")
                if(!meals.isChecked)append("🍚 Hãy dành thời gian cho bữa ăn phù hợp với nhu cầu của bạn.\n")
                if(!water.isChecked)append("💧 Nhớ uống nước theo cơn khát và điều kiện vận động.\n")
                append("\n🎯 Mục tiêu: "+(prefs.getString("goal","")?.ifBlank{"Chưa đặt"} ?: "Chưa đặt"))
                append("\n\nĐây là gợi ý chung từ quy tắc ngoại tuyến, không phải đánh giá y tế.")
            }
            AlertDialog.Builder(this@TransformationActivity).setTitle("🌸 Bản tin Chibi hôm nay")
                .setMessage(guidance).setPositiveButton("Cảm ơn Chibi",null).show()
        }})
        section(root,"📈 7 ngày gần đây")
        val calendar=Calendar.getInstance()
        val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US)
        val entries=(0..6).map {
            val day=dateFormat.format(calendar.time);calendar.add(Calendar.DATE,-1)
            day to prefs.getString("daily_"+day,null)
        }.reversed()
        val done=entries.count{it.second!=null}
        root.addView(text("Đã check-in $done/7 ngày • không cần duy trì chuỗi hoàn hảo.",15f))
        entries.forEach { (day,value)->
            val o=try{JSONObject(value ?: "{}")}catch(_:Exception){JSONObject()}
            root.addView(text((if(value==null)"○" else "✓")+" $day"+
                (if(value==null)"" else " • "+listOf("Ít năng lượng","Hơi mệt","Bình thường","Khá tốt","Nhiều năng lượng")[o.optInt("energy",2).coerceIn(0,4)]),14f))
        }
        root.addView(Button(this).apply {text="🗑 Xóa toàn bộ dữ liệu hành trình";setOnClickListener {
            AlertDialog.Builder(this@TransformationActivity).setTitle("Xóa tất cả dữ liệu?")
                .setMessage("Mục tiêu và toàn bộ check-in sẽ bị xóa khỏi điện thoại.")
                .setPositiveButton("Xóa"){_,_->prefs.edit().clear().apply();render()}
                .setNegativeButton("Hủy",null).show()
        }})
        root.addView(text("Dữ liệu lưu cục bộ trên thiết bị. Không gửi tâm trạng, giấc ngủ hay nhật ký lên Gemini.",12f))
        setContentView(ScrollView(this).apply{addView(root)})
    }
}
