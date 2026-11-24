package com.chibialarm.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineReminderWorkoutActionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val id = 98174

    private fun notifications() = context.getSystemService(NotificationManager::class.java)

    @Before fun reset() {
        LifeData.save(context, emptyList())
        notifications().cancel(id)
    }

    @After fun cleanup() {
        LifeData.save(context, emptyList())
        notifications().cancel(id)
    }

    private fun deliver(routine: Routine) {
        LifeData.save(context, listOf(routine))
        RoutineReminderReceiver().onReceive(
            context,
            Intent(context, RoutineReminderReceiver::class.java)
                .putExtra("routine_id", routine.id)
                .putExtra("routine_signature", RoutineReminder.scheduleSignature(routine))
        )
    }

    private fun postedNotification() = notifications().activeNotifications
        .firstOrNull { it.id == id }?.notification

    @Test fun weeklyBodyweightFitnessReminderOffersAStartWorkoutAction() {
        val program = TrainingCatalog.programs.first { candidate ->
            candidate.moves.isNotEmpty() && candidate.moves.all { it.equipmentOptions.isEmpty() }
        }
        val routine = Routine(
            id,
            "Thứ Hai · ${program.name}",
            "🏃 Sức khỏe",
            23,
            59,
            30,
            repeatMask = 1 shl 1
        )

        deliver(routine)

        val notification = postedNotification()
        assertNotNull(notification)
        val actions = notification?.actions.orEmpty()
        assertEquals(1, actions.size)
        assertEquals("Bắt đầu buổi tập", actions.single().title.toString())
        assertNotNull(actions.single().actionIntent)
    }

    @Test fun equipmentDependentFitnessReminderDoesNotGuessMissingEquipment() {
        val program = TrainingCatalog.programs.first { candidate ->
            candidate.moves.any { it.equipmentOptions.isNotEmpty() }
        }
        deliver(
            Routine(id, program.name, "🏃 Sức khỏe", 23, 59, 30, repeatMask = 1 shl 1)
        )

        val actions = postedNotification()?.actions.orEmpty()
        assertTrue(actions.isEmpty())
    }

    @Test fun nonFitnessRoutineDoesNotOfferAWorkoutAction() {
        val program = TrainingCatalog.programs.first { candidate ->
            candidate.moves.isNotEmpty() && candidate.moves.all { it.equipmentOptions.isEmpty() }
        }
        deliver(
            Routine(id, program.name, "📚 Học tập", 23, 59, 30, repeatMask = 1 shl 1)
        )

        val actions = postedNotification()?.actions.orEmpty()
        assertTrue(actions.isEmpty())
    }
}
