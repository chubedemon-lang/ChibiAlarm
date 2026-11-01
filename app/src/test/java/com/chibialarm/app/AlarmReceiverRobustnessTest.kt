package com.chibialarm.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Corrupted legacy preference types must not crash a booted alarm receiver. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlarmReceiverRobustnessTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val id = 97421

    @Before fun reset() {
        Scheduler.cancel(context, id)
        prefs.edit().remove(id.toString()).commit()
    }

    @After fun cleanup() {
        Scheduler.cancel(context, id)
        prefs.edit().remove(id.toString()).commit()
    }

    @Test fun nonStringRecordIsIgnoredWithoutCrashOrNotification() {
        assertTrue(prefs.edit().putInt(id.toString(), 123).commit())
        Scheduler.schedule(context, id, 8, 0)
        AlarmReceiver().onReceive(context,
            Intent(context, AlarmReceiver::class.java).putExtra("id", id))
        val notifications = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
        assertFalse(notifications.any { it.id == id })
    }

    @Test fun disabledRecordDoesNotRingWhenOldBroadcastArrives() {
        assertTrue(prefs.edit().putString(id.toString(), "8,30,0").commit())
        AlarmReceiver().onReceive(context,
            Intent(context, AlarmReceiver::class.java).putExtra("id", id))
        val notifications = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
        assertFalse(notifications.any { it.id == id })
    }
}
