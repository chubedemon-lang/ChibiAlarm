package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import java.util.*

class GoalsActivity:Activity() {
    private fun txt(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(84,53,96));setPadding(4,8,4,8)
    }
    private fun bg(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=30f }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1;setPadding(23,29,23,32);setBackgroundColor(Color.rgb(251,242,252)) }
        root.addView(txt("🏆 Thử thách bản thân",28f))
        root.addView(txt("Chọn một mục tiêu, duy trì từng ngày. Không cần tài khoản.",15f))
        root.addView(Button(this).apply { text="＋ Tạo thử thách 30 hoặc 90 ngày";setOnClickListener { addGoal() } })
        val goals=GoalData.goals(this)
        if(goals.isEmpty()) root.addView(txt("🐰 Chibi đang đợi mục tiêu đầu tiên của bạn!",18f))
        for(g in goals) {
            val card=LinearLayout(this).apply {
                orientation=1;setPadding(20,17,20,17)
                background=bg(Color.WHITE); elevation=3f
            }
            card.addView(txt("🌟 "+g.title,19f))
            val elapsed=GoalData.elapsed(g)
            val marked=g.checked.size.coerceAtMost(g.span)
            card.addView(txt("Ngày ${elapsed.coerceAtMost(g.span)} / ${g.span}   •   Đã hoàn thành $marked ngày",14f))
            card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=g.span;progress=marked
            })
            card.addView(txt("🎁 Thành tích: ${marked*20} XP   "+(if(elapsed>g.span) "• Đã hết thời hạn" else "• Còn ${g.span-elapsed} ngày"),14f))
            val actions=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
            actions.addView(Button(this).apply {
                text=if(LifeData.today() in g.checked) "✓ Đã hoàn thành hôm nay" else "✓ Check-in hôm nay"
                isEnabled=elapsed<=g.span
                setOnClickListener { GoalData.toggle(this@GoalsActivity,g);render() }
            },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(Button(this).apply { text="🗑";setOnClickListener {
                AlertDialog.Builder(this@GoalsActivity).setTitle("Xóa thử thách?")
                    .setMessage(g.title).setPositiveButton("Xóa"){_,_->
                        GoalData.save(this@GoalsActivity,GoalData.goals(this@GoalsActivity).filterNot { it.id==g.id })
                        render()
                    }.setNegativeButton("Hủy",null).show()
            } })
            card.addView(actions)
            root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=14 })
        }
        root.addView(txt("📊 Tổng điểm XP từ lịch hoạt động: ${LifeData.points(this)}",16f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun addGoal() {
        val field=EditText(this).apply { hint="Ví dụ: Tập thể dục đều đặn" }
        var days=30
        val panel=LinearLayout(this).apply { orientation=1;setPadding(24,10,24,8);addView(field) }
        panel.addView(RadioGroup(this).apply {
            orientation=RadioGroup.HORIZONTAL
            val r30=RadioButton(this@GoalsActivity).apply { id=3001;text="30 ngày";isChecked=true }
            val r90=RadioButton(this@GoalsActivity).apply { id=9001;text="90 ngày" }
            addView(r30);addView(r90)
            setOnCheckedChangeListener { _,checked->days=if(checked==9001) 90 else 30 }
        })
        val dialog=AlertDialog.Builder(this).setTitle("🎯 Mục tiêu mới")
            .setView(panel).setPositiveButton("Bắt đầu",null).setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title=field.text.toString().trim().take(80)
                if(title.isBlank()) {
                    field.error="Nhập tên mục tiêu"
                } else {
                    val goal=LifeGoal(LifeData.nextId(),title,days,System.currentTimeMillis(),emptySet())
                    GoalData.save(this,GoalData.goals(this)+goal)
                    dialog.dismiss();render()
                }
            }
        }
        dialog.show()
    }
}
