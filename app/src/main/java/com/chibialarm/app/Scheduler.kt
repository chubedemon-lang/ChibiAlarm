package com.chibialarm.app
import android.app.*
import android.content.*
import android.os.Build
import java.util.Calendar

object Scheduler {
    fun pending(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, id, Intent(ctx, AlarmReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, id: Int, h: Int, m: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            val data = ctx.getSharedPreferences("alarms", 0).getString(id.toString(), null)?.split(",")
            val mask = data?.getOrNull(2)?.toIntOrNull() ?: 127
            if (mask == 0) return
            var count = 0
            while ((timeInMillis <= System.currentTimeMillis() || (mask and (1 shl (get(Calendar.DAY_OF_WEEK)-1))) == 0) && count < 8) {
                add(Calendar.DAY_OF_YEAR, 1); count++
            }
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, id))
            return
        }
        val show = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), pending(ctx, id))
    }

    fun snooze(ctx: Context, id: Int, minutes: Int) {
        val requestId = id xor 0x40000000
        val pi = PendingIntent.getBroadcast(ctx, requestId,
            Intent(ctx, AlarmReceiver::class.java).putExtra("id", id).putExtra("snooze", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes.coerceIn(1,60)*60000L, pi)
    }

    fun cancel(ctx: Context, id: Int) {
        val requestId = id xor 0x40000000
        val snoozePi = PendingIntent.getBroadcast(ctx, requestId,
            Intent(ctx, AlarmReceiver::class.java).putExtra("id", id).putExtra("snooze", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ctx.getSystemService(AlarmManager::class.java).cancel(snoozePi)
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }
}
