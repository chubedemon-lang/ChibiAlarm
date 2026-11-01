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
    fun endTime(ctx:Context):Long=prefs(ctx).getLong("endTime",0L)
    fun sessions(ctx:Context):Int=prefs(ctx).getInt("sessions",0)
    @Synchronized fun start(ctx:Context,minutes:Int) {
        stop(ctx)
        val end=System.currentTimeMillis()+minutes.coerceIn(1,180)*60000L
        // Persist before scheduling so a killed process can restore the pending session.
        check(prefs(ctx).edit().putLong("endTime",end)
            .putBoolean("signedAlarm",true).commit()) { "Cannot save Focus timer" }
        schedule(ctx,end)
    }
    @Synchronized fun restore(ctx:Context) {
        val end=endTime(ctx)
        if(end==0L)return
        if(end<=System.currentTimeMillis()) complete(ctx) else {
            schedule(ctx,end)
            // Existing legacy sessions are upgraded without changing their end time.
            prefs(ctx).edit().putBoolean("signedAlarm",true).commit()
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
    @Synchronized fun stop(ctx:Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        prefs(ctx).edit().remove("endTime").commit()
    }
    /**
     * Ignore a delayed alarm from an earlier Focus session after the user restarted
     * the timer. Old pre-upgrade PendingIntents lack a marker and are accepted only
     * for an unchanged legacy session, never after a newly started session.
     */
    @Synchronized fun completeFromAlarm(ctx:Context,intent:Intent):Boolean {
        val current = endTime(ctx)
        if (current == 0L) return false
        val expected = intent.getLongExtra(EXTRA_EXPECTED_END,0L)
        if (expected != current &&
            (expected != 0L || prefs(ctx).getBoolean("signedAlarm",false))) return false
        return complete(ctx)
    }

    /** Serialize receiver and Activity completions; never award one session twice. */
    @Synchronized fun complete(ctx:Context):Boolean {
        val current=endTime(ctx)
        if(current==0L || current>System.currentTimeMillis())return false
        val dayKey = "sessions:" + LifeData.today()
        val currentDay = prefs(ctx).getInt(dayKey, 0)
        // commit() is intentional: durable reward state before notifying the user.
        val saved = prefs(ctx).edit().remove("endTime")
            .putInt("sessions", if (sessions(ctx) == Int.MAX_VALUE) Int.MAX_VALUE else sessions(ctx) + 1)
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
        val nm=ctx.getSystemService(NotificationManager::class.java)
        val channel="chibi_focus"
        nm.createNotificationChannel(NotificationChannel(channel,"Hoàn thành tập trung",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(ctx,47821,Intent(ctx,FocusActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(47821,Notification.Builder(ctx,channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🎉 Hoàn thành phiên tập trung!")
            .setContentText("Chibi khen bạn! Đứng dậy vận động và nghỉ ngơi nhé.")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
