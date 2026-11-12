package com.chibialarm.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.animation.ValueAnimator
import android.animation.ValueAnimator.areAnimatorsEnabled

/** Original vector stick-figure cue, rendered entirely on device without network or camera. */
class WorkoutPoseView(context: Context) : View(context) {
    var pose: WorkoutPoseGuide.Pose = WorkoutPoseGuide.Pose.STANDING
        set(value) { field = value; contentDescription = WorkoutPoseMotion.accessibilityCue(value, false); refreshMotion() }

    private var movementPhase = false

    /** Current alternate illustration phase, exposed for matching one-shot spoken guidance. */
    val isInAlternatePose: Boolean
        get() = movementPhase

    private var animator: ValueAnimator? = null

    /** Allows a modal walkthrough to pause the background illustration while its own preview animates. */
    var motionEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            refreshMotion()
        }

    /** Optional visible phase label used by the step-by-step exercise guide. */
    var onPhaseChanged: ((Boolean) -> Unit)? = null
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(65, 43, 105)
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMotion()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) refreshMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshMotion()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun refreshMotion() {
        animator?.cancel()
        animator = null
        movementPhase = false
        onPhaseChanged?.invoke(false)
        contentDescription = WorkoutPoseMotion.accessibilityCue(pose, false)
        if (!motionEnabled || !isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE ||
            !WorkoutPoseMotion.hasMotion(pose) ||
            !areAnimatorsEnabled()) {
            invalidate()
            return
        }
        animator = ValueAnimator.ofInt(0, 1).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val next = (it.animatedValue as Int) == 1
                if (next != movementPhase) {
                    movementPhase = next
                    contentDescription = WorkoutPoseMotion.accessibilityCue(pose, next)
                    onPhaseChanged?.invoke(next)
                    invalidate()
                }
            }
            start()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(94, 65, 145)
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (140 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(desired * 2, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.scale(width / 240f, height / 120f)
        canvas.drawColor(Color.rgb(245, 239, 252))
        paint.color = Color.rgb(94, 65, 145)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(x1, y1, x2, y2, paint)
        fun head(x: Float, y: Float) = canvas.drawCircle(x, y, 10f, paint)
        when (pose) {
            WorkoutPoseGuide.Pose.SQUAT -> {
                head(120f, if (movementPhase) 12f else 22f); line(120f, if (movementPhase) 23f else 33f, 118f, 66f)
                line(118f, 46f, 93f, 62f); line(118f, 46f, 145f, 61f)
                line(118f, 66f, 91f, 83f); line(91f, 83f, 91f, 108f)
                line(118f, 66f, 149f, 83f); line(149f, 83f, 149f, 108f)
            }
            WorkoutPoseGuide.Pose.HIP_HINGE -> {
                if (movementPhase) {
                    // Keep the torso as one straight diagonal while the hips travel backward.
                    head(160f, 29f); line(151f, 39f, 121f, 69f)
                    line(151f, 43f, 177f, 64f); line(177f, 64f, 190f, 67f)
                    line(121f, 69f, 108f, 108f); line(121f, 69f, 143f, 84f)
                    line(143f, 84f, 155f, 108f)
                } else {
                    head(120f, 20f); line(120f, 31f, 120f, 69f)
                    line(120f, 43f, 94f, 64f); line(120f, 43f, 146f, 64f)
                    line(120f, 69f, 103f, 108f); line(120f, 69f, 139f, 108f)
                }
            }
            WorkoutPoseGuide.Pose.PLANK, WorkoutPoseGuide.Pose.PUSH_UP -> {
                val upperY = if (pose == WorkoutPoseGuide.Pose.PUSH_UP && movementPhase) 87f else 67f
                head(54f, upperY); line(66f, upperY + 3f, 178f, upperY)
                line(85f, upperY + 3f, 85f, 106f); line(85f, 106f, 68f, 106f)
                line(178f, upperY, 204f, 104f); line(204f, 104f, 218f, 104f)
            }
            WorkoutPoseGuide.Pose.KNEE_PUSH_UP -> {
                // Knees remain on the floor; do not reuse the full plank push-up drawing.
                val shoulderY = if (movementPhase) 82f else 66f
                head(48f, shoulderY - 8f)
                line(60f, shoulderY, 143f, 72f)
                line(76f, shoulderY + 2f, 118f, if (movementPhase) 86f else 77f)
                line(118f, if (movementPhase) 86f else 77f, 181f, 104f)
                line(143f, 72f, 150f, 101f)
                line(150f, 101f, 184f, 104f)
                line(181f, 104f, 194f, 104f)
            }
            WorkoutPoseGuide.Pose.WALL_PUSH_UP -> {
                // A vertical stance and a visible wall distinguish this from a floor push-up.
                line(190f, 12f, 190f, 110f)
                head(78f, 18f); line(78f, 30f, 78f, 72f)
                line(78f, 72f, 62f, 105f); line(78f, 72f, 99f, 105f)
                line(62f, 105f, 48f, 105f); line(99f, 105f, 113f, 105f)
                if (movementPhase) {
                    line(78f, 44f, 132f, 58f)
                    line(132f, 58f, 177f, 45f)
                } else {
                    line(78f, 44f, 132f, 45f)
                    line(132f, 45f, 177f, 45f)
                }
            }
            WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> {
                // Hands rest on a raised, stable surface; the body stays diagonally aligned.
                line(164f, 82f, 225f, 82f)
                line(174f, 82f, 174f, 108f); line(216f, 82f, 216f, 108f)
                head(48f, 25f); line(60f, 32f, 133f, 62f)
                line(76f, 38f, if (movementPhase) 118f else 132f, if (movementPhase) 62f else 70f)
                line(if (movementPhase) 118f else 132f, if (movementPhase) 62f else 70f, 174f, 82f)
                line(133f, 62f, 151f, 91f); line(151f, 91f, 174f, 104f)
                line(133f, 62f, 153f, 103f); line(153f, 103f, 174f, 104f)
            }
            WorkoutPoseGuide.Pose.LUNGE -> {
                val headY = if (movementPhase) 30f else 20f
                head(116f, headY); line(116f, headY + 12f, 116f, 69f)
                line(116f, 44f, 95f, 64f); line(116f, 44f, 138f, 64f)
                line(116f, 69f, 83f, 86f); line(83f, 86f, 83f, 109f)
                line(116f, 69f, 151f, 89f); line(151f, 89f, 185f, 89f)
            }
            WorkoutPoseGuide.Pose.BRIDGE -> {
                val hipY = if (movementPhase) 55f else 73f
                head(39f, 83f); line(50f, 84f, 77f, 84f)
                line(77f, 84f, 124f, hipY); line(124f, hipY, 164f, hipY)
                line(164f, hipY, 190f, 101f); line(190f, 101f, 211f, 101f)
                line(77f, 84f, 69f, 103f); line(69f, 103f, 51f, 103f)
            }
            WorkoutPoseGuide.Pose.BIRD_DOG -> {
                head(72f, 55f); line(83f, 65f, 141f, 72f)
                line(99f, 67f, 96f, 105f); line(99f, 67f, if (movementPhase) 43f else 64f, if (movementPhase) 44f else 54f)
                line(141f, 72f, 145f, 104f); line(141f, 72f, if (movementPhase) 207f else 181f, if (movementPhase) 45f else 61f)
            }
            WorkoutPoseGuide.Pose.DEAD_BUG -> {
                head(42f, 86f); line(54f, 86f, 119f, 86f)
                line(75f, 84f, 58f, 43f); line(75f, 84f, 99f, 44f)
                line(119f, 86f, 145f, 63f)
                line(145f, 63f, if (movementPhase) 177f else 151f, if (movementPhase) 91f else 35f)
                line(119f, 86f, 94f, 64f); line(94f, 64f, 91f, if (movementPhase) 35f else 91f)
            }
            WorkoutPoseGuide.Pose.CAT_COW -> {
                val spineY = if (movementPhase) 57f else 87f
                head(67f, 68f); line(79f, 76f, 111f, spineY)
                line(111f, spineY, 151f, spineY); line(151f, spineY, 169f, 76f)
                line(91f, 78f, 91f, 105f); line(160f, 78f, 160f, 105f)
            }
            WorkoutPoseGuide.Pose.MARCH -> {
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, if (movementPhase) 52f else 64f)
                line(120f, 43f, 146f, if (movementPhase) 64f else 52f)
                line(120f, 71f, 120f, 108f)
                line(120f, 71f, if (movementPhase) 154f else 88f, 86f)
                line(if (movementPhase) 154f else 88f, 86f, if (movementPhase) 166f else 76f, 108f)
            }
            WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> {
                // A visible chair/rail communicates that light support is part of this move.
                line(184f, 48f, 224f, 48f); line(188f, 48f, 188f, 108f); line(220f, 48f, 220f, 108f)
                head(102f, 20f); line(102f, 31f, 102f, 71f)
                line(102f, 43f, 84f, 55f); line(102f, 43f, 151f, 48f); line(151f, 48f, 184f, 48f)
                line(102f, 71f, 91f, 108f)
                val kneeX = if (movementPhase) 146f else 115f
                val kneeY = if (movementPhase) 83f else 108f
                line(102f, 71f, kneeX, kneeY); line(kneeX, kneeY, if (movementPhase) 164f else 115f, 108f)
            }
            WorkoutPoseGuide.Pose.HIP_ABDUCTION -> {
                // Keep one leg grounded while the other moves sideways; chair remains in reach.
                line(184f, 48f, 224f, 48f); line(188f, 48f, 188f, 108f); line(220f, 48f, 220f, 108f)
                head(105f, 20f); line(105f, 31f, 105f, 71f)
                line(105f, 43f, 86f, 55f); line(105f, 43f, 153f, 48f); line(153f, 48f, 184f, 48f)
                line(105f, 71f, 96f, 108f)
                val sideX = if (movementPhase) 151f else 119f
                val sideY = if (movementPhase) 76f else 108f
                line(105f, 71f, sideX, sideY); line(sideX, sideY, if (movementPhase) 171f else 119f, 108f)
            }
            WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> {
                // Standing shoulder-blade retraction; the movement stays small and controlled.
                head(120f, 20f); line(120f, 31f, 120f, 72f)
                line(120f, 43f, 94f, 51f); line(120f, 43f, 146f, 51f)
                if (movementPhase) {
                    line(94f, 51f, 108f, 60f); line(108f, 60f, 120f, 55f)
                    line(146f, 51f, 132f, 60f); line(132f, 60f, 120f, 55f)
                } else {
                    line(94f, 51f, 90f, 76f); line(146f, 51f, 150f, 76f)
                }
                line(120f, 72f, 101f, 108f); line(120f, 72f, 139f, 108f)
            }
            WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> {
                // Forearms meet a visible wall; wrists stay neutral rather than bearing floor load.
                line(190f, 12f, 190f, 110f)
                head(76f, 18f); line(76f, 30f, 76f, 72f)
                line(76f, 72f, 60f, 105f); line(76f, 72f, 98f, 105f)
                line(60f, 105f, 46f, 105f); line(98f, 105f, 112f, 105f)
                val elbowX = if (movementPhase) 122f else 132f
                val elbowY = if (movementPhase) 57f else 48f
                line(76f, 44f, elbowX, elbowY)
                line(elbowX, elbowY, 177f, 45f)
            }
            WorkoutPoseGuide.Pose.SIDE_STEP -> {
                // One foot stays planted while the other moves laterally; no jumping is implied.
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, 60f); line(120f, 43f, 146f, 60f)
                line(120f, 71f, 101f, 108f)
                val sideKnee = if (movementPhase) 164f else 139f
                line(120f, 71f, sideKnee, 87f)
                line(sideKnee, 87f, sideKnee + 15f, 108f)
            }
            WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> {
                // Stable chair and a small seated knee lift, distinct from standing marching.
                line(70f, 69f, 166f, 69f); line(78f, 69f, 78f, 108f); line(158f, 69f, 158f, 108f)
                head(112f, 18f); line(112f, 30f, 112f, 68f)
                line(112f, 43f, 91f, 60f); line(112f, 43f, 134f, 60f)
                line(112f, 68f, 91f, 86f); line(91f, 86f, 91f, 106f)
                val kneeY = if (movementPhase) 77f else 94f
                line(112f, 68f, 145f, kneeY)
                line(145f, kneeY, 160f, 106f)
            }
            WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH -> {
                // Seated on a stable chair with one heel resting on the floor.
                line(75f, 67f, 151f, 67f); line(82f, 67f, 82f, 108f); line(145f, 67f, 145f, 108f)
                head(100f, 19f); line(100f, 31f, 100f, 66f)
                line(100f, 44f, 83f, 62f); line(100f, 44f, 130f, 62f)
                line(100f, 66f, 119f, 84f); line(119f, 84f, 134f, 105f)
                line(100f, 66f, 155f, 66f); line(155f, 66f, 205f, 66f)
            }
            WorkoutPoseGuide.Pose.STRETCH -> {
                head(120f, 22f); line(120f, 33f, 120f, 73f)
                line(120f, 45f, 87f, 20f); line(87f, 20f, 83f, 7f)
                line(120f, 45f, 153f, 20f); line(153f, 20f, 157f, 7f)
                line(120f, 73f, 101f, 108f); line(120f, 73f, 139f, 108f)
            }
            WorkoutPoseGuide.Pose.SIDE_PLANK -> {
                head(54f, 48f); line(66f, 54f, 162f, 72f)
                line(162f, 72f, 204f, 99f); line(204f, 99f, 220f, 99f)
                line(91f, 59f, 91f, 101f); line(91f, 101f, 72f, 101f)
            }
            WorkoutPoseGuide.Pose.CALF_RAISE -> {
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, 64f); line(120f, 43f, 146f, 64f)
                line(120f, 71f, 102f, 106f)
                line(120f, 71f, 140f, if (movementPhase) 91f else 106f)
                line(140f, if (movementPhase) 91f else 106f, 156f, 106f)
            }
            WorkoutPoseGuide.Pose.BALANCE -> {
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, 64f); line(120f, 43f, 146f, 64f)
                line(120f, 71f, 120f, 108f)
                line(120f, 71f, 151f, 84f); line(151f, 84f, 143f, 101f)
            }
            WorkoutPoseGuide.Pose.STANDING, WorkoutPoseGuide.Pose.REST -> {
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, 64f); line(120f, 43f, 146f, 64f)
                line(120f, 71f, 100f, 108f); line(120f, 71f, 140f, 108f)
            }
        }
        canvas.drawText(WorkoutPoseMotion.phaseCue(pose, movementPhase), 8f, 16f, cuePaint)
        canvas.restoreToCount(checkpoint)
    }
}
