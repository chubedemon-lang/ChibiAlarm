package com.chibialarm.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

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

    @Test fun latestEffortCheckInIsVisibleAndOpensReadOnlyCoachQuestion() {
        val today = LocalDate.now()
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            Workout(
                date = today.toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private health note",
                sessionId = UUID.randomUUID().toString(),
                completedMoves = 2,
                selfReportedSets = 2,
                effortLog = "Squat: hiệp 1: Vừa sức; Plank: hiệp 1: Nhẹ, còn dư sức"
            )
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val allViews = views(content)
        val insight = allViews.filterIsInstance<TextView>().first {
            it.text.toString().contains("Check-in cảm nhận gần nhất")
        }
        assertEquals(View.VISIBLE, insight.visibility)
        assertTrue(insight.text.toString().contains("Vừa sức"))
        assertTrue(insight.text.toString().contains("Nhẹ, còn dư sức"))
        assertTrue(!insight.text.toString().contains("private health note"))

        val action = allViews.filterIsInstance<Button>().first {
            it.text.toString() == "💬 Hỏi Chibi về check-in gần nhất"
        }
        assertEquals(View.VISIBLE, action.visibility)
        action.performClick()

        val input = allViews.filterIsInstance<EditText>().first {
            it.contentDescription?.toString() == "Yêu cầu giáo án bằng tiếng Việt"
        }
        assertEquals("Mức độ gắng sức buổi gần nhất thế nào?", input.text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun latestEffortCardStaysHiddenWhenNoValidCheckInExists() {
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            Workout(
                date = LocalDate.now().toString(),
                name = "Legacy workout",
                minutes = 20,
                note = "private legacy note"
            )
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val allViews = views(content)
        val effortAction = allViews.filterIsInstance<Button>().first {
            it.text.toString() == "💬 Hỏi Chibi về check-in gần nhất"
        }
        assertEquals(View.GONE, effortAction.visibility)
        controller.pause().stop().destroy()
    }


    @Test fun proposedPlanShowsMoveSpecificEffortReviewBeforeStarting() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE).edit()
            .putInt("recommendGoal", 0)
            .putInt("recommendEnergy", 1)
            .putInt("recommendEquipment", 0)
            .putInt("fitLevel", 0)
            .putInt("fitTimeMinutes", 10)
            .commit()

        val squat = TrainingCatalog.byId("quick").moves.first { it.name == "Squat không tạ" }
        WorkoutData.save(app, listOf(
            Workout(
                date = LocalDate.now().toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private health note",
                sessionId = UUID.randomUUID().toString(),
                completedMoves = 2,
                selfReportedSets = 1,
                effortLog = "${squat.name}: hiệp 1: Quá sức, tạm dừng"
            )
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val recommendation = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<Button>()
            .first { it.text.toString() == "🐰 Gợi ý chương trình phù hợp" }
        recommendation.performClick()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("The personalized plan preview should open", dialog)
        val dialogRoot = dialog.findViewById<ViewGroup>(android.R.id.custom)
        val previewText = dialogRoot?.let(::views).orEmpty()
            .filterIsInstance<TextView>()
            .joinToString("\n") { it.text.toString() }
        assertTrue(previewText.contains("Check-in trước đây cần xem lại"))
        assertTrue(previewText.contains("Squat không tạ"))
        assertTrue(previewText.contains("Quá sức, tạm dừng"))
        assertTrue(previewText.contains("Chibi không tự xóa, đổi bài"))
        assertTrue(!previewText.contains("private health note"))
        val speakButton = dialogRoot?.let(::views).orEmpty()
            .filterIsInstance<Button>()
            .first { it.text.toString() == "🔊 Nghe tóm tắt hồ sơ" }
        assertTrue(
            speakButton.contentDescription.toString().contains("lưu ý từ check-in trước đây")
        )
        controller.pause().stop().destroy()
    }


    @Test fun repeatedNumericEffortScoresShowAnOptInLighterSessionAction() {
        val today = LocalDate.now()
        val app = RuntimeEnvironment.getApplication()
        WorkoutData.save(app, listOf(
            Workout(
                date = today.minusDays(1).toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private previous health note",
                sessionId = "550e8400-e29b-41d4-a716-446655440125",
                completedMoves = 2,
                selfReportedSets = 2,
                effortScoreLog = "0:7.8"
            ),
            Workout(
                date = today.toString(),
                name = "Guided Workout",
                minutes = 20,
                note = "private latest health note",
                sessionId = "550e8400-e29b-41d4-a716-446655440126",
                completedMoves = 2,
                selfReportedSets = 2,
                effortScoreLog = "0:8.9"
            )
        ))

        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val allViews = views(content)
        val insight = allViews.filterIsInstance<TextView>().first {
            it.text.toString().contains("Hai buổi Guided Workout hợp lệ gần nhất")
        }
        assertEquals(View.VISIBLE, insight.visibility)
        assertTrue(insight.text.toString().contains("8,5/10"))
        assertTrue(!insight.text.toString().contains("private"))

        val action = allViews.filterIsInstance<Button>().first {
            it.text.toString() == "💬 Xem buổi nhẹ hơn theo điểm tự chấm"
        }
        assertEquals(View.VISIBLE, action.visibility)
        action.performClick()

        val input = allViews.filterIsInstance<EditText>().first {
            it.contentDescription?.toString() == "Yêu cầu giáo án bằng tiếng Việt"
        }
        assertTrue(input.text.toString().contains("dựa trên điểm gắng sức tự chấm"))
        assertTrue(input.text.toString().contains("giữ nguyên hiệp đã hoàn thành"))
        assertTrue(input.text.toString().contains("không tự tăng"))

        // A numeric-score insight must also expose the curated recovery preview.
        // Tapping it opens the full exercise library review; it must not start the workout.
        val recoveryAction = allViews.filterIsInstance<Button>().first {
            it.text.toString() == "🌿 Xem bản xem trước buổi nhẹ hơn theo điểm tự chấm"
        }
        assertEquals(View.VISIBLE, recoveryAction.visibility)
        recoveryAction.performClick()

        val previewDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("The recovery CTA must open a reviewed exercise preview", previewDialog)
        assertTrue(previewDialog.isShowing)
        assertEquals(
            "▶ Bắt đầu",
            previewDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text.toString()
        )
        assertEquals(
            "Đóng",
            previewDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).text.toString()
        )
        val previewRoot = previewDialog.findViewById<ViewGroup>(android.R.id.custom)
        val previewViews = previewRoot?.let(::views).orEmpty()
        assertTrue(
            "The preview should include the offline movement illustration",
            previewViews.any { it is WorkoutPoseView }
        )
        assertTrue(
            previewViews.filterIsInstance<TextView>().any {
                it.text.toString().contains("Hình minh họa chuyển động")
            }
        )
        controller.pause().stop().destroy()
    }
}
