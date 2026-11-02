package com.chibialarm.app

import android.app.*
import android.content.*
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

/** Offline habit and daily-routine dashboard; no account or server required. */
class GrowthActivity : Activity() {
    private val store by lazy { getSharedPreferences("growth", MODE_PRIVATE) }
    private val defaults = GrowthHabits.defaultNames
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun habits(): MutableList<String> = GrowthHabits.habitNames(this).toMutableList()
    private fun done(name:String, day:String=today()) = GrowthHabits.isDone(this, name, day)
    private fun saveHabits(names: List<String>): Boolean {
        val saved = GrowthHabits.saveHabits(this, names)
        if (!saved) Toast.makeText(this, "Không lưu được thói quen, dữ liệu cũ vẫn được giữ.", Toast.LENGTH_LONG).show()
        return saved
    }
    private fun label(t:String, size:Float=18f) = TextView(this).apply {
        text=t; textSize=size; setTextColor(Color.rgb(99,55,99)); setPadding(4,10,4,10)
    }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); showDashboard() }
    private fun showDashboard() {
        val root=LinearLayout(this).apply { orientation=1; setPadding(26,28,26,28); setBackgroundColor(Color.rgb(255,239,248)) }
        root.addView(label("🌱 Chibi Growth Coach",28f))
        root.addView(label("Mỗi ngày tiến bộ 1% ✨",16f))
        val habits=habits()
        if (GrowthHabits.hasMalformedHabitList(this)) {
            root.addView(label("⚠ Danh sách thói quen cũ bị lỗi. Chưa tính điểm RPG; thêm thói quen mới để lưu bản sao dữ liệu cũ.", 15f))
        }
        val completed=habits.count { done(it) }
        root.addView(label("🎯 Hôm nay: $completed / ${habits.size} thói quen",21f))
        val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=habits.size.coerceAtLeast(1); progress=completed
        }
        root.addView(progress)
        for (habit in habits) {
            val row=LinearLayout(this).apply { orientation=0; gravity=Gravity.CENTER_VERTICAL }
            row.addView(CheckBox(this).apply {
                text=habit; textSize=17f; isChecked=done(habit)
                layoutParams=LinearLayout.LayoutParams(0,-2,1f)
                setOnCheckedChangeListener { _,checked ->
                    if (!GrowthHabits.setDone(this@GrowthActivity, habit, today(), checked)) {
                        Toast.makeText(this@GrowthActivity,
                            "Không lưu được điểm danh. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
                    }
                    showDashboard()
                }
            })
            row.addView(Button(this).apply { text="🗑"; setOnClickListener {
                val updated = habits.filter { it != habit }
                if (saveHabits(updated)) showDashboard()
            } })
            root.addView(row)
        }
        root.addView(Button(this).apply { text="➕ Thêm thói quen"; setOnClickListener {
            val input=EditText(this@GrowthActivity).apply { hint="Ví dụ: Thiền 10 phút" }
            AlertDialog.Builder(this@GrowthActivity).setTitle("Thói quen mới").setView(input)
                .setPositiveButton("Lưu"){_,_->
                    val name=input.text.toString().trim().replace("\n"," ").take(60)
                    if(name.isNotBlank() && name !in habits) {
                        if (saveHabits(habits + name)) showDashboard()
                    }
                }.setNegativeButton("Hủy",null).show()
        } })
        root.addView(label("⏰ Lịch nhắc luyện tập",21f))
        root.addView(label("Chọn giờ để nhận thông báo nhắc mỗi ngày.",14f))
        root.addView(Button(this).apply { text="🔔 Đặt giờ nhắc hằng ngày"; setOnClickListener {
            val now=Calendar.getInstance()
            TimePickerDialog(this@GrowthActivity,{_,h,m->
                // Persist first: never claim a reminder is saved if the write failed.
                val saved=store.edit().putInt("remindHour",h).putInt("remindMinute",m).commit()
                if (!saved) {
                    Toast.makeText(this@GrowthActivity,"Không lưu được giờ nhắc. Hãy thử lại.",Toast.LENGTH_LONG).show()
                } else {
                    try {
                        GrowthReminder.schedule(this@GrowthActivity,h,m)
                        Toast.makeText(this@GrowthActivity,String.format(Locale.US,
                            "Đã lưu giờ nhắc %02d:%02d",h,m),Toast.LENGTH_LONG).show()
                    } catch (_: RuntimeException) {
                        // The persisted reminder can be restored on the next boot.
                        Toast.makeText(this@GrowthActivity,
                            "Đã lưu giờ nhắc, nhưng chưa lên lịch được. Hãy kiểm tra quyền báo thức.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            },now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),true).show()
        } })
        root.addView(label("📊 Tổng kết 7 ngày",21f))
        val date=Calendar.getInstance()
        for (i in 0..6) {
            val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date.time)
            root.addView(label("$day  •  ${habits.count { done(it,day) }} / ${habits.size}",14f))
            date.add(Calendar.DAY_OF_YEAR,-1)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
