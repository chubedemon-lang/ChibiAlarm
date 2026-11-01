package com.chibialarm.app
import android.app.*
import android.content.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val record = AlarmTimeCodec.parse(
            context.getSharedPreferences("alarms", 0).all[id.toString()] as? String)
        if (record == null || record.weekdayMask == 0) {
            // A stale broadcast must not ring a deleted, disabled or corrupt alarm.
            Scheduler.cancel(context, id)
            return
        }
        // A broadcast already in flight may belong to a time/day setting the
        // user has since changed. Ignore it without cancelling the new alarm.
        // Legacy intents without a marker remain supported after app upgrade.
        val scheduledSignature = intent.getStringExtra("alarm_signature")
        if (scheduledSignature != null &&
            scheduledSignature != AlarmTimeCodec.signature(record)) return
        if (!intent.getBooleanExtra("snooze", false))
            Scheduler.schedule(context, id, record.hour, record.minute)
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
        RoutineReminder.rescheduleAll(context)
        FocusTimer.restore(context)
        ChibiWidget.updateAll(context)
        val growth=context.getSharedPreferences("growth",Context.MODE_PRIVATE)
        if(growth.contains("remindHour")) GrowthReminder.schedule(context,growth.getInt("remindHour",8),growth.getInt("remindMinute",0))
        for ((key, value) in context.getSharedPreferences("alarms", 0).all) {
            val id = key.toIntOrNull() ?: continue
            val record = AlarmTimeCodec.parse(value as? String)
            if (record == null) Scheduler.cancel(context, id)
            else Scheduler.schedule(context, id, record.hour, record.minute)
        }
    }
}
