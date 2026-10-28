package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

/** Local, offline focus timer. Runs as an elapsed realtime deadline while the app is open. */
class FocusActivity:Activity() {
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var deadline=0L
    private var running=false
    private lateinit var clock:TextView
    private val tick=object:Runnable {
        override fun run() {
            if(!running)return
            val remaining=(deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            clock.text=String.format(Locale.US,"%02d:%02d",remaining/60000,(remaining/1000)%60)
            if(remaining==0L) {
                running=false
                val p=getSharedPreferences("focus",0)
                p.edit().putInt("sessions",p.getInt("sessions",0)+1).apply()
                AlertDialog.Builder(this@FocusActivity).setTitle("🎉 Hoàn thành!")
                    .setMessage("Bạn đã tập trung xong một phiên. Nghỉ ngơi nhé!").setPositiveButton("Tuyệt!",null).show()
            } else handler.postDelayed(this,500)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=1;setPadding(28,40,28,28);setBackgroundColor(Color.rgb(245,239,255)) }
        root.addView(TextView(this).apply { text="🍅 Chibi Focus";textSize=29f })
        root.addView(TextView(this).apply { text="Chọn một phiên tập trung không bị xao nhãng";textSize=16f })
        clock=TextView(this).apply { text="25:00";textSize=56f;gravity=android.view.Gravity.CENTER;setPadding(0,48,0,36) }
        root.addView(clock)
        listOf(15,25,45,60).forEach { mins ->
            root.addView(Button(this).apply { text="▶ Tập trung $mins phút";setOnClickListener {
                running=true;deadline=android.os.SystemClock.elapsedRealtime()+mins*60000L
                handler.removeCallbacks(tick);handler.post(tick)
            } })
        }
        root.addView(Button(this).apply { text="■ Dừng phiên";setOnClickListener {
            running=false;handler.removeCallbacks(tick);clock.text="00:00"
        } })
        root.addView(TextView(this).apply {
            text="🏆 Số phiên hoàn thành: "+getSharedPreferences("focus",0).getInt("sessions",0)
            textSize=17f
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onDestroy() { handler.removeCallbacks(tick);super.onDestroy() }
}
