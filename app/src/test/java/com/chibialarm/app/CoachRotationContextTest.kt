package com.chibialarm.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CoachRotationContextTest {
    @Test fun activeFitnessPreferencesSurviveRotationWithoutDiskWrite() {
        val controller = Robolectric.buildActivity(CoachActivity::class.java).setup()
        val activity = controller.get()
        val field = CoachActivity::class.java.getDeclaredField("fitnessContext").apply { isAccessible = true }
        field.set(activity, FitnessNaturalRequest.Result(
            goal = 2, level = 1, energy = 0, minutes = 20, equipment = 0, impact = 1,
            reply = "temporary", avoidWristLoad = true, avoidFloorPositions = false,
            avoidOverheadReach = true
        ))
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertEquals(false, activity.getSharedPreferences("coach_history", 0).contains("fitness_goal"))
        val restored = Robolectric.buildActivity(CoachActivity::class.java).create(saved).start().resume().get()
        val result = field.get(restored) as FitnessNaturalRequest.Result
        assertEquals(2, result.goal)
        assertEquals(0, result.energy)
        assertEquals(20, result.minutes)
        assertEquals(0, result.equipment)
        assertEquals(1, result.impact)
        assertEquals(1, result.level)
        assertEquals(true, result.avoidWristLoad)
        assertEquals(false, result.avoidFloorPositions)
        assertEquals(true, result.avoidOverheadReach)
        controller.pause().stop().destroy()
        restored.finish()
    }

    private fun findButton(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findButton(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    @Test fun chatHandoffCarriesLevelAndMovementLimitsIntoTheWorkoutPlanner() {
        val coach = Robolectric.buildActivity(CoachActivity::class.java).setup().get()
        val contextField = CoachActivity::class.java.getDeclaredField("fitnessContext").apply {
            isAccessible = true
        }
        contextField.set(coach, FitnessNaturalRequest.Result(
            goal = 0, level = 2, energy = 1, minutes = 30, equipment = 2, impact = 0,
            reply = "transient", avoidWristLoad = true, avoidFloorPositions = false,
            avoidOverheadReach = true
        ))

        val button = findButton(coach.window.decorView, "🏃 Xem bài tập")
        assertNotNull(button)
        assertTrue(button!!.performClick())
        val handoff = shadowOf(coach).nextStartedActivity
        assertNotNull(handoff)
        assertEquals(2, handoff.getIntExtra("coach_level", -1))
        assertEquals(true, handoff.getBooleanExtra("coach_avoid_wrist_load", false))
        assertEquals(false, handoff.getBooleanExtra("coach_avoid_floor_positions", true))
        assertEquals(true, handoff.getBooleanExtra("coach_avoid_overhead_reach", false))

        val fitnessController = Robolectric.buildActivity(FitnessActivity::class.java, handoff).setup()
        val fitness = fitnessController.get()
        val resultField = FitnessActivity::class.java.getDeclaredField("conversation").apply {
            isAccessible = true
        }
        val received = resultField.get(fitness) as FitnessNaturalRequest.Result
        assertEquals(2, received.level)
        assertEquals(true, received.avoidWristLoad)
        assertEquals(false, received.avoidFloorPositions)
        assertEquals(true, received.avoidOverheadReach)

        val wristField = FitnessActivity::class.java.getDeclaredField("wristLoadCheckBox").apply {
            isAccessible = true
        }
        val floorField = FitnessActivity::class.java.getDeclaredField("floorPositionCheckBox").apply {
            isAccessible = true
        }
        val overheadField = FitnessActivity::class.java.getDeclaredField("overheadReachCheckBox").apply {
            isAccessible = true
        }
        assertEquals(true, (wristField.get(fitness) as android.widget.CheckBox).isChecked)
        assertEquals(false, (floorField.get(fitness) as android.widget.CheckBox).isChecked)
        assertEquals(true, (overheadField.get(fitness) as android.widget.CheckBox).isChecked)
        coach.finish()
        fitnessController.pause().stop().destroy()
    }

    @Test fun coachAnswersEffortHistoryLocallyWithoutPersistingJournalDetails() {
        val coach = Robolectric.buildActivity(CoachActivity::class.java).setup().get()
        val originalWorkouts = WorkoutData.history(coach)
        val chatPreferences = coach.getSharedPreferences("coach_history", 0)
        val previousChat = chatPreferences.getString("messages", null)

        try {
            chatPreferences.edit().remove("messages").commit()
            val historyField = CoachActivity::class.java.getDeclaredField("history").apply {
                isAccessible = true
            }
            historyField.set(coach, org.json.JSONArray())
            WorkoutData.save(
                coach,
                listOf(
                    Workout(
                        date = java.time.LocalDate.now().toString(),
                        name = "Guided workout",
                        minutes = 25,
                        note = "private knee detail",
                        sessionId = "550e8400-e29b-41d4-a716-446655440071",
                        completedMoves = 2,
                        selfReportedSets = 1,
                        effortLog = "Burpee: hiệp 1: Quá sức, tạm dừng"
                    )
                )
            )

            val prompt = CoachActivity::class.java.getDeclaredField("prompt").apply {
                isAccessible = true
            }.get(coach) as android.widget.EditText
            prompt.setText("Bài nào làm tao đuối nhất?")
            CoachActivity::class.java.getDeclaredMethod("chat").apply {
                isAccessible = true
            }.invoke(coach)

            val transcript = historyField.get(coach) as org.json.JSONArray
            assertEquals(2, transcript.length())
            assertEquals("user", transcript.getJSONObject(0).optString("role"))
            val reply = transcript.getJSONObject(1).optString("text")
            assertTrue(reply.contains("Burpee"))
            assertTrue(reply.contains("cảm nhận tự khai báo"))
            assertFalse(reply.contains("private knee detail"))

            val persisted = chatPreferences.getString("messages", "[]") ?: "[]"
            assertFalse("Journal-derived exercise details must not be persisted in coach chat", persisted.contains("Burpee"))
            assertFalse("The sensitive journal question must remain transient", persisted.contains("đuối"))
        } finally {
            WorkoutData.save(coach, originalWorkouts)
            if (previousChat == null) {
                chatPreferences.edit().remove("messages").commit()
            } else {
                chatPreferences.edit().putString("messages", previousChat).commit()
            }
            coach.finish()
        }
    }

    @Test fun noContextRemainsAbsentOnFreshLaunch() {
        val activity = Robolectric.buildActivity(CoachActivity::class.java).setup().get()
        val field = CoachActivity::class.java.getDeclaredField("fitnessContext").apply { isAccessible = true }
        assertNull(field.get(activity))
        activity.finish()
    }
}
