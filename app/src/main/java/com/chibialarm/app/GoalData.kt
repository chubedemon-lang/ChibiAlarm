package com.chibialarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class LifeGoal(val id:Int,val title:String,val span:Int,val started:Long,val checked:Set<String>)
object GoalData {
    private const val PREF="chibi_goals_v12"
    private fun pref(ctx:Context)=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
    fun goals(ctx:Context):List<LifeGoal> {
        return try {
            val data=JSONArray(pref(ctx).getString("goals","[]"))
            (0 until data.length()).map { i ->
                val o=data.getJSONObject(i)
                val entries=o.optJSONArray("checked") ?: JSONArray()
                val marks=(0 until entries.length()).map { entries.optString(it) }.toSet()
                LifeGoal(o.getInt("id"),o.getString("title"),o.optInt("span",30),o.getLong("started"),marks)
            }
        } catch(_:Exception) { emptyList() }
    }
    fun save(ctx:Context,goals:List<LifeGoal>) {
        val arr=JSONArray()
        goals.forEach {
            arr.put(JSONObject().put("id",it.id).put("title",it.title).put("span",it.span)
                .put("started",it.started).put("checked",JSONArray(it.checked.toList())))
        }
        pref(ctx).edit().putString("goals",arr.toString()).apply()
        ChibiWidget.updateAll(ctx)
    }
    fun toggle(ctx:Context,g:LifeGoal) {
        val today=LifeData.today()
        val marks=g.checked.toMutableSet()
        if(!marks.add(today)) marks.remove(today)
        save(ctx,goals(ctx).map { if(it.id==g.id) it.copy(checked=marks) else it })
    }
    fun elapsed(g:LifeGoal):Int=((System.currentTimeMillis()-g.started)/86400000L).toInt().coerceAtLeast(0)+1
}
