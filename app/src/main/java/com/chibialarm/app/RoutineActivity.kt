package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.util.Calendar
import java.util.Locale

class RoutineActivity:Activity() {
    private val categories=arrayOf("🏃 Sức khỏe","📚 Học tập","🧘 Tinh thần","💼 Công việc","💤 Nghỉ ngơi","💧 Sinh hoạt")
    private fun text(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(76,49,87));setPadding(6,8,6,8)
    }
    private fun background(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=32f }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1;setPadding(24,25,24,25);setBackgroundColor(Color.rgb(252,242,249)) }
        root.addView(text("🌸 Lịch trình Chibi",28f))
        root.addView(text("Sắp xếp từng khung giờ, từng mục tiêu mỗi ngày",14f))
        val routines=LifeData.routines(this)
        val done=LifeData.count(this)
        val panel=LinearLayout(this).apply {
            orientation=1;setPadding(20,17,20,17);background=background(Color.rgb(231,221,255))
        }
        panel.addView(text("🐰 Hôm nay: $done / ${routines.size} hoạt động",20f))
        panel.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=routines.size.coerceAtLeast(1);progress=done
        })
        panel.addView(text("⭐ ${LifeData.points(this)} XP       🔥 ${LifeData.streak(this)} ngày liên tục",15f))
        root.addView(panel)
        root.addView(Button(this).apply { text="＋ Thêm hoạt động theo giờ";setOnClickListener { editor(null) } })
        root.addView(Button(this).apply { text="🎯 Mục tiêu 30/90 ngày";setOnClickListener { startActivity(android.content.Intent(this@RoutineActivity,GoalsActivity::class.java)) } })
        if(routines.isEmpty()) {
            root.addView(text("Chưa có lịch. Thêm việc đầu tiên hoặc chọn mẫu bên dưới.",15f))
            root.addView(Button(this).apply { text="✨ Tạo lịch mẫu cân bằng";setOnClickListener {
                LifeData.save(this@RoutineActivity,listOf(
                    Routine(LifeData.nextId(),"Uống nước & vận động nhẹ","💧 Sinh hoạt",7,0,10),
                    Routine(LifeData.nextId()+1,"Tập thể dục","🏃 Sức khỏe",7,30,30),
                    Routine(LifeData.nextId()+2,"Học kỹ năng mới","📚 Học tập",19,0,45),
                    Routine(LifeData.nextId()+3,"Đọc sách và thư giãn","🧘 Tinh thần",21,0,20)
                ));render()
            } })
        }
        routines.forEach { r ->
            val row=LinearLayout(this).apply { orientation=1;setPadding(13,10,13,10)
                background=background(if(LifeData.isDone(this@RoutineActivity,r.id)) Color.rgb(218,247,225) else Color.WHITE)
                elevation=2f }
            val header=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
            header.addView(CheckBox(this).apply {
                text=r.label()+" • "+r.title; textSize=16f;isChecked=LifeData.isDone(this@RoutineActivity,r.id)
                layoutParams=LinearLayout.LayoutParams(0,-2,1f)
                setOnCheckedChangeListener { _,checked->LifeData.mark(this@RoutineActivity,r.id,checked);render() }
            })
            header.addView(Button(this).apply { text="✎";setOnClickListener { editor(r) } })
            row.addView(header)
            row.addView(text(r.category+" • "+r.duration+" phút",13f))
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=10 })
        }
        root.addView(text("📝 Mẹo: Tích ✓ khi hoàn thành để tăng XP và duy trì chuỗi ngày.",13f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun editor(existing:Routine?) {
        val box=LinearLayout(this).apply { orientation=1;setPadding(22,10,22,8) }
        val title=EditText(this).apply { hint="Tên hoạt động (vd: Chạy bộ)";setText(existing?.title ?: "") }
        box.addView(title)
        val picker=TimePicker(this).apply {
            setIs24HourView(true)
            hour=existing?.hour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            minute=existing?.minute ?: 0
        }
        box.addView(picker)
        val category=Spinner(this).apply { adapter=ArrayAdapter(this@RoutineActivity,android.R.layout.simple_spinner_dropdown_item,categories) }
        category.setSelection(categories.indexOf(existing?.category).coerceAtLeast(0));box.addView(category)
        box.addView(text("Thời lượng (phút)",14f))
        val duration=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            setText((existing?.duration ?: 30).toString())
        }
        box.addView(duration)
        val dayFlags=BooleanArray(7) { idx -> ((existing?.repeatMask ?: 127) and (1 shl idx))!=0 }
        val dayLabels=arrayOf("CN","T2","T3","T4","T5","T6","T7")
        val days=LinearLayout(this).apply { orientation=0 }
        dayLabels.forEachIndexed { i,label ->
            days.addView(CheckBox(this).apply {
                text=label;textSize=10f;buttonTintList=android.content.res.ColorStateList.valueOf(Color.rgb(149,89,160))
                isChecked=dayFlags[i];setOnCheckedChangeListener { _,b->dayFlags[i]=b }
            },LinearLayout.LayoutParams(0,-2,1f))
        }
        box.addView(days)
        val dialog=AlertDialog.Builder(this).setTitle(if(existing==null) "🌱 Thêm hoạt động" else "✎ Sửa hoạt động")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("💾 Lưu",null).setNegativeButton("Hủy",null)
            .apply {
                if(existing!=null) setNeutralButton("🗑 Xóa"){_,_->
                    LifeData.save(this@RoutineActivity,LifeData.routines(this@RoutineActivity).filterNot { it.id==existing.id })
                    RoutineReminder.cancel(this@RoutineActivity,existing);render()
                }
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name=title.text.toString().trim().take(70)
                val mins=duration.text.toString().toIntOrNull()
                val mask=dayFlags.mapIndexed { i,b->if(b) 1 shl i else 0 }.sum()
                if(name.isBlank() || mins==null || mins !in 1..480 || mask==0) {
                    Toast.makeText(this,"Nhập tên, thời lượng 1–480 phút và chọn ngày lặp.",Toast.LENGTH_LONG).show()
                } else {
                    val r=Routine(existing?.id ?: LifeData.nextId(),name,category.selectedItem.toString(),picker.hour,picker.minute,mins,mask)
                    LifeData.save(this,LifeData.routines(this).filterNot { it.id==r.id }+r)
                    dialog.dismiss();render()
                }
            }
        }
        dialog.show()
    }
}
