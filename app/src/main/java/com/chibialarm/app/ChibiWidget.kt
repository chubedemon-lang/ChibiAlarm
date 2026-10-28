package com.chibialarm.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ChibiWidget:AppWidgetProvider() {
    override fun onUpdate(ctx:Context,manager:AppWidgetManager,ids:IntArray) {
        ids.forEach { updateOne(ctx,manager,it) }
    }
    override fun onReceive(ctx:Context,intent:Intent) {
        super.onReceive(ctx,intent)
        if(intent.action==Intent.ACTION_DATE_CHANGED || intent.action==Intent.ACTION_TIME_CHANGED) updateAll(ctx)
    }
    companion object {
        fun updateAll(ctx:Context) {
            val manager=AppWidgetManager.getInstance(ctx)
            val component=ComponentName(ctx,ChibiWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(ctx,manager,it) }
        }
        private fun updateOne(ctx:Context,manager:AppWidgetManager,id:Int) {
            val views=RemoteViews(ctx.packageName,R.layout.chibi_widget)
            val routines=LifeData.routines(ctx)
            val done=LifeData.count(ctx)
            val next=routines.firstOrNull { !LifeData.isDone(ctx,it.id) }
            views.setTextViewText(R.id.widget_title,"🐰 CHIBI LIFE")
            views.setTextViewText(R.id.widget_progress,"🌱 Hôm nay $done/${routines.size}  •  🔥 ${LifeData.streak(ctx)} ngày")
            views.setTextViewText(R.id.widget_next,if(next==null) "✨ Hoàn thành mục tiêu hôm nay!" else "⏰ ${next.label()} • ${next.title}")
            val intent=Intent(ctx,RoutineActivity::class.java)
            val pending=PendingIntent.getActivity(ctx,12001,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root,pending)
            manager.updateAppWidget(id,views)
        }
    }
}
