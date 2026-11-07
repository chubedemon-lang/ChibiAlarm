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
    private var conversation: FitnessNaturalRequest.Result? = null
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
        root.addView(txt("💬 Nói với Chibi về buổi tập",19f))
        val naturalRequest=EditText(this).apply {
            hint="Ví dụ: Hôm nay mệt, muốn giãn cơ 10 phút, không dụng cụ"
            minLines=2
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription="Yêu cầu giáo án bằng tiếng Việt"
        }
        root.addView(naturalRequest)
        root.addView(txt("🤖 Chibi gợi ý buổi tập hôm nay",20f))
        val goals= arrayOf("Tăng sức mạnh","Tăng vận động tim mạch","Giãn cơ & linh hoạt","Giảm mỏi do ngồi nhiều","Mới bắt đầu","Thử thách mới")
        val energyLevels=arrayOf("Hôm nay hơi mệt","Cảm thấy bình thường","Rất nhiều năng lượng")
        val goalSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,goals)
            setSelection(prefs.getInt("recommendGoal",0).coerceIn(0,goals.lastIndex))
        }
        root.addView(goalSpinner)
        val energySpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,energyLevels)
            setSelection(prefs.getInt("recommendEnergy",1).coerceIn(0,2))
        }
        root.addView(energySpinner)
        root.addView(txt("🏋️ Dụng cụ hiện có",19f))
        val equipmentOptions=arrayOf("Không có dụng cụ","Có thảm tập","Có tạ hoặc dây kháng lực")
        val equipmentSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,equipmentOptions)
            setSelection(prefs.getInt("recommendEquipment",0).coerceIn(0,2))
        }
        root.addView(equipmentSpinner)
        root.addView(txt("🦵 Mức độ vận động phù hợp",19f))
        val impactOptions=arrayOf("Không có hạn chế được báo","Ưu tiên bài ít tác động, không nhảy","Đang đau hoặc có chấn thương")
        val impactSpinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,impactOptions)
            setSelection(prefs.getInt("recommendImpact",0).coerceIn(0,2))
        }
        root.addView(impactSpinner)
        root.addView(Button(this).apply {
            text="💬 Chibi hiểu yêu cầu của tôi"
            setOnClickListener {
                val request=naturalRequest.text.toString()
                if (request.isBlank()) {
                    naturalRequest.error="Hãy mô tả buổi tập bạn muốn"
                } else {
                    val parsed=FitnessCoachConversation.merge(conversation, FitnessNaturalRequest.parse(request))
                    conversation=parsed
                    parsed.goal?.let { goalSpinner.setSelection(it) }
                    parsed.energy?.let { energySpinner.setSelection(it) }
                    parsed.minutes?.let { requested ->
                        val closest=mins.indices.minByOrNull { kotlin.math.abs(mins[it]-requested) } ?: 0
                        length.setSelection(closest)
                    }
                    parsed.equipment?.let { equipmentSpinner.setSelection(it) }
                    parsed.impact?.let { impactSpinner.setSelection(it) }
                    AlertDialog.Builder(this@FitnessActivity)
                        .setTitle("🐰 Chibi đã nghe")
                        .setMessage(parsed.reply + "\n\n🐰 Chibi hỏi tiếp: " + FitnessCoachFollowUp.nextQuestion(parsed))
                        .setPositiveButton("Xem giáo án",null)
                        .setNeutralButton("Trả lời tiếp") { _, _ ->
                            naturalRequest.text.clear()
                            naturalRequest.hint=FitnessCoachFollowUp.nextQuestion(parsed)
                            naturalRequest.requestFocus()
                            naturalRequest.setSelection(naturalRequest.text.length)
                            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                                ?.showSoftInput(naturalRequest, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }.show()
                }
            }
        })
        root.addView(Button(this).apply {
            text="🐰 Gợi ý chương trình phù hợp"
            setOnClickListener {
                val goal=goalSpinner.selectedItemPosition
                val energy=energySpinner.selectedItemPosition
                val equipment=equipmentSpinner.selectedItemPosition
                val impact=impactSpinner.selectedItemPosition
                prefs.edit().putInt("recommendGoal",goal).putInt("recommendEnergy",energy)
                    .putInt("recommendEquipment",equipment).putInt("recommendImpact",impact).apply()
                val recommendation=FitnessPlanAdvisor.recommend(FitnessPlanAdvisor.Preferences(
                    goal,energy,level.selectedItemPosition,mins[length.selectedItemPosition],equipment,impact))
                val id=recommendation.programId
                val p=TrainingCatalog.byId(id)
                AlertDialog.Builder(this@FitnessActivity).setTitle("🐰 Chibi đề xuất: "+p.name)
                    .setMessage("Mục tiêu: "+goals[goal]+" • "+energyLevels[energy]+"\n"+
                        "Dụng cụ: "+equipmentOptions[equipment]+"\n"+
                        "Vận động: "+impactOptions[impact]+"\n\n"+
                        recommendation.explanation+
                        "\n\nGợi ý offline theo quy tắc trên máy, không phải AI chẩn đoán hay tư vấn y khoa.")
                    .setPositiveButton("▶ Bắt đầu"){_,_->
                        if (impact==2) {
                            AlertDialog.Builder(this@FitnessActivity).setTitle("Ưu tiên an toàn")
                                .setMessage("Khi đang đau hoặc chấn thương, chỉ tập nếu được chuyên gia xác nhận phù hợp. Dừng ngay nếu triệu chứng tăng.")
                                .setPositiveButton("Tôi đã hiểu"){_,_->startProgram(p)}
                                .setNegativeButton("Hủy",null).show()
                        } else startProgram(p)
                    }
                    .setNegativeButton("Chọn bài khác",null).show()
            }
        })
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
