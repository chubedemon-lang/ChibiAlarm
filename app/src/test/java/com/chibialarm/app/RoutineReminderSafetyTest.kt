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

/** Old, disabled and edited reminders must not wake the user unexpectedly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineReminderSafetyTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val id = 98173

    private fun notifications() = context.getSystemService(NotificationManager::class.java)
    private fun fired(): Boolean = notifications().activeNotifications.any { it.id == id }

    @Before fun reset() {
        LifeData.save(context, emptyList())
        notifications().cancel(id)
    }

    @After fun cleanup() {
        LifeData.save(context, emptyList())
        notifications().cancel(id)
    }

    @Test fun disabledOrMalformedRoutineDoesNotNotifyEvenIfAnOldIntentArrives() {
        for (r in listOf(
            Routine(id, "Disabled", "Focus", 8, 0, 25, repeatMask = 0),
            Routine(id, "Invalid hour", "Focus", 25, 0, 25),
            Routine(id, "Invalid minute", "Focus", 8, 61, 25),
            Routine(id, "Invalid weekday mask", "Focus", 8, 0, 25, repeatMask = 128)
        )) {
            LifeData.save(context, listOf(r))
            RoutineReminderReceiver().onReceive(context,
                Intent(context, RoutineReminderReceiver::class.java).putExtra("routine_id", id))
            assertFalse("Unexpected notification for $r", fired())
        }
    }

    @Test fun staleBroadcastAfterTimeEditDoesNotNotify() {
        val old = Routine(id, "Read", "Study", 8, 0, 25)
        val current = old.copy(hour = 14, minute = 30)
        LifeData.save(context, listOf(current))
        RoutineReminderReceiver().onReceive(context,
            Intent(context, RoutineReminderReceiver::class.java)
                .putExtra("routine_id", id)
                .putExtra("routine_signature", RoutineReminder.scheduleSignature(old)))
        assertFalse(fired())
    }

    @Test fun currentAndLegacyBroadcastsStillNotify() {
        val current = Routine(id, "Read", "Study", 14, 30, 25)
        LifeData.save(context, listOf(current))
        RoutineReminderReceiver().onReceive(context,
            Intent(context, RoutineReminderReceiver::class.java)
                .putExtra("routine_id", id)
                .putExtra("routine_signature", RoutineReminder.scheduleSignature(current)))
        assertTrue(fired())
        notifications().cancel(id)
        // Existing alarms scheduled before the upgrade have no signature.
        RoutineReminderReceiver().onReceive(context,
            Intent(context, RoutineReminderReceiver::class.java).putExtra("routine_id", id))
        assertTrue(fired())
    }
}
