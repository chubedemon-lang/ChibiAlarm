package com.chibialarm.app

import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Removed routine IDs must not leave an alarm that can fire days later. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineReminderTest {
    @Test fun removingRoutinesCancelsTheirScheduledAlarms() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("chibi_life_v12", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarms = Shadows.shadowOf(alarmManager)
        val first = Routine(180001, "Read", "Study", 12, 0, 20)
        val second = Routine(180002, "Exercise", "Fitness", 18, 0, 30)
        try {
            LifeData.save(context, listOf(first, second))
            assertEquals(2, alarms.scheduledAlarms.size)
            LifeData.save(context, listOf(first))
            assertEquals(1, alarms.scheduledAlarms.size)
            LifeData.save(context, emptyList())
            assertEquals(0, alarms.scheduledAlarms.size)
        } finally {
            LifeData.save(context, emptyList())
            prefs.edit().clear().commit()
        }
    }
}
