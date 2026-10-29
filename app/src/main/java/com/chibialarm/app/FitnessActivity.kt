package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*

/** Catalog-driven free coach: selectable tracks, difficulty, guided session, weekly reminder. */
class FitnessActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("chibi_fitness_offline",0) }
    private lateinit var root:LinearLayout
    private lateinit var level:Spinner
    private lateinit var length:Spinner
    private lateinit var filter:Spinner
    private val levels=arrayOf("🌱 Mới bắt đầu","🌿 Đang tập đều","🔥 Có kinh nghiệm")
    private val lengths=arrayOf("10 phút","15 phút","20 phút","30 phút","40 phút")
    private val mins=intArrayOf(10,15,20,30,40)
    private val categories=arrayOf("Tất cả","Toàn thân","Sức mạnh","Cardio","Linh hoạt","Vận động nhẹ","Phục hồi nhẹ","Thử thách")
    private fun txt(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(73,59,88));setPadding(7,6,7,7)
    }
    private fun panel(bg:Int)=GradientDrawable().apply { setColor(bg);cornerRadius=33f }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        root=LinearLayout(this).apply {
            orientation=1;setPadding(22,26,22,28);setBackgroundColor(Color.rgb(246,250,249))
        }
        val hero=LinearLayout(this).apply {
            orientation=1;gravity=Gravity.CENTER;setPadding(22,24,22,24)
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(215,248,226),Color.rgb(230,223,255))).apply { cornerRadius=35f }
        }
        hero.addView(txt("🐰 CHIBI FITNESS PRO",27f))
        hero.addView(txt("12 chương trình • miễn phí • offline • không cần phòng gym",14f))
        root.addView(hero)
        root.addView(txt("🎚 Trình độ của bạn",19f))
        level=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,levels)
            setSelection(prefs.getInt("fitLevel",0).coerceIn(0,2))
        }
        root.addView(level)
        root.addView(txt("⏱ Thời lượng bạn muốn",19f))
        length=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,lengths)
            setSelection(prefs.getInt("fitTime",2).coerceIn(0,mins.lastIndex))
        }
        root.addView(length)
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text="🥗 Dinh dưỡng"
            isAllCaps=false
            setOnClickListener { startActivity(Intent(this@FitnessActivity,NutritionActivity::class.java)) }
        },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply {
            text="📊 Nhật ký tập"
            isAllCaps=false
            setOnClickListener { startActivity(Intent(this@FitnessActivity,WorkoutActivity::class.java)) }
        },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        root.addView(txt("📚 Bộ môn & chương trình",23f))
        root.addView(txt("Chọn chương trình, xem từng động tác và dùng huấn luyện bằng giọng nói.",14f))
        filter=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,categories)
            setSelection(prefs.getInt("fitCategory",0).coerceIn(0,categories.lastIndex))
        }
        root.addView(filter)
        val container=LinearLayout(this).apply { orientation=1 }
        root.addView(container)
        filter.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                prefs.edit().putInt("fitCategory",position).apply()
                drawPrograms(container,categories[position])
            }
        }
        root.addView(txt("🛡 Hướng dẫn an toàn",20f))
        root.addView(txt("Mục tiêu sức khỏe chung cho người trưởng thành: tăng dần hoạt động vừa sức và tập sức mạnh từ 2 ngày/tuần. Dùng bài nhẹ khi mới bắt đầu. Dừng tập nếu đau ngực, khó thở khác thường, chóng mặt hoặc đau tăng dần. Người có bệnh nền/chấn thương/mang thai nên hỏi chuyên gia trước khi áp dụng.",14f))
        root.addView(Button(this).apply { text="📖 Nguồn WHO • CDC • NHS";setOnClickListener {
            AlertDialog.Builder(this@FitnessActivity).setTitle("📚 Nguồn tham khảo")
                .setMessage("WHO: who.int/publications/i/item/9789240015128\nCDC: cdc.gov/physical-activity-basics/adding-adults/what-counts.html\nNHS: nhs.uk/live-well/exercise/strength-exercises/\n\nĐây là giáo trình mẫu dựa trên nguyên tắc cơ bản, không phải chẩn đoán hay trị liệu.")
                .setPositiveButton("Đóng",null).show()
        } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun drawPrograms(container:LinearLayout,category:String) {
        container.removeAllViews()
        TrainingCatalog.programs.filter { category=="Tất cả" || it.category==category }.forEach { program ->
            val card=LinearLayout(this).apply {
                orientation=1;setPadding(17,14,17,14);background=panel(Color.WHITE)
                elevation=2f
            }
            card.addView(txt(program.name,19f))
            card.addView(txt(program.subtitle+" • "+program.moves.size+" bước",13f))
            val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply { text="📖 Xem bài";isAllCaps=false;setOnClickListener {
                val guide=TrainingCatalog.adjusted(program,level.selectedItemPosition,mins[length.selectedItemPosition])
                    .mapIndexed { i,m->"${i+1}. ${m.name} — ${m.dose}\nCách tập: ${m.steps}\nDễ hơn: ${m.easier}\nLưu ý: ${m.avoid}" }
                    .joinToString("\n\n")
                AlertDialog.Builder(this@FitnessActivity)
                    .setTitle(program.name).setMessage(guide)
                    .setPositiveButton("🎙 Bắt đầu"){_,_->startProgram(program)}
                    .setNegativeButton("Đóng",null).show()
            } },LinearLayout.LayoutParams(0,-2,1f))
            buttons.addView(Button(this).apply { text="▶ Tập ngay";isAllCaps=false;setOnClickListener {
                startProgram(program)
            } },LinearLayout.LayoutParams(0,-2,1f))
            card.addView(buttons)
            card.addView(Button(this).apply { text="📆 Thêm lịch tuần";isAllCaps=false;setOnClickListener {
                addRoutine(program)
            } })
            container.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=15 })
        }
    }
    private fun startProgram(p:TrainingProgram) {
        val l=level.selectedItemPosition
        val m=mins[length.selectedItemPosition]
        prefs.edit().putInt("fitLevel",l).putInt("fitTime",length.selectedItemPosition).apply()
        startActivity(Intent(this,GuidedWorkoutActivity::class.java)
            .putExtra("program",p.id).putExtra("level",l).putExtra("minutes",m))
    }
    private fun addRoutine(p:TrainingProgram) {
        val current=LifeData.routines(this)
        val trainingDays=if(level.selectedItemPosition==0) ((1 shl 2) or (1 shl 5))
            else ((1 shl 1) or (1 shl 4) or (1 shl 6))
        val r=Routine(LifeData.nextId(),p.name,"🏃 Sức khỏe",18,0,
            mins[length.selectedItemPosition],trainingDays)
        AlertDialog.Builder(this).setTitle("📅 Thêm lịch định kỳ?")
            .setMessage("18:00 • "+(if(level.selectedItemPosition==0) "T3 và T6" else "T2, T5 và T7")+
                "\n${p.name}\nCó thể chỉnh giờ và ngày trong Lịch trình.")
            .setPositiveButton("Thêm lịch"){_,_->
                LifeData.save(this,current+r)
                Toast.makeText(this,"Đã thêm lịch tập.",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Hủy",null).show()
    }
}
