package com.chibialarm.app
import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.view.*
import android.widget.*
import java.util.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        render()
    }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = 1; setPadding(32,50,32,20); setBackgroundColor(Color.rgb(255,239,248)) }
        root.addView(TextView(this).apply { text = "🐰 Chibi Alarm 🌸"; textSize = 30f; setTextColor(Color.rgb(139,65,120)) })
        root.addView(TextView(this).apply { text = "Dậy thôi, mặt trời đang đợi bạn! ☀️"; textSize = 16f })
        root.addView(Button(this).apply { text = "＋ Thêm báo thức"; setOnClickListener { createAlarm() } })
        root.addView(Button(this).apply {
            text = "⚙️ Cho phép báo thức chính xác"
            setOnClickListener { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        })
        list = LinearLayout(this).apply { orientation = 1 }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        refresh()
    }
    private fun createAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            getSharedPreferences("alarms", 0).edit().putString(id.toString(), "$hour,$minute,true").apply()
            Scheduler.schedule(this, id, hour, minute)
            refresh()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }
    private fun refresh() {
        list.removeAllViews()
        val prefs = getSharedPreferences("alarms", 0)
        for ((key, value) in prefs.all) {
            val id = key.toIntOrNull() ?: continue
            val parts = (value as? String)?.split(",") ?: continue
            if (parts.size < 3) continue
            val h = parts[0].toIntOrNull() ?: continue
            val m = parts[1].toIntOrNull() ?: continue
            val row = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = String.format(Locale.getDefault(), "🐻 %02d:%02d", h, m)
                textSize = 25f
                setTextColor(Color.rgb(115,57,103))
                layoutParams = LinearLayout.LayoutParams(0, 110, 1f)
            })
            row.addView(Button(this).apply {
                text = "Xóa"
                setOnClickListener { Scheduler.cancel(this@MainActivity, id); prefs.edit().remove(key).apply(); refresh() }
            })
            list.addView(row)
        }
    }
}
