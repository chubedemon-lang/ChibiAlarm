package com.chibialarm.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Routine(val id:Int,val title:String,val category:String,val hour:Int,val minute:Int,val duration:Int,val repeatMask:Int=127) {
    fun label():String=String.format(Locale.US,"%02d:%02d",hour,minute)
}
object LifeData {
    private const val FILE="chibi_life_v12"
    fun today():String=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private fun prefs(ctx:Context)=ctx.getSharedPreferences(FILE,Context.MODE_PRIVATE)
    fun routines(ctx:Context):List<Routine> {
        // A single damaged legacy record must not hide every valid routine.
        // Read-only recovery: never overwrite the original preference on parse failure.
        return try {
            val data=JSONArray(prefs(ctx).getString("routines","[]") ?: "[]")
            val recovered=mutableListOf<Routine>()
            for (i in 0 until data.length()) {
                val o=data.optJSONObject(i) ?: continue
                try {
                    recovered.add(Routine(o.getInt("id"),o.getString("title"),
                        o.optString("category","📚 Học tập"),o.getInt("hour"),
                        o.getInt("minute"),o.optInt("duration",30),
                        o.optInt("repeatMask",127)))
                } catch (_:Exception) {
                    // Skip only this record; preserve its raw JSON for diagnostics.
                }
            }
            recovered.sortedWith(compareBy({it.hour},{it.minute}))
        } catch (_:Exception) { emptyList() }
    }
    fun save(ctx:Context,routines:List<Routine>) {
        // Cancel alarms for removed IDs before replacing the stored routine list.
        // Rescheduling only the new list otherwise leaves orphan reminders behind.
        val retainedIds = routines.map { it.id }.toSet()
        this.routines(ctx).filterNot { it.id in retainedIds }
            .forEach { RoutineReminder.cancel(ctx, it) }
        val array=JSONArray()
        routines.forEach { r ->
            array.put(JSONObject().put("id",r.id).put("title",r.title).put("category",r.category)
                .put("hour",r.hour).put("minute",r.minute).put("duration",r.duration).put("repeatMask",r.repeatMask))
        }
        prefs(ctx).edit().putString("routines",array.toString()).apply()
        RoutineReminder.rescheduleAll(ctx)
        ChibiWidget.updateAll(ctx)
    }
    fun isDone(ctx:Context,id:Int,date:String=today()):Boolean=prefs(ctx).getBoolean("done:$date:$id",false)
    fun mark(ctx:Context,id:Int,done:Boolean) {
        prefs(ctx).edit().putBoolean("done:${today()}:$id",done).apply()
        ChibiWidget.updateAll(ctx)
    }
    fun count(ctx:Context,date:String=today()):Int=routines(ctx).count { isDone(ctx,it.id,date) }
    fun nextId():Int=(System.currentTimeMillis()%1800000000L).toInt()
    fun points(ctx:Context):Int {
        val routines=routines(ctx)
        val cal=Calendar.getInstance()
        var completed=0
        repeat(90) {
            val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time)
            completed+=routines.count { r -> isDone(ctx,r.id,day) }
            cal.add(Calendar.DATE,-1)
        }
        return completed*10
    }
    fun streak(ctx:Context):Int {
        val cal=Calendar.getInstance()
        if(count(ctx)==0) cal.add(Calendar.DATE,-1)
        var days=0
        repeat(90) {
            val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time)
            if(count(ctx,day)>0) { days++;cal.add(Calendar.DATE,-1) } else return days
        }
        return days
    }
    fun setGoal(ctx:Context,title:String,days:Int) {
        prefs(ctx).edit().putString("goalTitle",title).putInt("goalDays",days).putLong("goalStart",System.currentTimeMillis()).apply()
    }
    fun goalTitle(ctx:Context)=prefs(ctx).getString("goalTitle","") ?: ""
    fun goalDays(ctx:Context)=prefs(ctx).getInt("goalDays",30)
    fun goalStart(ctx:Context)=prefs(ctx).getLong("goalStart",0L)
}

object RoutineReminder {
    private fun requestCode(id:Int)=id xor 0x382819

    // A disabled or corrupted legacy routine must not create a notification.
    // Keep the saved record untouched so an upgrade never destroys user data.
    internal fun canSchedule(r:Routine):Boolean =
        r.hour in 0..23 && r.minute in 0..59 && r.repeatMask in 1..127

    // A delayed broadcast for an edited routine must not ring at its old time.
    // Older pending intents without a signature are still accepted on upgrade.
    internal fun scheduleSignature(r:Routine):String =
        "${r.hour}:${r.minute}:${r.repeatMask}"

    private fun pending(ctx:Context,r:Routine):PendingIntent=PendingIntent.getBroadcast(
        ctx,requestCode(r.id),Intent(ctx,RoutineReminderReceiver::class.java)
            .putExtra("routine_id",r.id).putExtra("routine_signature",scheduleSignature(r)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(ctx:Context,r:Routine) { ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx,r)) }
    fun schedule(ctx:Context,r:Routine) {
        if(!canSchedule(r)) { cancel(ctx,r);return }
        val c=Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,r.hour);set(Calendar.MINUTE,r.minute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        repeat(9) {
            val offset=c.get(Calendar.DAY_OF_WEEK)-1
            if(c.timeInMillis>System.currentTimeMillis() && (r.repeatMask and (1 shl offset))!=0) {
                val am=ctx.getSystemService(AlarmManager::class.java)
                val alarm = pending(ctx, r)
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, alarm)
                } else {
                    try {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, alarm)
                    } catch (_: SecurityException) {
                        // Exact-alarm permission can be revoked between the check and scheduling.
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, alarm)
                    }
                }
                return
            }
            c.add(Calendar.DATE,1)
        }
    }
    fun rescheduleAll(ctx:Context) {
        val present=LifeData.routines(ctx)
        present.forEach { cancel(ctx,it);schedule(ctx,it) }
    }
}
class RoutineReminderReceiver:BroadcastReceiver() {
    override fun onReceive(ctx:Context,intent:Intent) {
        val id=intent.getIntExtra("routine_id",-1)
        val r=LifeData.routines(ctx).firstOrNull { it.id==id } ?: return
        if(!RoutineReminder.canSchedule(r)) {
            RoutineReminder.cancel(ctx,r)
            return
        }
        val scheduledSignature=intent.getStringExtra("routine_signature")
        if(scheduledSignature!=null && scheduledSignature!=RoutineReminder.scheduleSignature(r)) return
        RoutineReminder.schedule(ctx,r)
        if(LifeData.isDone(ctx,id)) return
        val nm=ctx.getSystemService(NotificationManager::class.java)
        val channel="chibi_routines"
        nm.createNotificationChannel(NotificationChannel(channel,"Lịch phát triển bản thân",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(ctx,id,Intent(ctx,RoutineActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id,Notification.Builder(ctx,channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🐰 Đến giờ: "+r.title)
            .setContentText(r.category+" • "+r.duration+" phút • Chibi đang cổ vũ bạn!")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
