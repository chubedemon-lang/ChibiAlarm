package com.chibialarm.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.MotionEvent
import kotlin.math.sin

/** Lightweight offline 2D mascot scene; no downloaded assets or permissions. */
class ChibiHeroView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startedAt = android.os.SystemClock.uptimeMillis()
    private var happyUntil = 0L
    var onPet: (() -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "Chạm để vuốt ve nhân vật Chibi"
    }

    override fun performClick(): Boolean {
        super.performClick()
        happyUntil = android.os.SystemClock.uptimeMillis() + 1100L
        onPet?.invoke()
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val scale = minOf(width / 320f, height / 240f)
            if (scale <= 0f) return false
            val x = (event.x - (width - 320f * scale) / 2f) / scale
            val y = (event.y - (height - 240f * scale) / 2f) / scale
            if (x in 88f..230f && y in 15f..218f) return performClick()
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (240 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    private fun oval(canvas: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawOval(l, t, r, b, paint)
    }

    private fun circle(canvas: Canvas, color: Int, x: Float, y: Float, radius: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 320f, height / 240f)
        val save = canvas.save()
        canvas.translate((width - 320f * scale) / 2f, (height - 240f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.color = Color.rgb(230, 242, 255)
        canvas.drawRoundRect(0f, 0f, 320f, 240f, 24f, 24f, paint)
        circle(canvas, Color.rgb(255, 255, 255), 53f, 43f, 17f)
        circle(canvas, Color.rgb(255, 255, 255), 73f, 43f, 22f)
        circle(canvas, Color.rgb(255, 255, 255), 250f, 59f, 23f)
        circle(canvas, Color.rgb(255, 255, 255), 270f, 59f, 16f)
        oval(canvas, Color.rgb(145, 199, 151), 0f, 195f, 320f, 265f)
        oval(canvas, Color.rgb(111, 163, 121), 95f, 209f, 225f, 227f)
        val bob = sin((android.os.SystemClock.uptimeMillis() - startedAt) / 420.0).toFloat() * 3f
        canvas.save()
        canvas.translate(0f, bob)
        // Ears, body, hands and feet form a distinct mascot silhouette.
        oval(canvas, Color.rgb(251, 244, 255), 114f, 18f, 144f, 108f)
        oval(canvas, Color.rgb(251, 244, 255), 176f, 18f, 206f, 108f)
        oval(canvas, Color.rgb(245, 175, 202), 122f, 31f, 136f, 92f)
        oval(canvas, Color.rgb(245, 175, 202), 184f, 31f, 198f, 92f)
        oval(canvas, Color.rgb(114, 116, 212), 119f, 137f, 201f, 211f)
        oval(canvas, Color.rgb(251, 244, 255), 106f, 148f, 133f, 183f)
        oval(canvas, Color.rgb(251, 244, 255), 187f, 148f, 214f, 183f)
        oval(canvas, Color.rgb(78, 83, 165), 124f, 199f, 157f, 216f)
        oval(canvas, Color.rgb(78, 83, 165), 163f, 199f, 196f, 216f)
        circle(canvas, Color.rgb(251, 244, 255), 160f, 111f, 69f)
        oval(canvas, Color.rgb(68, 62, 104), 127f, 104f, 140f, 122f)
        oval(canvas, Color.rgb(68, 62, 104), 180f, 104f, 193f, 122f)
        circle(canvas, Color.WHITE, 133f, 109f, 3.5f)
        circle(canvas, Color.WHITE, 186f, 109f, 3.5f)
        oval(canvas, Color.rgb(250, 176, 193), 107f, 123f, 130f, 136f)
        oval(canvas, Color.rgb(250, 176, 193), 190f, 123f, 213f, 136f)
        paint.color = Color.rgb(87, 67, 110)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        val smile = Path().apply { moveTo(153f, 130f); quadTo(160f, 139f, 167f, 130f) }
        canvas.drawPath(smile, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        if (android.os.SystemClock.uptimeMillis() < happyUntil) {
            paint.color = Color.rgb(231, 79, 140)
            paint.textSize = 30f
            canvas.drawText("♥", 219f, 70f, paint)
            paint.textSize = 22f
            canvas.drawText("♥", 91f, 86f, paint)
        }
        canvas.restoreToCount(save)
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateDelayed(50)
    }
}
