package com.chibialarm.app

import kotlin.math.abs

/**
 * Deterministic, offline scene controller. This is intentionally independent of Android and
 * the RPG reward ledger: walking, switching rooms and watering plants never mint XP.
 */
class ChibiSceneState {
    enum class Scene { BEDROOM, GARDEN }
    enum class Action { NONE, PET, BED, DESK, TRAINING, DOOR, WALK, WATER }

    var scene: Scene = Scene.BEDROOM
        private set
    var heroX: Float = 160f
        private set
    var destinationX: Float = 160f
        private set
    /** Direction is presentation-only: it never grants rewards or changes collisions. */
    var facingRight: Boolean = true
        private set

    /** Temporary scene state for Android rotation/process recreation, not RPG progress. */
    data class Snapshot(val scene: Scene, val heroX: Float, val destinationX: Float, val facingRight: Boolean = true)

    fun snapshot(): Snapshot = Snapshot(scene, heroX, destinationX, facingRight)

    /** Reject malformed saved state rather than moving the hero outside the walkable floor. */
    fun restore(saved: Snapshot): Boolean {
        if (!saved.heroX.isFinite() || !saved.destinationX.isFinite() ||
            saved.heroX !in 116f..204f || saved.destinationX !in 116f..204f) return false
        scene = saved.scene
        heroX = saved.heroX
        destinationX = saved.destinationX
        facingRight = saved.facingRight
        return true
    }

    fun switchScene(): Scene {
        scene = if (scene == Scene.BEDROOM) Scene.GARDEN else Scene.BEDROOM
        destinationX = 160f
        heroX = 160f
        return scene
    }

    fun tap(x: Float, y: Float): Action {
        if (!x.isFinite() || !y.isFinite() || x !in 0f..320f || y !in 0f..240f) {
            return Action.NONE
        }
        if (x in 138f..182f && y in 225f..240f) {
            switchScene()
            return Action.DOOR
        }
        if (scene == Scene.GARDEN) {
            if (y in 125f..198f && (x in 15f..85f || x in 240f..307f)) {
                return Action.WATER
            }
        } else {
            if (x in 9f..79f && y in 122f..199f) return Action.BED
            if (x in 241f..311f && y in 101f..195f) return Action.DESK
            if (x in 12f..86f && y in 212f..230f) return Action.TRAINING
        }
        // Match the moving character, not the original fixed center.
        if (abs(x - heroX) <= 48f && y in 43f..190f) return Action.PET
        if (x in 88f..238f && y in 195f..224f) {
            val nextX = x.coerceIn(116f, 204f)
            if (nextX != heroX) facingRight = nextX > heroX
            destinationX = nextX
            return Action.WALK
        }
        return Action.NONE
    }

    fun advance(dtSeconds: Float): Float {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return heroX
        val maxStep = 105f * dtSeconds.coerceAtMost(0.05f)
        heroX += (destinationX - heroX).coerceIn(-maxStep, maxStep)
        return heroX
    }

    /** Accessibility: finish movement immediately when Android animations are disabled. */
    fun finishWalk(): Float {
        heroX = destinationX
        return heroX
    }

    val isWalking: Boolean
        get() = abs(destinationX - heroX) > 0.5f
}
