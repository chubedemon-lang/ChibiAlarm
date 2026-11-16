package com.chibialarm.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FitnessActivityEffortSuggestionTest {
    @Before fun clearWorkoutHistory() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
        }
    }

    @Test fun repeatedEffortShowsAnActionThatPrefillsTheLightSessionRequest() {
        val today = LocalDate.now()
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            Workout(
                date = today.minusDays(2).toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private note",
                sessionId = UUID.randomUUID().toString(),
                completedMoves = 2,
                selfReportedSets = 2,
                effortLog = "Squat: hiệp 1: Khó, cần nghỉ thêm"
            ),
            Workout(
                date = today.minusDays(1).toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private note",
                sessionId = UUID.randomUUID().toString(),
                completedMoves = 2,
                selfReportedSets = 2,
                effortLog = "Squat: hiệp 1: Quá sức, tạm dừng"
            )
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val allViews = views(content)
        val action = allViews.filterIsInstance<Button>().first {
            it.text.toString() == "💬 Hỏi Chibi về buổi nhẹ hơn"
        }
        assertEquals(View.VISIBLE, action.visibility)

        action.performClick()
        val input = allViews.filterIsInstance<EditText>().first {
            it.contentDescription?.toString() == "Yêu cầu giáo án bằng tiếng Việt"
        }
        assertTrue(input.text.toString().contains("buổi tập nhẹ hơn"))
        assertTrue(input.text.toString().contains("không tự tăng số hiệp"))
        controller.pause().stop().destroy()
    }
}
