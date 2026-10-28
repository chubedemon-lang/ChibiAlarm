package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.util.Locale

class FocusActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var clock:TextView
    private lateinit var score:TextView
    private val tick=object:Runnable {
        override fun run() {
            val end=FocusTimer.endTime(this@FocusActivity)
            val remain=(end-System.currentTimeMillis()).coerceAtLeast(0L)
            if(end!=0L && remain==0L) {
                if(FocusTimer.complete(this@FocusActivity)) {
                    AlertDialog.Builder(this@FocusActivity).setTitle("🎉 Hoàn thành!")
                        .setMessage("Bạn vừa hoàn thành một phiên tập trung. Giỏi lắm!")
                        .setPositiveButton("Tuyệt!",null).show()
                }
            }
            val duration=if(FocusTimer.endTime(this@FocusActivity)==0L) 0L else remain
            clock.text=String.format(Locale.US,"%02d:%02d",duration/60000,(duration/1000)%60)
            score.text="🏆 Tổng phiên hoàn thành: "+FocusTimer.sessions(this@FocusActivity)
            handler.postDelayed(this,600)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=1;setPadding(28,37,28,25);setBackgroundColor(Color.rgb(245,239,255)) }
        root.addView(TextView(this).apply { text="🍅 Chibi Focus PRO";textSize=28f;setTextColor(Color.rgb(99,61,113)) })
        root.addView(TextView(this).apply { text="Hẹn giờ tập trung • Nhắc khi hoàn thành kể cả khi rời app";textSize=15f })
        clock=TextView(this).apply {
            text="00:00";textSize=56f;gravity=android.view.Gravity.CENTER;setPadding(0,38,0,30)
        }
        root.addView(clock)
        listOf(15,25,45,60).forEach { mins ->
            root.addView(Button(this).apply { text="▶ Bắt đầu $mins phút";setOnClickListener {
                FocusTimer.start(this@FocusActivity,mins)
                Toast.makeText(this@FocusActivity,"Chibi nhắc sau $mins phút!",Toast.LENGTH_SHORT).show()
            } })
        }
        root.addView(Button(this).apply { text="■ Hủy phiên hiện tại";setOnClickListener {
            FocusTimer.stop(this@FocusActivity)
            clock.text="00:00"
        } })
        score=TextView(this).apply { textSize=17f }
        root.addView(score)
        root.addView(TextView(this).apply {
            text="Trên Android 12+, để nhắc đúng thời điểm cần quyền báo thức chính xác. Nếu chưa cấp, hệ thống có thể thông báo trễ."
            textSize=13f
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onResume() { super.onResume();handler.removeCallbacks(tick);handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick);super.onPause() }
}
