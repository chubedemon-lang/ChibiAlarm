package com.chibialarm.app

import android.app.*
import android.content.*
import java.util.Calendar

object GrowthReminder {
    private const val REQUEST=839104

    /** Read legacy settings without overwriting malformed or partial preferences. */
    internal fun savedTime(ctx: Context): Pair<Int, Int>? {
        val stored = ctx.getSharedPreferences("growth", Context.MODE_PRIVATE).all
        val hour = stored["remindHour"] as? Int ?: return null
        val minute = (stored["remindMinute"] ?: 0) as? Int ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    /** Reschedule only a valid, explicitly configured reminder. */
    fun restore(ctx: Context): Boolean {
        val (hour, minute) = savedTime(ctx) ?: return false
        schedule(ctx, hour, minute)
        return true
    }

    fun schedule(ctx: Context, h: Int, m: Int) {
        require(h in 0..23 && m in 0..59) { "Invalid growth reminder time" }
        val time=Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
            if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1)
        }
        val pi=PendingIntent.getBroadcast(ctx,REQUEST,Intent(ctx,GrowthReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am=ctx.getSystemService(AlarmManager::class.java)
        if(android.os.Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time.timeInMillis,pi)
        } else {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time.timeInMillis,pi)
            } catch (_: SecurityException) {
                // Exact-alarm permission can be revoked between checking and scheduling.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time.timeInMillis,pi)
            }
        }
    }
}
class GrowthReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        // Ignore stale broadcasts when a reminder was removed or its legacy
        // settings are malformed. Never display an unrequested notification.
        if (!GrowthReminder.restore(context)) return
        val manager=context.getSystemService(NotificationManager::class.java)
        val channel="chibi_growth"
        manager.createNotificationChannel(NotificationChannel(channel,"Nhắc thói quen",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,839104,Intent(context,GrowthActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(839104,Notification.Builder(context,channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🌱 Đến giờ phát triển bản thân!")
            .setContentText("Chibi nhắc bạn hoàn thành mục tiêu hôm nay ✨")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
