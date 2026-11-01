package com.chibialarm.app

/**
 * Frame pacing for the lightweight Canvas scene. Idle animations need fewer frames
 * than walking; Android's system "remove animations" setting is respected.
 * Pure policy so it can be tested without a device or rendering a Canvas.
 */
object ChibiFramePolicy {
    const val WALK_MS = 33L
    const val IDLE_MS = 100L
    const val EFFECT_MS = 33L

    /**
     * Null means no frame needs to be scheduled. Explicit user interaction
     * calls invalidate(), so a motion-disabled scene remains responsive.
     */
    fun nextDelayMs(
        visible: Boolean,
        reducedMotion: Boolean,
        walking: Boolean,
        activeEffect: Boolean
    ): Long? = when {
        !visible -> null
        reducedMotion -> if (activeEffect) EFFECT_MS else null
        walking -> WALK_MS
        activeEffect -> EFFECT_MS
        else -> IDLE_MS
    }
}
