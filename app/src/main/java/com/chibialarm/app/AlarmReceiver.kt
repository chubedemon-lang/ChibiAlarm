package com.chibialarm.app
import android.app.*
import android.content.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val fields = context.getSharedPreferences("alarms", 0).getString(id.toString(), null)?.split(",")
        if (!intent.getBooleanExtra("snooze", false) && fields != null && fields.size >= 2) Scheduler.schedule(context, id, fields[0].toInt(), fields[1].toInt())
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "alarms"
        manager.createNotificationChannel(NotificationChannel(channel, "Báo thức", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, null)
        })
        val full = PendingIntent.getActivity(context, id, Intent(context, RingActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🐰 Dậy thôi!")
            .setContentText("Chibi đang gọi bạn dậy ☀️")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val growth=context.getSharedPreferences("growth",Context.MODE_PRIVATE)
        if(growth.contains("remindHour")) GrowthReminder.schedule(context,growth.getInt("remindHour",8),growth.getInt("remindMinute",0))
        for ((key, value) in context.getSharedPreferences("alarms", 0).all) {
            val id = key.toIntOrNull() ?: continue
            val p = (value as? String)?.split(",") ?: continue
            if (p.size >= 2) Scheduler.schedule(context, id, p[0].toInt(), p[1].toInt())
        }
    }
}
