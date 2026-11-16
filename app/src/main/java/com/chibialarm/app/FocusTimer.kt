package com.chibialarm.app

import android.app.*
import android.content.*
import android.os.Build

/** Persistent focus completion alarm: works after Activity closes, subject to Android scheduling permissions. */
object FocusTimer {
    private const val CODE=47821
    private const val EXTRA_EXPECTED_END = "focus_expected_end"
    private fun pi(ctx:Context,end:Long=0L)=PendingIntent.getBroadcast(ctx,CODE,
        Intent(ctx,FocusDoneReceiver::class.java).putExtra(EXTRA_EXPECTED_END,end),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun prefs(ctx:Context)=ctx.getSharedPreferences("focus",Context.MODE_PRIVATE)
    // A malformed legacy preference must not crash boot restore or silently
    // overwrite historical session counters. Keep damaged values for recovery.
    fun endTime(ctx:Context):Long = try {
        prefs(ctx).getLong("endTime", 0L).takeIf { it > 0L } ?: 0L
    } catch (_: ClassCastException) { 0L }
    private fun validCount(ctx:Context, key:String):Int? = try {
        prefs(ctx).getInt(key, 0).takeIf { it >= 0 }
    } catch (_: ClassCastException) { null }
    fun sessions(ctx:Context):Int = validCount(ctx, "sessions") ?: 0
    /** Invalid legacy day counters must never crash the RPG dashboard or award quest progress. */
    fun sessionsOnDay(ctx:Context, day:String):Int {
        if (runCatching { java.time.LocalDate.parse(day) }.isFailure) return 0
        return validCount(ctx, "sessions:$day") ?: 0
    }
    internal fun canPostCompletionNotification(sdk:Int, permissionGranted:Boolean):Boolean =
        sdk < 33 || permissionGranted
    @Synchronized fun start(ctx:Context,minutes:Int) {
        val end=System.currentTimeMillis()+minutes.coerceIn(1,180)*60000L
        // Do not cancel the previous timer before the replacement is durable.
        // Scheduling the same PendingIntent replaces its old AlarmManager entry.
        // If the write fails, the original session and alarm remain intact.
        check(prefs(ctx).edit().putLong("endTime",end)
            .putBoolean("signedAlarm",true).commit()) { "Cannot save Focus timer" }
        schedule(ctx,end)
    }
    @Synchronized fun restore(ctx:Context) {
        val end=endTime(ctx)
        if(end==0L)return
        if(end<=System.currentTimeMillis()) complete(ctx) else {
            // Persist the strict receiver marker before installing a new alarm.
            // Failed writes must not weaken stale-broadcast protection.
            if (!prefs(ctx).edit().putBoolean("signedAlarm",true).commit()) return
            schedule(ctx,end)
        }
    }
    private fun schedule(ctx:Context,end:Long) {
        val am=ctx.getSystemService(AlarmManager::class.java)
        val pending = pi(ctx,end)
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,pending)
        } else {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,pending)
            } catch (_: SecurityException) {
                // The permission can change while a Focus session is being scheduled.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,pending)
            }
        }
    }
    @Synchronized fun stop(ctx:Context): Boolean {
        // Keep the old scheduled alarm if storage refuses to clear the session.
        if (!prefs(ctx).edit().remove("endTime").commit()) return false
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        return true
    }
    /**
     * Ignore a delayed alarm from an earlier Focus session after the user restarted
     * the timer. Old pre-upgrade PendingIntents lack a marker and are accepted only
     * for an unchanged legacy session, never after a newly started session.
     */
    @Synchronized fun completeFromAlarm(ctx:Context,intent:Intent):Boolean {
        val current = endTime(ctx)
        if (current == 0L) return false
        // A malformed broadcast extra or legacy marker must fail closed rather
        // than crash the receiver or authorize an unsigned stale completion.
        val expected = try {
            intent.getLongExtra(EXTRA_EXPECTED_END,0L)
        } catch (_: ClassCastException) { return false }
        val marker = prefs(ctx).all["signedAlarm"]
        val requiresSignature = marker != null && marker != false
        if (expected != current &&
            (expected != 0L || requiresSignature)) return false
        return complete(ctx)
    }

    /** Serialize receiver and Activity completions; never award one session twice. */
    @Synchronized fun complete(ctx:Context):Boolean {
        val current=endTime(ctx)
        if(current==0L || current>System.currentTimeMillis())return false
        val dayKey = "sessions:" + LifeData.today()
        val currentDay = validCount(ctx, dayKey) ?: return false
        val total = validCount(ctx, "sessions") ?: return false
        // Do not destroy malformed/negative historical counts to award a new one.
        // commit() is intentional: durable reward state before notifying the user.
        val saved = prefs(ctx).edit().remove("endTime")
            .putInt("sessions", if (total == Int.MAX_VALUE) Int.MAX_VALUE else total + 1)
            .putInt(dayKey, if (currentDay == Int.MAX_VALUE) Int.MAX_VALUE else currentDay + 1)
            .commit()
        if (!saved) return false
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        return true
    }
}
class FocusDoneReceiver:BroadcastReceiver() {
    override fun onReceive(ctx:Context,intent:Intent) {
        if(!FocusTimer.completeFromAlarm(ctx,intent))return
        // The durable session reward is independent of notification permission.
        // Android 13+ may deny/revoke POST_NOTIFICATIONS at any time.
        if (!FocusTimer.canPostCompletionNotification(Build.VERSION.SDK_INT,
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED)) return
        val nm=ctx.getSystemService(NotificationManager::class.java)
        val channel="chibi_focus"
        nm.createNotificationChannel(NotificationChannel(channel,"Hoàn thành tập trung",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(ctx,47821,Intent(ctx,FocusActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            nm.notify(47821,Notification.Builder(ctx,channel)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("🎉 Hoàn thành phiên tập trung!")
                .setContentText("Chibi khen bạn! Đứng dậy vận động và nghỉ ngơi nhé.")
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
            // Never crash a receiver after recording the completed session.
        }
    }
}
