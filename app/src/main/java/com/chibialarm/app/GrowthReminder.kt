package com.chibialarm.app

import android.app.*
import android.content.*
import java.util.Calendar

object GrowthReminder {
    private const val REQUEST=839104
    fun schedule(ctx:Context,h:Int,m:Int) {
        val time=Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
            if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1)
        }
        val pi=PendingIntent.getBroadcast(ctx,REQUEST,Intent(ctx,GrowthReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am=ctx.getSystemService(AlarmManager::class.java)
        if(android.os.Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time.timeInMillis,pi)
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time.timeInMillis,pi)
    }
}
class GrowthReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val p=context.getSharedPreferences("growth",Context.MODE_PRIVATE)
        if(p.contains("remindHour")) GrowthReminder.schedule(context,p.getInt("remindHour",8),p.getInt("remindMinute",0))
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
