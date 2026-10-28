package com.chibialarm.app

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

/** Alarm permission and ringtone setup wizard for recent Android devices. */
class AlarmSettingsActivity:Activity() {
    private val preferences by lazy { getSharedPreferences("settings",0) }
    private val PICK_RING=9021
    private lateinit var root:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);draw() }
    override fun onResume() { super.onResume();if(::root.isInitialized)draw() }
    private fun draw() {
        root=LinearLayout(this).apply { orientation=1;setPadding(25,28,25,25);setBackgroundColor(Color.rgb(255,240,249)) }
        root.addView(TextView(this).apply { text="⏰ Thiết lập báo thức";textSize=28f;setTextColor(Color.rgb(94,56,112)) })
        root.addView(TextView(this).apply {
            text="Kiểm tra quyền để Chibi báo thức đáng tin cậy nhất có thể trên Android 12–16."
            textSize=15f;setPadding(0,8,0,18)
        })
        val alarm=getSystemService(AlarmManager::class.java)
        val exact=Build.VERSION.SDK_INT<31 || alarm.canScheduleExactAlarms()
        root.addView(TextView(this).apply { text=(if(exact)"✅" else "⚠️")+" Báo thức chính xác";textSize=18f })
        root.addView(Button(this).apply { text="Mở quyền báo thức chính xác";setOnClickListener {
            if(Build.VERSION.SDK_INT>=31) try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
            }catch(_:Exception){ Toast.makeText(this@AlarmSettingsActivity,"Không mở được cài đặt hệ thống",Toast.LENGTH_LONG).show() }
        } })
        val nm=getSystemService(NotificationManager::class.java)
        val notificationGranted=Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
        root.addView(TextView(this).apply {
            text=(if(notificationGranted)"✅" else "⚠️")+" Thông báo";textSize=18f
        })
        root.addView(Button(this).apply { text="Cho phép thông báo";setOnClickListener {
            if(Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),73)
        } })
        if(Build.VERSION.SDK_INT>=34) {
            val allowed=nm.canUseFullScreenIntent()
            root.addView(TextView(this).apply {
                text=(if(allowed)"✅" else "⚠️")+" Hiển thị khi khóa màn hình";textSize=18f
            })
            root.addView(Button(this).apply { text="Mở quyền hiển thị toàn màn hình";setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"))) }
                catch(_:Exception){Toast.makeText(this@AlarmSettingsActivity,"Không mở được cài đặt",Toast.LENGTH_LONG).show()}
            } })
        }
        root.addView(Button(this).apply { text="🎵 Chọn nhạc chuông từ điện thoại";setOnClickListener {
            val intent=Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Chọn nhạc báo thức")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
            }
            try { startActivityForResult(intent,PICK_RING) }
            catch(_:Exception){Toast.makeText(this@AlarmSettingsActivity,"Không có trình chọn nhạc chuông",Toast.LENGTH_LONG).show()}
        } })
        root.addView(CheckBox(this).apply {
            text="🔊 Tăng âm lượng từ từ (khoảng 20 giây)"
            textSize=16f;isChecked=preferences.getBoolean("fade_in",true)
            setOnCheckedChangeListener { _,value->preferences.edit().putBoolean("fade_in",value).apply() }
        })
        root.addView(Button(this).apply { text="🧪 Thử màn hình báo thức ngay";setOnClickListener {
            startActivity(Intent(this@AlarmSettingsActivity,RingActivity::class.java).putExtra("id",-200))
        } })
        root.addView(TextView(this).apply {
            text="Nếu không bật quyền chính xác hoặc toàn màn hình, Android có thể làm trễ nhắc việc hoặc chỉ hiển thị thông báo. Kết quả còn tùy chính sách tiết kiệm pin của hãng."
            textSize=13f;setPadding(0,18,0,4)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==PICK_RING && resultCode==RESULT_OK) {
            val uri=if(Build.VERSION.SDK_INT>=33)
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,Uri::class.java)
            else @Suppress("DEPRECATION") (data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri)
            preferences.edit().putString("ringtone",uri?.toString() ?: "").apply()
            Toast.makeText(this,"Đã lưu nhạc chuông",Toast.LENGTH_SHORT).show()
        }
    }
}
