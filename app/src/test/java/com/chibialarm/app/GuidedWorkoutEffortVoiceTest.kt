package com.chibialarm.app

import android.app.AlertDialog
import android.os.Looper
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutEffortVoiceTest {
    @Before fun clearCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private data class CheckIn(
        val activity: GuidedWorkoutActivity,
        val dialog: AlertDialog,
        val moveIndex: Int,
        val plan: WorkoutSetTracker.Plan,
        val sessionId: String,
        val completedSets: Int
    )

    @Suppress("UNCHECKED_CAST")
    private fun openCheckIn(
        completedSetCount: Int = 1,
        previousEfforts: List<WorkoutEffortFeedback.Level?> = emptyList()
    ): CheckIn {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, GuidedWorkoutActivity::class.java)
            .putExtra("program", "full")
            .putExtra("minutes", 30)
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup().get()

        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }

        val moves = field("moves").get(activity) as List<TrainingMove>
        val moveIndex = moves.indices.first { index ->
            WorkoutSetTracker.parse(moves[index].dose, moves[index].restSeconds) != null
        }
        val plan = WorkoutSetTracker.parse(moves[moveIndex].dose, moves[moveIndex].restSeconds)!!
        field("index").setInt(activity, moveIndex)
        val completedSets = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
        completedSets[moveIndex] = completedSetCount
        val priorEffortMap = field("effortLevelsByMove").get(activity)
            as MutableMap<Int, List<WorkoutEffortFeedback.Level?>>
        if (previousEfforts.isNotEmpty()) priorEffortMap[moveIndex] = previousEfforts
        val sessionId = field("sessionId").get(activity) as String

        GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "showSetEffortCheckIn",
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            WorkoutSetTracker.Plan::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
            .invoke(activity, moveIndex, sessionId, completedSetCount, plan, true)

        return CheckIn(
            activity,
            ShadowAlertDialog.getLatestAlertDialog() as AlertDialog,
            moveIndex,
            plan,
            sessionId,
            completedSetCount
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun effortLevels(checkIn: CheckIn): Map<Int, List<WorkoutEffortFeedback.Level?>> {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("effortLevelsByMove")
            .apply { isAccessible = true }
        return field.get(checkIn.activity) as Map<Int, List<WorkoutEffortFeedback.Level?>>
    }

    private fun sendVoice(checkIn: CheckIn, phrase: String) {
        val handle = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "handleEffortVoiceCheckIn",
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            WorkoutSetTracker.Plan::class.java,
            Boolean::class.javaPrimitiveType,
            AlertDialog::class.java,
            String::class.java
        ).apply { isAccessible = true }
        handle.invoke(
            checkIn.activity, checkIn.moveIndex, checkIn.sessionId, checkIn.completedSets,
            checkIn.plan, true, checkIn.dialog, phrase
        )
    }

    @Test fun voiceAnswerRequiresDisclosureAndCanBeCancelledWithoutChangingTheCheckIn() {
        val checkIn = openCheckIn()
        try {
            val voiceButton = checkIn.dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            assertTrue(voiceButton.text.toString().contains("giọng nói"))
            voiceButton.performClick()

            val disclosure = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val message = disclosure.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("có thể xử lý âm thanh trực tuyến"))
            assertTrue(message.contains("không lưu bản ghi"))
            disclosure.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

            assertTrue("Cancelling microphone disclosure must keep the effort choices open", checkIn.dialog.isShowing)
            assertTrue(effortLevels(checkIn).isEmpty())
            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertFalse(resting.getBoolean(checkIn.activity))
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun recognizedEffortStaysPendingUntilTheUserConfirmsIt() {
        val checkIn = openCheckIn()
        try {
            sendVoice(checkIn, "khó quá")
            assertTrue(checkIn.dialog.isShowing)
            val message = checkIn.dialog.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("Khó, cần nghỉ thêm"))
            assertTrue(message.contains("Chibi chưa lưu"))
            assertTrue(effortLevels(checkIn).isEmpty())

            val confirm = checkIn.dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            assertTrue(confirm.isEnabled)
            confirm.performClick()

            assertEquals(
                WorkoutEffortFeedback.Level.HARD,
                effortLevels(checkIn)[checkIn.moveIndex]?.single()
            )
            assertFalse(checkIn.dialog.isShowing)
            val restChoice = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(restChoice.isShowing)
            assertTrue(restChoice.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString().contains("nhẹ hơn"))
            restChoice.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            assertFalse("The rest-choice dialog must close before the planner preview opens", restChoice.isShowing)
            val planPreview = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(planPreview.isShowing)
            assertTrue(
                planPreview.findViewById<android.widget.TextView>(android.R.id.message)
                    ?.text?.toString().orEmpty().contains("Các hiệp đã xác nhận không bị sửa")
            )
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun cancellingRecognizedVoiceCheckInDoesNotStoreOrStartRest() {
        val checkIn = openCheckIn()
        try {
            sendVoice(checkIn, "khó quá")
            assertTrue(checkIn.dialog.isShowing)
            assertTrue(checkIn.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(effortLevels(checkIn).isEmpty())
            assertEquals(WorkoutEffortFeedback.Level.values().size, checkIn.dialog.listView.count)

            checkIn.dialog.cancel()

            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertFalse(resting.getBoolean(checkIn.activity))
            assertTrue(effortLevels(checkIn).isEmpty())
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun manualChoiceCanCorrectARecognizedVoiceAnswer() {
        val checkIn = openCheckIn()
        try {
            sendVoice(checkIn, "khó quá")
            val list = checkIn.dialog.listView
            val manualChoice = WorkoutEffortFeedback.Level.MODERATE.ordinal
            list.performItemClick(
                list.getChildAt(manualChoice),
                manualChoice,
                list.getItemIdAtPosition(manualChoice)
            )

            assertEquals(
                WorkoutEffortFeedback.Level.MODERATE,
                effortLevels(checkIn)[checkIn.moveIndex]?.single()
            )
            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertTrue("A direct manual choice is explicit and follows the normal rest recommendation",
                resting.getBoolean(checkIn.activity))
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun cancellingHardRestSuggestionDoesNotStartRestWithoutAnExplicitChoice() {
        val checkIn = openCheckIn()
        try {
            sendVoice(checkIn, "khó quá")
            checkIn.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

            assertEquals(
                WorkoutEffortFeedback.Level.HARD,
                effortLevels(checkIn)[checkIn.moveIndex]?.single()
            )
            val restChoice = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(restChoice.isShowing)
            restChoice.cancel()

            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertFalse("Back/outside dismissal must not choose a rest duration",
                resting.getBoolean(checkIn.activity))
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun repeatedHardCheckInsOfferLongerRestButStillRequireAnExplicitChoice() {
        val checkIn = openCheckIn(
            completedSetCount = 2,
            previousEfforts = listOf(WorkoutEffortFeedback.Level.HARD)
        )
        try {
            val hardChoice = checkIn.dialog.listView
            val hardIndex = WorkoutEffortFeedback.Level.HARD.ordinal
            hardChoice.performItemClick(
                hardChoice.getChildAt(hardIndex),
                hardIndex,
                hardChoice.getItemIdAtPosition(hardIndex)
            )

            assertEquals(
                listOf(WorkoutEffortFeedback.Level.HARD, WorkoutEffortFeedback.Level.HARD),
                effortLevels(checkIn)[checkIn.moveIndex]
            )
            val restChoice = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val message = restChoice.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("2 hiệp liên tiếp"))
            assertTrue(message.contains((checkIn.plan.defaultRestSeconds + 30).toString() + " giây"))

            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertFalse("A repeated hard report must not start rest before confirmation",
                resting.getBoolean(checkIn.activity))
            restChoice.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The explicit rest choice should start the suggested countdown",
                resting.getBoolean(checkIn.activity))
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun veryHardDialogShowsTheExactPlanAwareRestAndWaitsForConfirmation() {
        val checkIn = openCheckIn()
        try {
            val choices = checkIn.dialog.listView
            val veryHardIndex = WorkoutEffortFeedback.Level.VERY_HARD.ordinal
            choices.performItemClick(
                choices.getChildAt(veryHardIndex),
                veryHardIndex,
                choices.getItemIdAtPosition(veryHardIndex)
            )

            val pauseDialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val expectedRest = maxOf(checkIn.plan.defaultRestSeconds, 120)
                .coerceAtMost(300)
            assertEquals(
                "Nghỉ ${expectedRest} giây",
                pauseDialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString()
            )
            val message = pauseDialog.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("Nghỉ theo giáo án: ${checkIn.plan.defaultRestSeconds} giây"))
            assertTrue(message.contains("Thời gian đề xuất: ${expectedRest} giây"))
            assertTrue(message.contains("không ngắn hơn giáo án"))

            val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
                .apply { isAccessible = true }
            assertFalse("A very-hard answer must pause for an explicit user choice",
                resting.getBoolean(checkIn.activity))
        } finally {
            checkIn.activity.finish()
        }
    }

    @Test fun spokenPainClosesTheCheckInAndStopsTheWorkout() {
        val checkIn = openCheckIn()
        try {
            val handle = GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "handleEffortVoiceCheckIn",
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
                WorkoutSetTracker.Plan::class.java,
                Boolean::class.javaPrimitiveType,
                AlertDialog::class.java,
                String::class.java
            ).apply { isAccessible = true }

            handle.invoke(
                checkIn.activity, checkIn.moveIndex, checkIn.sessionId, 1,
                checkIn.plan, true, checkIn.dialog, "đau gối"
            )

            val safety = GuidedWorkoutActivity::class.java.getDeclaredField("pausedForSafety")
                .apply { isAccessible = true }
            assertTrue(safety.getBoolean(checkIn.activity))
            assertFalse(checkIn.dialog.isShowing)
            assertTrue(effortLevels(checkIn).isEmpty())
        } finally {
            checkIn.activity.finish()
        }
    }
}
