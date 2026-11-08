package com.chibialarm.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Original vector stick-figure cue, rendered entirely on device without network or camera. */
class WorkoutPoseView(context: Context) : View(context) {
    var pose: WorkoutPoseGuide.Pose = WorkoutPoseGuide.Pose.STANDING
        set(value) { field = value; contentDescription = WorkoutPoseGuide.label(value); invalidate() }

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
                head(120f, 22f); line(120f, 33f, 118f, 66f)
                line(118f, 46f, 93f, 62f); line(118f, 46f, 145f, 61f)
                line(118f, 66f, 91f, 83f); line(91f, 83f, 91f, 108f)
                line(118f, 66f, 149f, 83f); line(149f, 83f, 149f, 108f)
            }
            WorkoutPoseGuide.Pose.PLANK, WorkoutPoseGuide.Pose.PUSH_UP -> {
                head(54f, 66f); line(66f, 70f, 178f, 67f)
                line(85f, 70f, 85f, 106f); line(85f, 106f, 68f, 106f)
                line(178f, 67f, 204f, 104f); line(204f, 104f, 218f, 104f)
            }
            WorkoutPoseGuide.Pose.LUNGE -> {
                head(116f, 20f); line(116f, 32f, 116f, 69f)
                line(116f, 44f, 95f, 64f); line(116f, 44f, 138f, 64f)
                line(116f, 69f, 83f, 86f); line(83f, 86f, 83f, 109f)
                line(116f, 69f, 151f, 89f); line(151f, 89f, 185f, 89f)
            }
            WorkoutPoseGuide.Pose.STANDING, WorkoutPoseGuide.Pose.REST -> {
                head(120f, 20f); line(120f, 31f, 120f, 71f)
                line(120f, 43f, 94f, 64f); line(120f, 43f, 146f, 64f)
                line(120f, 71f, 100f, 108f); line(120f, 71f, 140f, 108f)
            }
        }
        canvas.restoreToCount(checkpoint)
    }
}
