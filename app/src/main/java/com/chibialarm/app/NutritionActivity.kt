package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import java.util.Locale

/**
 * Simple food literacy and journaling. It does NOT prescribe calories, macros, weight loss
 * targets, diagnose illness, or infer energy requirements from limited user data.
 */
class NutritionActivity:Activity() {
    private val pref by lazy { getSharedPreferences("chibi_nutrition",0) }
    private val styles=arrayOf("🍚 Cơm nhà cân bằng","💰 Tiết kiệm","🥬 Nhiều thực vật","💪 Hỗ trợ vận động")
    private val restrictions=arrayOf("Không dùng sữa","Tránh trứng","Tránh hải sản","Tránh đậu phộng")
    private val meals=arrayOf("Bữa sáng","Bữa trưa","Bữa tối","Bữa phụ khi đói")
    private fun date()=LifeData.today()
    private fun txt(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(73,59,80));setPadding(7,8,7,8)
    }
    private fun block(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=28f }
    override fun onCreate(savedInstanceState:Bundle?) {super.onCreate(savedInstanceState);render()}
    private fun render() {
        val root=LinearLayout(this).apply {
            orientation=1;setPadding(22,26,22,32);setBackgroundColor(Color.rgb(250,246,236))
        }
        val header=LinearLayout(this).apply {
            orientation=1;setPadding(20,18,20,18);background=block(Color.rgb(230,248,224))
        }
        header.addView(txt("🥗 Chibi Nutrition",28f))
        header.addView(txt("Gợi ý bữa ăn Việt • nhật ký trong ngày • miễn phí và ngoại tuyến",14f))
        root.addView(header)
        root.addView(txt("🍽 Phong cách ăn uống",20f))
        val mode=Spinner(this).apply {
            adapter=ArrayAdapter(this@NutritionActivity,android.R.layout.simple_spinner_dropdown_item,styles)
            setSelection(pref.getInt("style",0).coerceIn(0,styles.lastIndex))
        }
        root.addView(mode)
        root.addView(txt("⚠️ Thành phần cần tránh (theo lựa chọn của bạn)",18f))
        val restrict=BooleanArray(restrictions.size) { pref.getBoolean("avoid$it",false) }
        restrictions.forEachIndexed { idx,label ->
            root.addView(CheckBox(this).apply {
                text=label;isChecked=restrict[idx]
                setOnCheckedChangeListener { _,checked->
                    restrict[idx]=checked;pref.edit().putBoolean("avoid$idx",checked).apply()
                }
            })
        }
        val menuLabel=txt("",15f)
        root.addView(Button(this).apply {
            text="📅 Xem gợi ý bữa ăn cho hôm nay"
            setOnClickListener {
                val selected=mode.selectedItemPosition
                pref.edit().putInt("style",selected).apply()
                menuLabel.text=menu(selected,restrict)
            }
        })
        menuLabel.text=menu(mode.selectedItemPosition,restrict)
        root.addView(menuLabel)
        root.addView(txt("✅ Nhật ký ăn uống hôm nay",21f))
        root.addView(txt("Ghi nhận bữa ăn để theo dõi nếp sinh hoạt, không chấm điểm thực phẩm tốt/xấu.",14f))
        val today=date()
        meals.forEachIndexed { i,name->
            root.addView(CheckBox(this).apply {
                text=name;isChecked=pref.getBoolean("done:$today:$i",false)
                setOnCheckedChangeListener { _,checked->
                    pref.edit().putBoolean("done:$today:$i",checked).apply()
                }
            })
        }
        val note=EditText(this).apply {
            hint="Ví dụ: Cơm, cá, rau luộc; ăn thoải mái..."
            minLines=2;maxLines=5;setText(pref.getString("note:$today",""))
        }
        root.addView(note)
        root.addView(Button(this).apply {
            text="💾 Lưu ghi chú bữa ăn"
            setOnClickListener {
                pref.edit().putString("note:$today",note.text.toString().take(900)).apply()
                Toast.makeText(this@NutritionActivity,"Đã lưu trên điện thoại",Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(txt("📊 Bảy ngày gần đây",20f))
        val calendar=java.util.Calendar.getInstance()
        val formatter=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US)
        repeat(7) {
            val day=formatter.format(calendar.time)
            val amount=(0..3).count { i -> pref.getBoolean("done:$day:$i",false) }
            root.addView(txt("$day • $amount/4 mục đã ghi nhận",13f))
            calendar.add(java.util.Calendar.DAY_OF_YEAR,-1)
        }
        root.addView(txt("📚 Nguyên tắc được tham khảo",19f))
        root.addView(txt("WHO khuyến khích thực phẩm đa dạng, đủ chất, nhiều rau quả và đậu; giảm bớt đồ uống nhiều đường và muối. Với người trên 10 tuổi, WHO khuyến nghị ít nhất 400 g rau quả/ngày; đây không phải chỉ tiêu bắt buộc từng bữa. Không nên dùng thực đơn này để điều trị bệnh.",14f))
        root.addView(Button(this).apply {
            text="🌐 Đọc khuyến nghị WHO"
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.who.int/news-room/fact-sheets/detail/healthy-diet")))
                } catch(_:Exception){}
            }
        })
        root.addView(txt("Lưu ý: đây là món ăn gợi ý, không đảm bảo không có dị nguyên do nguyên liệu và cách chế biến khác nhau. Người có dị ứng nặng, bệnh thận, tiểu đường, đang mang thai hoặc chế độ ăn điều trị cần tư vấn chuyên môn. Không dùng Chibi để ép ăn kiêng khắc nghiệt.",12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun menu(style:Int,avoid:BooleanArray):String {
        fun allowed(s:String):Boolean {
            val t=s.lowercase(Locale.ROOT)
            if(avoid[0] && listOf("sữa","phô mai","sữa chua").any{it in t})return false
            if(avoid[1] && "trứng" in t)return false
            if(avoid[2] && listOf("cá","tôm","mực","hải sản").any { it in t })return false
            if(avoid[3] && listOf("đậu phộng","lạc","bơ đậu").any {it in t})return false
            return true
        }
        val breakfasts=listOf(
            "Bánh mì nguyên cám + trứng + cà chua",
            "Cháo yến mạch + chuối + sữa chua",
            "Bún rau với đậu phụ",
            "Bánh mì + thịt nạc + dưa leo",
            "Khoai lang + đậu phụ + trái cây")
        val lunches=listOf(
            "Cơm + cá hấp + rau luộc + canh",
            "Cơm + ức gà + rau xào ít dầu",
            "Cơm + đậu phụ sốt cà + cải xanh",
            "Bún + thịt nạc + nhiều rau",
            "Cơm + trứng + rau muống + canh bí")
        val dinners=listOf(
            "Cơm + thịt nạc + canh bí + rau",
            "Cơm + đậu phụ + nấm + rau luộc",
            "Khoai + cá hấp + rau xanh",
            "Bún rau + đậu phụ + trái cây",
            "Cơm + gà luộc + rau củ")
        val snacks=listOf("Chuối hoặc ổi","Sữa chua không đường + trái cây","Trái cây theo mùa",
            "Bắp luộc","Một ít hạt không tẩm muối nếu phù hợp")
        val dayIndex=java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        fun choose(source:List<String>,fallback:String,shift:Int):String {
            val items=source.filter(::allowed)
            if(items.isEmpty())return fallback
            return items[(dayIndex+shift+style)%items.size]
        }
        val lean=style==2
        val cheap=style==1
        val title=when(style) {
            1->"💰 Ưu tiên nguyên liệu phổ biến, dễ mua"
            2->"🥬 Ưu tiên món có nguồn đạm thực vật"
            3->"💪 Đạm trong mỗi bữa và ăn đủ theo nhu cầu vận động"
            else->"🍚 Ăn đa dạng, cân bằng và linh hoạt"
        }
        val breakfast=if(lean) choose(listOf("Bún rau với đậu phụ","Khoai lang + đậu phụ + trái cây"),"Khoai lang + trái cây",1)
            else choose(breakfasts,"Cháo yến mạch + trái cây",1)
        val lunch=if(cheap)choose(listOf("Cơm + đậu phụ sốt cà + cải xanh","Cơm + trứng + rau muống + canh bí"),
            "Cơm + đậu phụ + rau",2)
            else if(lean)choose(listOf("Cơm + đậu phụ sốt cà + cải xanh","Bún + đậu phụ + nhiều rau"),"Cơm + rau + đậu phụ",2)
            else choose(lunches,"Cơm + đậu phụ + rau",2)
        val dinner=if(lean)choose(listOf("Cơm + đậu phụ + nấm + rau luộc","Bún rau + đậu phụ + trái cây"),
            "Khoai + rau + đậu phụ",3)
            else choose(dinners,"Cơm + rau + thịt nạc hoặc đậu phụ",3)
        val snack=choose(snacks,"Trái cây theo mùa",4)
        return "🥗 $title\n\n🌞 SÁNG: $breakfast\n\n🍱 TRƯA: $lunch\n\n🌙 TỐI: $dinner\n\n🍎 KHI ĐÓI: $snack\n\n💧 Uống nước đều theo cảm giác khát và mức vận động. Ăn phần phù hợp nhu cầu thực tế. Kiểm tra thành phần dị ứng của từng món."
    }
}
