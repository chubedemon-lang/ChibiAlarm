package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Workout(val date:String,val name:String,val minutes:Int,val note:String)
object WorkoutData {
    private fun prefs(ctx:Context)=ctx.getSharedPreferences("workouts_v12",Context.MODE_PRIVATE)
    fun history(ctx:Context):List<Workout> {
        return try {
            val arr=JSONArray(prefs(ctx).getString("history","[]"))
            (0 until arr.length()).map { i->
                val o=arr.getJSONObject(i)
                Workout(o.optString("date"),o.optString("name"),o.optInt("minutes"),o.optString("note"))
            }
        }catch(_:Exception){ emptyList() }
    }
    fun save(ctx:Context,items:List<Workout>) {
        val arr=JSONArray()
        items.forEach {
            arr.put(JSONObject().put("date",it.date).put("name",it.name)
                .put("minutes",it.minutes).put("note",it.note))
        }
        prefs(ctx).edit().putString("history",arr.toString()).apply()
    }
}
class WorkoutActivity:Activity() {
    private val workouts=arrayOf("🚶 Đi bộ","🏃 Chạy bộ","🧘 Yoga","💪 Tập sức mạnh","🚴 Đạp xe","🏋️ Tập tạ","🌼 Giãn cơ","🌙 Thiền")
    private fun text(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(79,54,91));setPadding(5,8,5,8)
    }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1;setPadding(24,31,24,31);setBackgroundColor(Color.rgb(241,252,247)) }
        root.addView(text("🏃 Chibi Workout",28f))
        root.addView(text("Ghi nhật ký luyện tập ngoại tuyến. Theo dõi đều đặn, không ép tập quá sức.",15f))
        root.addView(Button(this).apply { text="＋ Ghi buổi tập";setOnClickListener {addWorkout()} })
        val all=WorkoutData.history(this)
        val cal=Calendar.getInstance()
        val formatter=SimpleDateFormat("yyyy-MM-dd",Locale.US)
        val week=(0..6).map {
            val d=formatter.format(cal.time)
            val mins=all.filter { w->w.date==d }.sumOf { w->w.minutes }
            cal.add(Calendar.DATE,-1)
            d to mins
        }.reversed()
        val total=week.sumOf { it.second }
        root.addView(text("📊 7 ngày: $total phút   •   ${all.size} buổi đã ghi",19f))
        val max=week.maxOfOrNull { it.second }?.coerceAtLeast(30) ?: 30
        week.forEach { (day,mins)->
            val line=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
            line.addView(text(day.substring(5),13f),LinearLayout.LayoutParams(80,-2))
            line.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                this.max=max;progress=mins
            },LinearLayout.LayoutParams(0,22,1f))
            line.addView(text(" $mins p",13f))
            root.addView(line)
        }
        root.addView(Button(this).apply { text="📤 Chia sẻ báo cáo CSV";setOnClickListener {
            val csv="date,exercise,minutes,note\n"+all.joinToString("\n") {
                listOf(it.date,it.name,it.minutes.toString(),it.note).joinToString(",") { value->
                    "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""
                }
            }
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Chibi workout log")
                putExtra(Intent.EXTRA_TEXT,csv)
            }
            startActivity(Intent.createChooser(intent,"Xuất nhật ký luyện tập"))
        } })
        root.addView(text("🗓 Nhật ký gần đây",21f))
        if(all.isEmpty())root.addView(text("Chưa có buổi tập. Bắt đầu với 5–10 phút vận động nhẹ nhé!",15f))
        all.asReversed().take(30).forEachIndexed { index,w->
            val row=LinearLayout(this).apply {
                orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(12,10,12,10)
                background=GradientDrawable().apply { setColor(Color.WHITE);cornerRadius=22f }
            }
            row.addView(text("${w.date}\n${w.name} • ${w.minutes} phút"+(if(w.note.isBlank())"" else "\n"+w.note),15f),
                LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply { text="🗑";setOnClickListener {
                AlertDialog.Builder(this@WorkoutActivity).setTitle("Xóa buổi tập?")
                    .setPositiveButton("Xóa"){_,_->
                        val current=WorkoutData.history(this@WorkoutActivity).toMutableList()
                        val original=all.indexOf(w)
                        if(original in current.indices)current.removeAt(original)
                        WorkoutData.save(this@WorkoutActivity,current);render()
                    }.setNegativeButton("Hủy",null).show()
            } })
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=9 })
        }
        root.addView(text("Dữ liệu này do bạn tự nhập, không phải số đo từ thiết bị y tế hoặc Health Connect.",12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun addWorkout() {
        val form=LinearLayout(this).apply { orientation=1;setPadding(24,9,24,12) }
        val spinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@WorkoutActivity,android.R.layout.simple_spinner_dropdown_item,workouts)
        }
        form.addView(spinner)
        form.addView(text("Thời gian luyện tập (phút)",14f))
        val minutes=EditText(this).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText("20") }
        form.addView(minutes)
        val note=EditText(this).apply { hint="Ghi chú (không bắt buộc)";maxLines=3 }
        form.addView(note)
        val dialog=AlertDialog.Builder(this).setTitle("🏃 Buổi tập hôm nay")
            .setView(form).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val mins=minutes.text.toString().toIntOrNull()
                if(mins==null || mins !in 1..600)minutes.error="Nhập 1 đến 600 phút"
                else {
                    WorkoutData.save(this,WorkoutData.history(this)+Workout(
                        LifeData.today(),spinner.selectedItem.toString(),mins,note.text.toString().take(150)))
                    dialog.dismiss();render()
                }
            }
        }
        dialog.show()
    }
}
