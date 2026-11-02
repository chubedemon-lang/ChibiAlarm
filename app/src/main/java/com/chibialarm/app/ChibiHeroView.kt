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
    // Avoid allocating vector paths on every animation frame.
    private val smilePath = Path().apply {
        moveTo(153f, 130f)
        quadTo(160f, 139f, 167f, 130f)
    }
    private val crownPath = Path().apply {
        moveTo(137f, 70f)
        lineTo(140f, 47f)
        lineTo(151f, 58f)
        lineTo(160f, 39f)
        lineTo(169f, 58f)
        lineTo(180f, 47f)
        lineTo(183f, 70f)
        close()
    }
    private val startedAt = android.os.SystemClock.uptimeMillis()
    private var happyUntil = 0L
    var sceneState = ChibiSceneState()
    var outfit: ChibiCosmetics.Outfit = ChibiCosmetics.Outfit.STARTER
        set(value) {
            field = value
            invalidate()
        }
    private var lastFrameMs = android.os.SystemClock.uptimeMillis()
    private val reducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f
    private val tapGesture = ChibiTapGesture(
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    )
    private var waterUntil = 0L
    private var transitionUntil = 0L
    // Recalculate ambient lighting at most once per minute; do not query the
    // wall clock on every Canvas frame (important on low-power devices).
    private var nextLightCheckMs = 0L
    private var lightPhase = ChibiDayCycle.Phase.DAY
    var onRoomChanged: ((Boolean) -> Unit)? = null
    var onGardenAction: (() -> Unit)? = null
    var onPet: (() -> Unit)? = null
    var onSceneAction: ((ChibiSceneActions.Target) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "Chạm Chibi để vuốt ve, chạm sàn để đi, chạm cửa để chuyển phòng và khu vườn"
    }

    override fun performClick(): Boolean {
        super.performClick()
        happyUntil = android.os.SystemClock.uptimeMillis() + 1100L
        onPet?.invoke()
        invalidate()
        return true
    }

    /** Accessible alternative to the small visual doorway. */
    fun toggleScene() {
        sceneState.switchScene()
        showRoomChange()
    }

    /** TalkBack/large-target equivalent of tapping a garden planter. */
    fun waterGarden(): Boolean {
        if (sceneState.scene != ChibiSceneState.Scene.GARDEN) return false
        waterUntil = android.os.SystemClock.uptimeMillis() + 850L
        onGardenAction?.invoke()
        invalidate()
        return true
    }

    private fun showRoomChange() {
        transitionUntil = if (reducedMotion) 0L else android.os.SystemClock.uptimeMillis() + 350L
        onRoomChanged?.invoke(sceneState.scene == ChibiSceneState.Scene.GARDEN)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapGesture.begin(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // A scroll or swipe must never pet, claim a reward or change rooms.
                // Let the parent ScrollView intercept movement naturally.
                tapGesture.move(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                tapGesture.cancel()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tapGesture.end(event.x, event.y)) return true
                val scale = minOf(width / 320f, height / 240f)
                if (!scale.isFinite() || scale <= 0f) return true
                val x = (event.x - (width - 320f * scale) / 2f) / scale
                val y = (event.y - (height - 240f * scale) / 2f) / scale
                when (sceneState.tap(x, y)) {
                    ChibiSceneState.Action.PET -> performClick()
                    ChibiSceneState.Action.DOOR -> showRoomChange()
                    ChibiSceneState.Action.WALK -> {
                        if (reducedMotion) sceneState.finishWalk()
                        invalidate()
                    }
                    ChibiSceneState.Action.WATER -> { waterGarden() }
                    ChibiSceneState.Action.BED -> onSceneAction?.invoke(ChibiSceneActions.Target.BED)
                    ChibiSceneState.Action.DESK -> onSceneAction?.invoke(ChibiSceneActions.Target.DESK)
                    ChibiSceneState.Action.TRAINING -> onSceneAction?.invoke(ChibiSceneActions.Target.TRAINING)
                    ChibiSceneState.Action.NONE -> Unit
                }
                return true
            }
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
        val now = android.os.SystemClock.uptimeMillis()
        val dt = ((now - lastFrameMs).coerceIn(0L, 50L) / 1000f)
        lastFrameMs = now
        val heroX = if (reducedMotion) sceneState.finishWalk() else sceneState.advance(dt)
        val outdoor = sceneState.scene == ChibiSceneState.Scene.GARDEN
        if (now >= nextLightCheckMs) {
            lightPhase = ChibiDayCycle.phase(java.time.LocalTime.now().hour)
            nextLightCheckMs = now + 60_000L
        }
        paint.color = when (lightPhase) {
            ChibiDayCycle.Phase.DAY ->
                if (outdoor) Color.rgb(209, 245, 221) else Color.rgb(230, 242, 255)
            ChibiDayCycle.Phase.SUNSET -> Color.rgb(250, 211, 194)
            ChibiDayCycle.Phase.NIGHT -> Color.rgb(57, 70, 117)
        }
        canvas.drawRoundRect(0f, 0f, 320f, 240f, 24f, 24f, paint)
        val cloudColor = if (lightPhase == ChibiDayCycle.Phase.NIGHT)
            Color.rgb(128, 141, 179) else Color.WHITE
        circle(canvas, cloudColor, 53f, 43f, 17f)
        circle(canvas, cloudColor, 73f, 43f, 22f)
        circle(canvas, cloudColor, 250f, 59f, 23f)
        circle(canvas, cloudColor, 270f, 59f, 16f)
        oval(canvas, Color.rgb(145, 199, 151), 0f, 195f, 320f, 265f)
        oval(canvas, Color.rgb(111, 163, 121), 95f, 209f, 225f, 227f)
        // Hand-drawn interactive bedroom props: bed, focus desk and training mat.
        if (!outdoor) {
        paint.color = Color.rgb(164, 112, 178)
        canvas.drawRoundRect(9f, 122f, 79f, 199f, 9f, 9f, paint)
        paint.color = Color.rgb(255, 239, 246)
        canvas.drawRoundRect(13f, 130f, 75f, 195f, 8f, 8f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(18f, 134f, 70f, 150f, 7f, 7f, paint)
        paint.color = Color.rgb(169, 113, 75)
        canvas.drawRoundRect(241f, 126f, 311f, 144f, 4f, 4f, paint)
        canvas.drawRect(247f, 143f, 254f, 195f, paint)
        canvas.drawRect(298f, 143f, 305f, 195f, paint)
        paint.color = Color.rgb(95, 120, 180)
        canvas.drawRoundRect(259f, 101f, 294f, 126f, 4f, 4f, paint)
        paint.color = Color.rgb(208, 234, 249)
        canvas.drawRoundRect(263f, 105f, 290f, 121f, 2f, 2f, paint)
        paint.color = Color.rgb(112, 174, 190)
        canvas.drawRoundRect(12f, 212f, 86f, 230f, 8f, 8f, paint)
        } else {
            if (lightPhase == ChibiDayCycle.Phase.NIGHT) {
                // Moon and a few static stars: legible without extra animations.
                circle(canvas, Color.rgb(248, 243, 215), 274f, 43f, 18f)
                circle(canvas, Color.rgb(255, 249, 208), 112f, 36f, 2.5f)
                circle(canvas, Color.rgb(255, 249, 208), 208f, 21f, 2f)
                circle(canvas, Color.rgb(255, 249, 208), 225f, 76f, 2.5f)
            } else {
                paint.color = if (lightPhase == ChibiDayCycle.Phase.SUNSET)
                    Color.rgb(246, 142, 102) else Color.rgb(255, 218, 118)
                canvas.drawCircle(274f, 43f, 23f, paint)
            }
            paint.color = Color.rgb(92, 164, 105)
            canvas.drawRoundRect(15f, 140f, 82f, 198f, 10f, 10f, paint)
            canvas.drawRoundRect(242f, 140f, 307f, 198f, 10f, 10f, paint)
            if (now < waterUntil) {
                paint.color = Color.rgb(58, 147, 223)
                canvas.drawCircle(50f, 117f, 5f, paint)
                canvas.drawCircle(272f, 116f, 5f, paint)
                canvas.drawCircle(60f, 105f, 3f, paint)
            }
        }
        paint.color = Color.rgb(172, 116, 90)
        canvas.drawRoundRect(138f, 228f, 182f, 239f, 5f, 5f, paint)
        val bob = if (reducedMotion) 0f else sin((now - startedAt) / 420.0).toFloat() * 3f
        val gait = if (!reducedMotion && sceneState.isWalking) sin(now / 95.0).toFloat() * 5f else 0f
        canvas.save()
        canvas.translate(heroX - 160f, bob)
        // Face the direction of travel without extra bitmap assets or allocations.
        // Mirror around the mascot's local center, not around the entire scene.
        if (!sceneState.facingRight) canvas.scale(-1f, 1f, 160f, 135f)
        if (!reducedMotion && sceneState.isWalking) {
            canvas.rotate(sin(now / 85.0).toFloat() * 2f, 160f, 175f)
        }
        // Ears, body, hands and feet form a distinct mascot silhouette.
        oval(canvas, Color.rgb(251, 244, 255), 114f, 18f, 144f, 108f)
        oval(canvas, Color.rgb(251, 244, 255), 176f, 18f, 206f, 108f)
        oval(canvas, Color.rgb(245, 175, 202), 122f, 31f, 136f, 92f)
        oval(canvas, Color.rgb(245, 175, 202), 184f, 31f, 198f, 92f)
        oval(canvas, Color.rgb(114, 116, 212), 119f, 137f, 201f, 211f)
        oval(canvas, Color.rgb(251, 244, 255), 106f, 148f, 133f, 183f)
        oval(canvas, Color.rgb(251, 244, 255), 187f, 148f, 214f, 183f)
        // Opposing foot motion makes the walk readable without external sprite assets.
        oval(canvas, Color.rgb(78, 83, 165), 124f, 199f + gait, 157f, 216f + gait)
        oval(canvas, Color.rgb(78, 83, 165), 163f, 199f - gait, 196f, 216f - gait)
        circle(canvas, Color.rgb(251, 244, 255), 160f, 111f, 69f)
        // Outfits are unlocked by real RPG levels, not by repeatedly tapping the avatar.
        if (outfit != ChibiCosmetics.Outfit.STARTER) {
            paint.color = Color.rgb(240, 106, 130)
            canvas.drawRoundRect(127f, 157f, 193f, 172f, 8f, 8f, paint)
            canvas.drawRoundRect(181f, 165f, 194f, 194f, 5f, 5f, paint)
            circle(canvas, Color.rgb(255, 224, 164), 137f, 165f, 3f)
        }
        if (outfit == ChibiCosmetics.Outfit.CROWN) {
            paint.color = Color.rgb(245, 190, 58)
            canvas.drawPath(crownPath, paint)
            circle(canvas, Color.rgb(255, 252, 225), 160f, 57f, 4f)
        }
        // Brief blink every few seconds, using only the existing vector primitives.
        if (!reducedMotion && (now - startedAt) % 4200L < 160L) {
            paint.color = Color.rgb(68, 62, 104)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawLine(127f, 113f, 140f, 113f, paint)
            canvas.drawLine(180f, 113f, 193f, 113f, paint)
            paint.style = Paint.Style.FILL
        } else {
            oval(canvas, Color.rgb(68, 62, 104), 127f, 104f, 140f, 122f)
            oval(canvas, Color.rgb(68, 62, 104), 180f, 104f, 193f, 122f)
            circle(canvas, Color.WHITE, 133f, 109f, 3.5f)
            circle(canvas, Color.WHITE, 186f, 109f, 3.5f)
        }
        oval(canvas, Color.rgb(250, 176, 193), 107f, 123f, 130f, 136f)
        oval(canvas, Color.rgb(250, 176, 193), 190f, 123f, 213f, 136f)
        paint.color = Color.rgb(87, 67, 110)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawPath(smilePath, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        if (android.os.SystemClock.uptimeMillis() < happyUntil) {
            paint.color = Color.rgb(231, 79, 140)
            paint.textSize = 30f
            canvas.drawText("♥", 219f, 70f, paint)
            paint.textSize = 22f
            canvas.drawText("♥", 91f, 86f, paint)
        }
        if (now < transitionUntil) {
            val remaining = ((transitionUntil - now).toFloat() / 350f).coerceIn(0f, 1f)
            paint.color = Color.argb((remaining * 190).toInt(), 255, 255, 255)
            canvas.drawRoundRect(0f, 0f, 320f, 240f, 24f, 24f, paint)
        }
        canvas.restoreToCount(save)
        val delay = ChibiFramePolicy.nextDelayMs(
            visible = isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE,
            reducedMotion = reducedMotion,
            walking = sceneState.isWalking,
            activeEffect = now < happyUntil || now < waterUntil || now < transitionUntil
        )
        if (delay != null) postInvalidateDelayed(delay)
    }
}
