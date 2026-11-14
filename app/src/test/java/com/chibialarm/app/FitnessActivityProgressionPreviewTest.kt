package com.chibialarm.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class FitnessActivityProgressionPreviewTest {
    @Before fun prepareGuestFitnessData() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE).edit()
            .putInt("recommendEquipment", 2)
            .putInt("fitTimeMinutes", 30)
            .commit()
    }

    private fun workout(date: LocalDate) = Workout(
        date = date.toString(),
        name = "Guided Workout",
        minutes = 20,
        note = "private note",
        sessionId = UUID.randomUUID().toString(),
        completedMoves = 2,
        selfReportedSets = 2,
        setLog = "Kéo tạ đơn hoặc dây kháng lực: hiệp 1: 8 lần, hiệp 2: 9 lần",
        effortLog = "Kéo tạ đơn hoặc dây kháng lực: hiệp 1: Nhẹ, còn dư sức, hiệp 2: Vừa sức"
    )

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
        }
    }

    @Test fun steadyProgressActionShowsAConcretePreviewBeforeStartingTheNextSession() {
        val today = LocalDate.now()
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            workout(today.minusDays(4)),
            workout(today.minusDays(2)),
            workout(today)
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val action = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .first { it.text.toString() == "🌱 Xem bước tiến triển có kiểm soát" }
        assertEquals(View.VISIBLE, action.visibility)

        action.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
        assertTrue(message.contains("9 lần/hiệp"))
        assertTrue(message.contains("Giữ 2 hiệp tối thiểu"))
        assertTrue(message.contains("Không tăng tải"))

        controller.pause().stop().destroy()
    }
}
