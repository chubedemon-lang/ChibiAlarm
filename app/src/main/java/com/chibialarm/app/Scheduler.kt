package com.chibialarm.app
import android.app.*
import android.content.*
import android.os.Build
import java.util.Calendar

/**
 * Legacy alarm records: "hour,minute[,weekdayMask]". Two-field records
 * remain readable for users upgrading from older versions.
 * A zero mask is disabled; malformed records must never trigger an alarm.
 */
internal object AlarmTimeCodec {
    data class Entry(val hour: Int, val minute: Int, val weekdayMask: Int)

    /** Version marker for a scheduled broadcast; never contains account data. */
    fun signature(entry: Entry): String =
        "${entry.hour}:${entry.minute}:${entry.weekdayMask}"

    fun parse(raw: String?): Entry? {
        val fields = raw?.split(",") ?: return null
        if (fields.size !in 2..3) return null
        val hour = fields[0].trim().toIntOrNull() ?: return null
        val minute = fields[1].trim().toIntOrNull() ?: return null
        val mask = if (fields.size == 3)
            fields[2].trim().toIntOrNull() ?: return null else 127
        if (hour !in 0..23 || minute !in 0..59 || mask !in 0..127) return null
        return Entry(hour, minute, mask)
    }
}

object Scheduler {
    internal fun pending(ctx: Context, id: Int, record: AlarmTimeCodec.Entry? = null): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("id", id)
        if (record != null) intent.putExtra("alarm_signature", AlarmTimeCodec.signature(record))
        return PendingIntent.getBroadcast(ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, id: Int, h: Int, m: Int) {
        // Use the latest persisted values even when a delayed caller supplies
        // an older time. Keep the legacy h/m signature for existing call sites.
        val record = AlarmTimeCodec.parse(
            ctx.getSharedPreferences("alarms", 0).all[id.toString()] as? String)
        if (record == null || record.weekdayMask == 0) {
            // Also cancel a previously scheduled alarm when the user unchecks
            // every day, or an old/corrupted preference cannot be restored.
            cancel(ctx, id)
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, record.hour); set(Calendar.MINUTE, record.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            val mask = record.weekdayMask
            var count = 0
            while ((timeInMillis <= System.currentTimeMillis() || (mask and (1 shl (get(Calendar.DAY_OF_WEEK)-1))) == 0) && count < 8) {
                add(Calendar.DAY_OF_YEAR, 1); count++
            }
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val alarmIntent = pending(ctx, id, record)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent)
            return
        }
        val show = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), alarmIntent)
        } catch (_: SecurityException) {
            // Exact-alarm permission can be revoked between checking and scheduling.
            // Keep the alarm registered as an inexact fallback rather than crashing.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent)
        }
    }

    fun snooze(ctx: Context, id: Int, minutes: Int) {
        val record = AlarmTimeCodec.parse(
            ctx.getSharedPreferences("alarms", 0).all[id.toString()] as? String)
            ?: return
        if (record.weekdayMask == 0) return
        val requestId = id xor 0x40000000
        val pi = PendingIntent.getBroadcast(ctx, requestId,
            Intent(ctx, AlarmReceiver::class.java).putExtra("id", id)
                .putExtra("snooze", true)
                .putExtra("alarm_signature", AlarmTimeCodec.signature(record)),
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
