package com.chibialarm.app

import com.chibialarm.app.fitness.WorkoutMidSessionRequest

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutRestConfirmationTest {
    @Before fun clearCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun selectSetBasedMove(activity: GuidedWorkoutActivity, unit: String) {
        val movesField = GuidedWorkoutActivity::class.java.getDeclaredField("moves")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val moves = movesField.get(activity) as List<TrainingMove>
        val selected = moves.indices.firstOrNull { position ->
            WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds)?.unit == unit
        } ?: throw AssertionError("No set-based exercise with unit $unit in the test program")
        GuidedWorkoutActivity::class.java.getDeclaredField("index")
            .apply { isAccessible = true }.setInt(activity, selected)
        GuidedWorkoutActivity::class.java.getDeclaredMethod("displayStep")
            .apply { isAccessible = true }.invoke(activity)
    }

    private fun remaining(activity: GuidedWorkoutActivity): Long {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        return field.getLong(activity)
    }

    private fun propose(activity: GuidedWorkoutActivity, seconds: Int): AlertDialog {
        val method = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("confirmExtraRest", Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(activity, seconds)
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    @Test fun restRequestDoesNotMutateUntilConfirmed() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val dialog = propose(activity, 30)
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

    @Test fun acceptingRestPreviewStartsSeparateRestWithoutChangingExerciseTime() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val dialog = propose(activity, 30)
        // propose() pauses the exercise first; capture the frozen value after that pause.
        val exerciseBeforeRest = remaining(activity)
        val elapsedBeforeRest = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("activeElapsedMs").apply { isAccessible = true }
            .invoke(activity) as Long

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val restingField = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
            .apply { isAccessible = true }
        val restRemainingField = GuidedWorkoutActivity::class.java.getDeclaredField("restRemainingMs")
            .apply { isAccessible = true }
        val elapsedAfterRest = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("activeElapsedMs").apply { isAccessible = true }
            .invoke(activity) as Long

        assertEquals(exerciseBeforeRest, remaining(activity))
        assertTrue(restingField.getBoolean(activity))
        assertTrue(restRemainingField.getLong(activity) in 29_000L..30_000L)
        assertEquals(elapsedBeforeRest, elapsedAfterRest)
        activity.finish()
    }

    @Test fun outdatedRestPreviewCannotOverwriteNewTimer() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val dialog = propose(activity, 30)
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        field.setLong(activity, remaining(activity) + 1_000L)
        val changed = remaining(activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(changed, remaining(activity))
        activity.finish()
    }
    private fun skipRest(activity: GuidedWorkoutActivity): AlertDialog {
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("confirmSkipRest")
        method.isAccessible = true
        method.invoke(activity)
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    private fun editText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            editText(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test fun customRestRejectsInvalidSecondsWithoutClosingDialog() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("requestCustomRest")
        method.isAccessible = true
        method.invoke(activity)
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val field = editText(dialog.window!!.decorView)!!
        field.setText("601")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        field.setText("abc")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        dialog.dismiss()
        activity.finish()
    }

    @Test fun customRestRequiresSecondExplicitConfirmation() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("requestCustomRest")
        method.isAccessible = true
        method.invoke(activity)
        val inputDialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        editText(inputDialog.window!!.decorView)!!.setText("45")
        inputDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val confirmation = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(confirmation.isShowing)
        assertEquals(before, remaining(activity))
        confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

    @Test fun compoundEasierRestRejectsStaleTimer() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val command = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("handleCoachCommand", String::class.java)
        command.isAccessible = true
        command.invoke(activity, "dễ hơn và nghỉ thêm 30 giây")
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        field.setLong(activity, remaining(activity) + 2_000L)
        val changed = remaining(activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(changed, remaining(activity))
        activity.finish()
    }

    @Test fun compoundEasierRestCancellationLeavesTimerUntouched() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val command = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("handleCoachCommand", String::class.java)
        command.isAccessible = true
        command.invoke(activity, "dễ hơn và nghỉ thêm 30 giây")
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

    @Test fun spokenRepPreviewRejectsChangedTimer() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        selectSetBasedMove(activity, "lần")
        val command = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("handleCoachCommand", String::class.java)
        command.isAccessible = true
        command.invoke(activity, "giảm còn 6 lần")
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        val timer = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        timer.isAccessible = true
        timer.setLong(activity, remaining(activity) + 2_000L)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val targets = GuidedWorkoutActivity::class.java.getDeclaredField("repTargets")
        targets.isAccessible = true
        assertTrue((targets.get(activity) as Map<*, *>).isEmpty())
        activity.finish()
    }

    @Test fun spokenSecondsTargetUsesPlankUnitAndRejectsChangedTimer() {
        val intent = Intent(RuntimeEnvironment.getApplication(), GuidedWorkoutActivity::class.java)
            .putExtra("program", "core")
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup().get()
        selectSetBasedMove(activity, "giây")
        val command = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("handleCoachCommand", String::class.java)
        command.isAccessible = true
        command.invoke(activity, "giảm còn 20 giây")
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        val timer = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        timer.isAccessible = true
        timer.setLong(activity, remaining(activity) + 2_000L)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val targets = GuidedWorkoutActivity::class.java.getDeclaredField("repTargets")
        targets.isAccessible = true
        assertTrue((targets.get(activity) as Map<*, *>).isEmpty())
        activity.finish()
    }


    @Test fun restOnlyMidSessionRequestExtendsActiveRestOnlyAfterConfirmation() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val restingField = GuidedWorkoutActivity::class.java.getDeclaredField("resting")
            .apply { isAccessible = true }
        val pausedField = GuidedWorkoutActivity::class.java.getDeclaredField("paused")
            .apply { isAccessible = true }
        val restRemainingField = GuidedWorkoutActivity::class.java.getDeclaredField("restRemainingMs")
            .apply { isAccessible = true }
        restingField.setBoolean(activity, true)
        pausedField.setBoolean(activity, true)
        restRemainingField.setLong(activity, 45_000L)
        val exerciseBefore = remaining(activity)

        val apply = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "applyMidSessionRequest",
            WorkoutMidSessionRequest.Request::class.java
        ).apply { isAccessible = true }
        val request = WorkoutMidSessionRequest.Request(restSeconds = 30)
        assertTrue(request.isRestOnly)
        apply.invoke(activity, request)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(45_000L, restRemainingField.getLong(activity))
        assertEquals(exerciseBefore, remaining(activity))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(75_000L, restRemainingField.getLong(activity))
        assertEquals(exerciseBefore, remaining(activity))
        assertTrue("A paused rest must remain paused after extension", pausedField.getBoolean(activity))
        val resume = GuidedWorkoutActivity::class.java.getDeclaredMethod("resume").apply { isAccessible = true }
        resume.invoke(activity)
        assertTrue("The extended rest can be resumed explicitly", !pausedField.getBoolean(activity))
        activity.finish()
    }

    @Test fun extendingRunningRestKeepsItRunningAndAddsTheRequestedDuration() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val restingField = GuidedWorkoutActivity::class.java.getDeclaredField("resting").apply { isAccessible = true }
        val pausedField = GuidedWorkoutActivity::class.java.getDeclaredField("paused").apply { isAccessible = true }
        val deadlineField = GuidedWorkoutActivity::class.java.getDeclaredField("restDeadline").apply { isAccessible = true }
        val remainingField = GuidedWorkoutActivity::class.java.getDeclaredField("restRemainingMs").apply { isAccessible = true }
        restingField.setBoolean(activity, true)
        pausedField.setBoolean(activity, false)
        remainingField.setLong(activity, 45_000L)
        deadlineField.setLong(activity, android.os.SystemClock.elapsedRealtime() + 45_000L)
        val apply = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "applyMidSessionRequest", WorkoutMidSessionRequest.Request::class.java
        ).apply { isAccessible = true }
        apply.invoke(activity, WorkoutMidSessionRequest.Request(restSeconds = 30))
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val before = remainingField.getLong(activity)
        assertTrue(before in 44_000L..45_000L)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before + 30_000L, remainingField.getLong(activity))
        assertTrue("A previously running rest should continue", !pausedField.getBoolean(activity))
        activity.finish()
    }


    @Test fun skipRestDoesNotMutateUntilTheUserConfirmsAndCancelKeepsTheRest() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("resting").setBoolean(activity, true)
        field("paused").setBoolean(activity, true)
        field("restRemainingMs").setLong(activity, 45_000L)
        val exerciseBefore = remaining(activity)

        val dialog = skipRest(activity)
        assertTrue(dialog.isShowing)
        assertTrue(field("resting").getBoolean(activity))
        assertEquals(45_000L, field("restRemainingMs").getLong(activity))
        assertEquals(exerciseBefore, remaining(activity))

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertTrue(field("resting").getBoolean(activity))
        assertTrue(field("paused").getBoolean(activity))
        assertEquals(45_000L, field("restRemainingMs").getLong(activity))
        assertEquals(exerciseBefore, remaining(activity))
        activity.finish()
    }

    @Test fun confirmedSkipRestClearsOnlyRestAndLeavesWorkoutPaused() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("resting").setBoolean(activity, true)
        field("paused").setBoolean(activity, true)
        field("restRemainingMs").setLong(activity, 45_000L)
        val exerciseBefore = remaining(activity)
        val render = GuidedWorkoutActivity::class.java.getDeclaredMethod("renderSetControls")
            .apply { isAccessible = true }
        render.invoke(activity)
        val button = field("skipRestButton").get(activity) as android.widget.Button
        assertEquals(View.VISIBLE, button.visibility)

        button.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(field("resting").getBoolean(activity))
        assertTrue("Skipping rest must not auto-start the next exercise", field("paused").getBoolean(activity))
        assertEquals(0L, field("restRemainingMs").getLong(activity))
        assertEquals(exerciseBefore, remaining(activity))
        assertEquals(View.GONE, button.visibility)
        val saved = RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
        assertFalse(saved.getBoolean("resting", true))
        assertEquals(0L, saved.getLong("restRemaining", -1L))
        activity.finish()
    }

    @Test fun pauseCommandWinsOverRestOnlyParserWhenItMentionsSkippingRest() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("resting").setBoolean(activity, true)
        field("paused").setBoolean(activity, false)
        field("restRemainingMs").setLong(activity, 45_000L)
        field("restDeadline").setLong(activity, android.os.SystemClock.elapsedRealtime() + 45_000L)

        val command = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "handleCoachCommand", String::class.java
        ).apply { isAccessible = true }
        command.invoke(activity, "dừng tập rồi bỏ qua nghỉ")

        assertTrue("Pause must stop the running rest countdown", field("paused").getBoolean(activity))
        assertTrue("The request must not skip the rest", field("resting").getBoolean(activity))
        assertTrue(field("restRemainingMs").getLong(activity) in 44_000L..45_000L)
        activity.finish()
    }

    @Test fun spokenSkipRestUsesTheSameConfirmationGate() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("resting").setBoolean(activity, true)
        field("paused").setBoolean(activity, true)
        field("restRemainingMs").setLong(activity, 30_000L)
        val command = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "handleCoachCommand", String::class.java
        ).apply { isAccessible = true }
        command.invoke(activity, "bỏ qua nghỉ")
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertTrue(field("resting").getBoolean(activity))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(field("resting").getBoolean(activity))
        assertTrue(field("paused").getBoolean(activity))
        activity.finish()
    }

    @Test fun finalSetEffortIsSavedWithoutStartingAnotherSetRest() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val moves = field("moves").get(activity) as List<TrainingMove>
        val moveIndex = moves.indexOfFirst { WorkoutSetTracker.parse(it.dose) != null }
        assertTrue("The starter program should contain a set-based move", moveIndex >= 0)
        val plan = WorkoutSetTracker.parse(moves[moveIndex].dose, moves[moveIndex].restSeconds)!!
        val id = field("sessionId").get(activity) as String
        field("index").setInt(activity, moveIndex)
        @Suppress("UNCHECKED_CAST")
        (field("completedSetsByMove").get(activity) as MutableMap<Int, Int>)[moveIndex] = plan.maximumSets

        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "showSetEffortCheckIn",
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            WorkoutSetTracker.Plan::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        method.invoke(activity, moveIndex, id, plan.maximumSets, plan, false)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        val choices = dialog.listView
        choices.performItemClick(choices.getChildAt(0), 0, choices.getItemIdAtPosition(0))
        shadowOf(Looper.getMainLooper()).idle()

        @Suppress("UNCHECKED_CAST")
        val effort = field("effortLevelsByMove").get(activity) as Map<Int, List<WorkoutEffortFeedback.Level?>>
        val savedEffort = effort[moveIndex]
        assertEquals("Every confirmed set keeps its own slot, including unrated earlier sets", plan.maximumSets, savedEffort?.size)
        assertTrue(savedEffort!!.dropLast(1).all { it == null })
        assertEquals(WorkoutEffortFeedback.Level.EASY, savedEffort.last())
        assertTrue("A final-set check-in must not start an extra rest timer", !field("resting").getBoolean(activity))
        activity.finish()
    }

    @Test fun onPauseSynchronouslyPersistsCheckpointAndRestoreKeepsProgressPaused() {
        val sessionId = "550e8400-e29b-41d4-a716-446655440099"
        val firstController = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup()
        val first = firstController.get()

        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("sessionId").set(first, sessionId)
        field("index").setInt(first, 1)
        field("remainingMs").setLong(first, 37_000L)
        field("resting").setBoolean(first, true)
        field("restRemainingMs").setLong(first, 45_000L)
        @Suppress("UNCHECKED_CAST")
        (field("completedMoveIndices").get(first) as MutableSet<Int>).add(0)
        @Suppress("UNCHECKED_CAST")
        val sessionMoves = field("moves").get(first) as List<TrainingMove>
        val setMoveIndex = sessionMoves.indexOfFirst { WorkoutSetTracker.parse(it.dose) != null }
        assertTrue("The starter program should contain a set-based move", setMoveIndex >= 0)
        @Suppress("UNCHECKED_CAST")
        (field("completedSetsByMove").get(first) as MutableMap<Int, Int>)[setMoveIndex] = 1
        @Suppress("UNCHECKED_CAST")
        (field("effortLevelsByMove").get(first) as MutableMap<Int, List<WorkoutEffortFeedback.Level?>>)[setMoveIndex] =
            listOf(WorkoutEffortFeedback.Level.HARD)

        val save = GuidedWorkoutActivity::class.java.getDeclaredMethod("saveCheckpoint").apply {
            isAccessible = true
        }
        assertTrue("Checkpoint must report a successful synchronous disk commit", save.invoke(first) as Boolean)

        firstController.pause()
        val saved = RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
        assertEquals(sessionId, saved.getString("sessionId", null))
        assertEquals(1, saved.getInt("index", -1))
        assertEquals(37_000L, saved.getLong("remaining", -1L))
        assertEquals(45_000L, saved.getLong("restRemaining", -1L))
        firstController.stop().destroy()

        val restoredController = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup()
        val restored = restoredController.get()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue("A process recreation should offer the saved workout", dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(sessionId, field("sessionId").get(restored))
        assertEquals(1, field("index").getInt(restored))
        assertEquals(37_000L, remaining(restored))
        assertTrue(field("resting").getBoolean(restored))
        assertEquals(45_000L, field("restRemainingMs").getLong(restored))
        assertTrue("Restored workouts must remain paused until the user resumes",
            field("paused").getBoolean(restored))
        @Suppress("UNCHECKED_CAST")
        assertTrue((field("completedMoveIndices").get(restored) as Set<Int>).contains(0))
        @Suppress("UNCHECKED_CAST")
        val restoredEffort = field("effortLevelsByMove").get(restored) as Map<Int, List<WorkoutEffortFeedback.Level?>>
        assertEquals(listOf(WorkoutEffortFeedback.Level.HARD), restoredEffort[setMoveIndex])

        restoredController.pause().stop().destroy()
    }

    @Test fun restCountdownDefersVoiceCueWhileMicrophoneRecognitionIsActive() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }

        field("resting").setBoolean(activity, true)
        field("paused").setBoolean(activity, false)
        field("pausedForSafety").setBoolean(activity, false)
        field("restRemainingMs").setLong(activity, 5_000L)
        field("restDeadline").setLong(
            activity,
            android.os.SystemClock.elapsedRealtime() + 5_000L
        )
        field("pendingRecognitionRequestCode").set(activity, 891)

        val tick = field("tick").get(activity) as Runnable
        tick.run()
        assertFalse("TTS cue must wait while speech recognition owns the microphone",
            field("restTenSecondCueGiven").getBoolean(activity))

        field("pendingRecognitionRequestCode").set(activity, null)
        tick.run()
        assertTrue("The one-shot cue remains available after speech input ends",
            field("restTenSecondCueGiven").getBoolean(activity))
        activity.finish()
    }

    @Test fun activeRestShowsTheNextSetAndTargetInAnAccessiblePreview() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            selectSetBasedMove(activity, "lần")
            fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            val index = field("index").getInt(activity)
            val move = moves[index]
            val plan = WorkoutSetTracker.parse(move.dose, move.restSeconds)!!
            val completed = (plan.requiredSets - 1).coerceAtLeast(0)
            @Suppress("UNCHECKED_CAST")
            (field("completedSetsByMove").get(activity) as MutableMap<Int, Int>)[index] = completed
            field("resting").setBoolean(activity, true)
            field("restRemainingMs").setLong(activity, 45_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderSetControls")
                .apply { isAccessible = true }.invoke(activity)
            val preview = field("restPreview").get(activity) as android.widget.TextView
            assertEquals(View.VISIBLE, preview.visibility)
            assertTrue(preview.text.toString().contains("hiệp ${completed + 1}/${plan.maximumSets}"))
            assertTrue(preview.text.toString().contains(move.name))
            assertTrue(preview.text.toString().contains("mục tiêu"))
            assertEquals(preview.text.toString(), preview.contentDescription.toString())
            field("resting").setBoolean(activity, false)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderSetControls")
                .apply { isAccessible = true }.invoke(activity)
            assertEquals(View.GONE, preview.visibility)
        } finally {
            activity.finish()
        }
    }


    @Test fun restShowsVectorPreviewForTheNextRequiredSetAndFreezesTheMainPose() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        selectSetBasedMove(activity, "lần")

        val movesField = GuidedWorkoutActivity::class.java.getDeclaredField("moves").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val moves = movesField.get(activity) as List<TrainingMove>
        val index = GuidedWorkoutActivity::class.java.getDeclaredField("index")
            .apply { isAccessible = true }.getInt(activity)
        val plan = WorkoutSetTracker.parse(moves[index].dose, moves[index].restSeconds)!!
        val completed = GuidedWorkoutActivity::class.java.getDeclaredField("completedSetsByMove")
            .apply { isAccessible = true }.get(activity) as MutableMap<Int, Int>
        completed[index] = 1
        GuidedWorkoutActivity::class.java.getDeclaredField("resting")
            .apply { isAccessible = true }.setBoolean(activity, true)

        GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
            .apply { isAccessible = true }.invoke(activity)

        val preview = GuidedWorkoutActivity::class.java.getDeclaredField("restPosePreview")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val mainPose = GuidedWorkoutActivity::class.java.getDeclaredField("poseView")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val stepPreview = GuidedWorkoutActivity::class.java.getDeclaredField("restStepPreview")
            .apply { isAccessible = true }.get(activity) as android.widget.TextView
        val expectedCue = WorkoutRestStepPreview.forMove(moves[index])

        assertEquals(View.VISIBLE, preview.visibility)
        assertEquals(View.VISIBLE, stepPreview.visibility)
        assertTrue(stepPreview.text.toString().startsWith("Bước cần chú ý:"))
        assertTrue(stepPreview.contentDescription.toString().contains(expectedCue!!.instruction))
        assertEquals(expectedCue.alternate, preview.isInAlternatePose)
        assertEquals(WorkoutPoseGuide.classify(moves[index].name), preview.pose)
        assertTrue(preview.contentDescription.toString().contains(moves[index].name))
        assertFalse("The active illustration must pause while the rest preview is shown", mainPose.motionEnabled)
        assertFalse("The rest preview is a static cue, not a second animator", preview.motionEnabled)
        assertEquals(index, WorkoutRestPosePreview.nextMoveIndex(index, moves.size, 1, plan))
        val speakButton = GuidedWorkoutActivity::class.java.getDeclaredField("restStepSpeakButton")
            .apply { isAccessible = true }.get(activity) as android.widget.Button
        assertEquals(View.VISIBLE, speakButton.visibility)
        assertEquals("🔊 Nghe bước tiếp theo", speakButton.text.toString())
        assertTrue(speakButton.contentDescription.toString().contains("Đọc một lần"))
        assertTrue(speakButton.contentDescription.toString().contains("giọng đọc chưa sẵn sàng"))
        assertFalse("The voice action stays disabled until the TTS engine is ready", speakButton.isEnabled)
        assertTrue(WorkoutRestStepPreview.spokenMessage(moves[index])!!.contains(expectedCue.instruction))
        activity.finish()
    }

    @Test fun restPreviewAdvancesAfterMaximumSetsAndHidesWhenRestEnds() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        selectSetBasedMove(activity, "lần")

        val movesField = GuidedWorkoutActivity::class.java.getDeclaredField("moves").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val moves = movesField.get(activity) as List<TrainingMove>
        val index = GuidedWorkoutActivity::class.java.getDeclaredField("index")
            .apply { isAccessible = true }.getInt(activity)
        val plan = WorkoutSetTracker.parse(moves[index].dose, moves[index].restSeconds)!!
        val completed = GuidedWorkoutActivity::class.java.getDeclaredField("completedSetsByMove")
            .apply { isAccessible = true }.get(activity) as MutableMap<Int, Int>
        completed[index] = plan.maximumSets
        val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting").apply { isAccessible = true }
        resting.setBoolean(activity, true)

        val render = GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
            .apply { isAccessible = true }
        render.invoke(activity)

        val preview = GuidedWorkoutActivity::class.java.getDeclaredField("restPosePreview")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val mainPose = GuidedWorkoutActivity::class.java.getDeclaredField("poseView")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val stepPreview = GuidedWorkoutActivity::class.java.getDeclaredField("restStepPreview")
            .apply { isAccessible = true }.get(activity) as android.widget.TextView
        val expectedIndex = WorkoutRestPosePreview.nextMoveIndex(index, moves.size, plan.maximumSets, plan)

        assertEquals(View.VISIBLE, preview.visibility)
        assertEquals(View.VISIBLE, stepPreview.visibility)
        assertTrue(stepPreview.text.toString().contains(WorkoutRestStepPreview.forMove(moves[expectedIndex!!])!!.instruction))
        assertEquals(WorkoutPoseGuide.classify(moves[expectedIndex!!].name), preview.pose)
        assertTrue(preview.contentDescription.toString().contains(moves[expectedIndex].name))

        resting.setBoolean(activity, false)
        render.invoke(activity)
        assertEquals(View.GONE, preview.visibility)
        assertEquals(View.GONE, stepPreview.visibility)
        val speakButton = GuidedWorkoutActivity::class.java.getDeclaredField("restStepSpeakButton")
            .apply { isAccessible = true }.get(activity) as android.widget.Button
        assertEquals(View.GONE, speakButton.visibility)
        assertTrue("The main exercise animation should resume outside rest", mainPose.motionEnabled)
        activity.finish()
    }


    @Test fun restStepSpeechIsManualAndDisabledWhileMicrophoneIsPending() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        selectSetBasedMove(activity, "lần")
        fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field("resting").setBoolean(activity, true)
        field("ready").setBoolean(activity, true)
        val render = GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
            .apply { isAccessible = true }
        render.invoke(activity)
        val button = field("restStepSpeakButton").get(activity) as android.widget.Button
        assertEquals(View.VISIBLE, button.visibility)
        assertTrue(button.isEnabled)
        val before = remaining(activity)
        button.performClick()
        assertEquals("Speaking a cue must not change exercise time", before, remaining(activity))

        field("pendingRecognitionRequestCode").set(activity, 890)
        render.invoke(activity)
        assertEquals(View.VISIBLE, button.visibility)
        assertFalse("TTS must yield while microphone recognition is pending", button.isEnabled)

        field("pendingRecognitionRequestCode").set(activity, null)
        field("pausedForSafety").setBoolean(activity, true)
        render.invoke(activity)
        assertEquals(View.GONE, button.visibility)
        activity.finish()
    }

    @Test fun safetyPauseHidesRestPreviewAndKeepsTheMainPoseFrozen() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        selectSetBasedMove(activity, "lần")
        val resting = GuidedWorkoutActivity::class.java.getDeclaredField("resting").apply { isAccessible = true }
        val safety = GuidedWorkoutActivity::class.java.getDeclaredField("pausedForSafety").apply { isAccessible = true }
        resting.setBoolean(activity, true)
        safety.setBoolean(activity, true)

        GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
            .apply { isAccessible = true }.invoke(activity)

        val preview = GuidedWorkoutActivity::class.java.getDeclaredField("restPosePreview")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val mainPose = GuidedWorkoutActivity::class.java.getDeclaredField("poseView")
            .apply { isAccessible = true }.get(activity) as WorkoutPoseView
        val stepPreview = GuidedWorkoutActivity::class.java.getDeclaredField("restStepPreview")
            .apply { isAccessible = true }.get(activity) as android.widget.TextView
        assertEquals(View.GONE, preview.visibility)
        assertEquals(View.GONE, stepPreview.visibility)
        val speakButton = GuidedWorkoutActivity::class.java.getDeclaredField("restStepSpeakButton")
            .apply { isAccessible = true }.get(activity) as android.widget.Button
        assertEquals(View.GONE, speakButton.visibility)
        assertFalse("A safety pause must not animate exercise cues", mainPose.motionEnabled)
        activity.finish()
    }

}
