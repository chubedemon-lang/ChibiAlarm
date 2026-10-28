package com.chibialarm.app

import android.app.*
import android.content.*
import android.os.Build

/** Persistent focus completion alarm: works after Activity closes, subject to Android scheduling permissions. */
object FocusTimer {
    private const val CODE=47821
    private fun pi(ctx:Context)=PendingIntent.getBroadcast(ctx,CODE,
        Intent(ctx,FocusDoneReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun prefs(ctx:Context)=ctx.getSharedPreferences("focus",Context.MODE_PRIVATE)
    fun endTime(ctx:Context):Long=prefs(ctx).getLong("endTime",0L)
    fun sessions(ctx:Context):Int=prefs(ctx).getInt("sessions",0)
    fun start(ctx:Context,minutes:Int) {
        stop(ctx)
        val end=System.currentTimeMillis()+minutes.coerceIn(1,180)*60000L
        prefs(ctx).edit().putLong("endTime",end).apply()
        schedule(ctx,end)
    }
    fun restore(ctx:Context) {
        val end=endTime(ctx)
        if(end==0L)return
        if(end<=System.currentTimeMillis()) complete(ctx) else schedule(ctx,end)
    }
    private fun schedule(ctx:Context,end:Long) {
        val am=ctx.getSystemService(AlarmManager::class.java)
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,pi(ctx))
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,pi(ctx))
    }
    fun stop(ctx:Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        prefs(ctx).edit().remove("endTime").apply()
    }
    fun complete(ctx:Context):Boolean {
        val current=endTime(ctx)
        if(current==0L || current>System.currentTimeMillis())return false
        prefs(ctx).edit().remove("endTime").putInt("sessions",sessions(ctx)+1).apply()
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        return true
    }
}
class FocusDoneReceiver:BroadcastReceiver() {
    override fun onReceive(ctx:Context,intent:Intent) {
        if(!FocusTimer.complete(ctx))return
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
