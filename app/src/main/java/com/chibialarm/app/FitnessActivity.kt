package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import java.util.Locale

data class BodyMove(val name:String,val sets:String,val guide:String,val caution:String)
object FitnessLibrary {
    val beginner=listOf(
        BodyMove("Khởi động tại chỗ","3–5 phút","Đi bộ tại chỗ, xoay khớp nhẹ, nâng gối chậm. Tăng nhịp từ từ.","Không xoay giật khớp."),
        BodyMove("Squat ghế","2 × 8–10 lần","Đứng trước ghế chắc chắn, chân rộng bằng vai. Đẩy hông ra sau như ngồi xuống, chạm nhẹ ghế rồi đứng lên. Gối hướng theo mũi chân.","Giảm biên độ nếu đau gối; không cố chịu đau."),
        BodyMove("Chống đẩy tường","2 × 8–12 lần","Đặt tay lên tường ngang vai, lùi chân. Giữ thân thẳng, gập khuỷu đưa ngực gần tường rồi đẩy ra.","Không võng lưng; giảm độ khó bằng cách đứng gần tường."),
        BodyMove("Glute bridge","2 × 10–12 lần","Nằm ngửa, gập gối, bàn chân trên sàn. Siết mông nâng hông vừa đủ thành đường thẳng vai–hông–gối, hạ chậm.","Không ưỡn lưng quá mức."),
        BodyMove("Bird-dog","2 × 6 lần mỗi bên","Chống tay và gối trên sàn, duỗi tay phải và chân trái, giữ 2 giây rồi đổi bên. Giữ lưng trung lập.","Nếu mất thăng bằng chỉ duỗi một chi."),
        BodyMove("Thả lỏng","3–5 phút","Đi bộ chậm và giãn cơ nhẹ không nảy. Hít thở bình thường.","Không ép kéo căng tới mức đau.")
    )
    val medium=listOf(
        BodyMove("Khởi động","5 phút","Đi bộ nhanh tại chỗ, nâng gối thấp và xoay khớp nhẹ.","Tăng cường độ dần."),
        BodyMove("Squat trọng lượng cơ thể","3 × 10–15 lần","Hạ hông ra sau, giữ gót chân chạm sàn, gối cùng hướng bàn chân; đứng lên có kiểm soát.","Giảm độ sâu nếu khó chịu."),
        BodyMove("Chống đẩy gối hoặc sàn","3 × 6–12 lần","Tay dưới vai, thân thành đường thẳng; hạ ngực có kiểm soát rồi đẩy lên.","Dừng nếu đau cổ tay hoặc vai."),
        BodyMove("Lunge lùi","2 × 8 lần mỗi chân","Bước một chân về sau, hạ trọng tâm nhẹ rồi đứng về vị trí ban đầu.","Có thể vịn tường để giữ thăng bằng."),
        BodyMove("Plank","3 × 20–35 giây","Chống khuỷu, siết bụng vừa phải, giữ đầu–lưng–hông thẳng và tiếp tục thở.","Dừng nếu đau lưng hoặc chóng mặt."),
        BodyMove("Thả lỏng","5 phút","Đi bộ chậm và giãn nhẹ các nhóm cơ vừa tập.","Không nín thở.")
    )
    val advanced=listOf(
        BodyMove("Khởi động","6–8 phút","Đi bộ nhanh tại chỗ, squat nông, bước ngang, làm nóng vai và hông.","Không bỏ qua khởi động."),
        BodyMove("Squat tempo","3 × 12–15 lần","Hạ người trong 3 giây, dừng ngắn rồi đứng lên, giữ thân ổn định.","Giảm số lần nếu mất kỹ thuật."),
        BodyMove("Chống đẩy","3 × 8–15 lần","Giữ thân thẳng, hạ có kiểm soát, đẩy lên không khóa khuỷu quá mạnh.","Có thể chuyển sang chống đẩy gối."),
        BodyMove("Split squat","3 × 8–12 lần mỗi chân","Đứng chân trước chân sau, hạ trọng tâm theo phương thẳng đứng, đẩy lên bằng chân trước.","Dùng tường hỗ trợ nếu cần."),
        BodyMove("Mountain climber chậm","3 × 25 giây","Tư thế plank tay, đưa từng gối về phía ngực với nhịp có kiểm soát.","Giữ lưng trung lập; dừng nếu đau vai."),
        BodyMove("Side plank gối","2 × 20–30 giây mỗi bên","Chống khuỷu, gối gập, nâng hông và giữ thân thẳng.","Giảm thời gian nếu không giữ được tư thế."),
        BodyMove("Thả lỏng","5 phút","Hạ nhịp tim bằng đi bộ chậm và giãn nhẹ.","Không dừng đột ngột sau vận động mạnh.")
    )
    fun mealTips(goal:Int):String {
        val intro=when(goal) {
            1->"Mục tiêu tăng sức mạnh: ăn đủ bữa, bổ sung nguồn đạm ở mỗi bữa và tăng lượng ăn vừa phải nếu cần."
            2->"Mục tiêu kiểm soát cân nặng: ưu tiên thực phẩm ít chế biến, ăn chậm, theo dõi cảm giác đói no; không nhịn ăn cực đoan."
            else->"Mục tiêu sức khỏe: ăn đa dạng, cân bằng giữa rau củ, ngũ cốc, chất đạm và chất béo lành mạnh."
        }
        return intro+"\n\n🍳 SÁNG: trứng hoặc đậu phụ + bánh mì nguyên cám/yến mạch + trái cây.\n\n🍚 TRƯA: cơm vừa nhu cầu + cá/thịt nạc/đậu + nhiều rau.\n\n🥗 TỐI: rau + đạm + khoai/cơm tùy mức vận động.\n\n🍌 BỮA PHỤ (nếu đói): sữa chua không đường, trái cây hoặc các loại hạt phù hợp.\n\n💧 Uống nước đều trong ngày, tăng khi trời nóng hoặc ra mồ hôi. Nhu cầu nước và năng lượng khác nhau theo từng người.\n\n⚠️ Đây là ví dụ bữa ăn, không phải thực đơn tính calo hay điều trị bệnh. Điều chỉnh theo dị ứng, ngân sách, văn hóa ăn uống và nhu cầu cá nhân."
    }
}
class FitnessActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("chibi_fitness_offline",0) }
    private val levels=arrayOf("Mới bắt đầu","Đã tập một thời gian","Có kinh nghiệm")
    private val goals=arrayOf("Khỏe mạnh","Tăng sức mạnh","Kiểm soát cân nặng")
    private lateinit var root:LinearLayout
    private fun t(value:String,size:Float=16f)=TextView(this).apply { text=value;textSize=size;setTextColor(Color.rgb(67,56,77));setPadding(5,9,5,9) }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        root=LinearLayout(this).apply { orientation=1;setPadding(22,26,22,28);setBackgroundColor(Color.rgb(244,252,248)) }
        root.addView(t("🐰 Chibi Fitness Offline",27f))
        root.addView(t("Huấn luyện không dụng cụ • không API • không Internet • miễn phí",14f))
        root.addView(t("Chọn trình độ",18f))
        val level=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,levels)
            setSelection(prefs.getInt("level",0))
        }
        root.addView(level)
        root.addView(t("Mục tiêu",18f))
        val goal=Spinner(this).apply {
            adapter=ArrayAdapter(this@FitnessActivity,android.R.layout.simple_spinner_dropdown_item,goals)
            setSelection(prefs.getInt("goal",0))
        }
        root.addView(goal)
        root.addView(Button(this).apply { text="✨ Tạo lịch tập phù hợp";setOnClickListener {
            prefs.edit().putInt("level",level.selectedItemPosition).putInt("goal",goal.selectedItemPosition).apply()
            showPlan(level.selectedItemPosition,goal.selectedItemPosition)
        } })
        root.addView(Button(this).apply { text="🍽 Hướng dẫn ăn uống trong ngày";setOnClickListener {
            AlertDialog.Builder(this@FitnessActivity).setTitle("🥗 Dinh dưỡng cơ bản")
                .setMessage(FitnessLibrary.mealTips(goal.selectedItemPosition))
                .setPositiveButton("Đã hiểu",null).show()
        } })
        root.addView(t("📚 Nguyên tắc tập luyện",21f))
        root.addView(t("• 3 ngày tập sức mạnh/tuần là điểm bắt đầu hợp lý cho nhiều người; xen kẽ ngày nghỉ hoặc vận động nhẹ.\n• Tăng số lần từ từ khi đã giữ được kỹ thuật.\n• Thở đều, không nín thở; dừng nếu đau ngực, choáng hoặc khó thở bất thường.\n• Người có bệnh nền, chấn thương hoặc đang mang thai nên tham khảo chuyên gia trước khi bắt đầu.",15f))
        root.addView(t("🗓 Lịch mẫu 7 ngày",20f))
        root.addView(t("T2: Buổi tập A\nT3: Đi bộ 15–30 phút tùy sức\nT4: Nghỉ hoặc giãn cơ nhẹ\nT5: Buổi tập A\nT6: Đi bộ hoặc vận động yêu thích\nT7: Buổi tập A (nếu hồi phục tốt)\nCN: Nghỉ ngơi",15f))
        root.addView(Button(this).apply { text="📝 Mở nhật ký tập luyện";setOnClickListener {
            startActivity(android.content.Intent(this@FitnessActivity,WorkoutActivity::class.java))
        } })
        root.addView(t("Chibi Fitness hiện dùng quy tắc và thư viện bài tập trên máy, không phải AI sinh ngôn ngữ. Không thay thế chuyên gia y tế hay dinh dưỡng.",12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun showPlan(level:Int,goal:Int) {
        val moves=when(level) { 1->FitnessLibrary.medium;2->FitnessLibrary.advanced;else->FitnessLibrary.beginner }
        val content=buildString {
            append("🎯 "+goals[goal]+" • "+levels[level]+"\n\n")
            moves.forEachIndexed { i,m->
                append("${i+1}. ${m.name} — ${m.sets}\n")
                append("Cách tập: ${m.guide}\n")
                append("Lưu ý: ${m.caution}\n\n")
            }
            append("Nghỉ 45–90 giây giữa các hiệp nếu cần. Bắt đầu nhẹ và ưu tiên kỹ thuật.\n")
        }
        AlertDialog.Builder(this).setTitle("🏋️ Buổi tập không dụng cụ")
            .setMessage(content).setPositiveButton("📆 Thêm lịch tập"){_,_->
                val current=LifeData.routines(this)
                val id=LifeData.nextId()
                val r=Routine(id,"Chibi Fitness • "+goals[goal],"🏃 Sức khỏe",18,0,25, (1 shl 1) or (1 shl 4) or (1 shl 6))
                LifeData.save(this,current+r)
                Toast.makeText(this,"Đã thêm lịch tập T2, T5, T7 lúc 18:00. Có thể sửa trong Lịch.",Toast.LENGTH_LONG).show()
            }.setNegativeButton("Đóng",null).show()
    }
}
