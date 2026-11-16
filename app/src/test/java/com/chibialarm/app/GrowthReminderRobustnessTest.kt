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

/** Invalid legacy reminder settings must never crash boot or notify a stale user. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GrowthReminderRobustnessTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("growth", Context.MODE_PRIVATE)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before fun reset() {
        prefs.edit().clear().commit()
        notifications.cancelAll()
    }

    @After fun cleanup() {
        prefs.edit().clear().commit()
        notifications.cancelAll()
    }

    @Test fun malformedLegacyTimeIsPreservedAndDoesNotTriggerNotification() {
        assertTrue(prefs.edit().putString("remindHour", "broken")
            .putInt("remindMinute", 15).commit())
        assertNull(GrowthReminder.savedTime(context))
        assertFalse(GrowthReminder.restore(context))
        GrowthReminderReceiver().onReceive(context,
            Intent(context, GrowthReminderReceiver::class.java))
        assertEquals("broken", prefs.getString("remindHour", null))
        assertEquals(0, notifications.activeNotifications.size)
    }

    @Test fun removedReminderCannotTriggerStaleBroadcast() {
        assertFalse(GrowthReminder.restore(context))
        GrowthReminderReceiver().onReceive(context,
            Intent(context, GrowthReminderReceiver::class.java))
        assertEquals(0, notifications.activeNotifications.size)
    }

    @Test fun invalidHourAndMinuteAreRejectedWithoutRewritingPreferences() {
        assertTrue(prefs.edit().putInt("remindHour", 25)
            .putInt("remindMinute", 0).commit())
        assertFalse(GrowthReminder.restore(context))
        assertEquals(25, prefs.getInt("remindHour", 0))

        assertTrue(prefs.edit().putInt("remindHour", 9)
            .putInt("remindMinute", 99).commit())
        assertFalse(GrowthReminder.restore(context))
        assertEquals(99, prefs.getInt("remindMinute", 0))
    }

    @Test fun validLegacyReminderWithoutMinuteStillRestores() {
        assertTrue(prefs.edit().putInt("remindHour", 8).commit())
        assertEquals(8 to 0, GrowthReminder.savedTime(context))
        assertTrue(GrowthReminder.restore(context))
        assertEquals(8, prefs.getInt("remindHour", -1))
        assertFalse(prefs.contains("remindMinute"))
    }

    @Test fun validReminderCanRescheduleAndNotify() {
        assertTrue(prefs.edit().putInt("remindHour", 7)
            .putInt("remindMinute", 30).commit())
        assertEquals(7 to 30, GrowthReminder.savedTime(context))
        GrowthReminderReceiver().onReceive(context,
            Intent(context, GrowthReminderReceiver::class.java))
        assertEquals(1, notifications.activeNotifications.size)
    }
}
