package com.chibialarm.app

import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure, bounded mascot animation. Never reads account state or grants RPG rewards.
 * Reduced-motion devices keep a completely still mascot.
 */
internal object ChibiCharacterMotion {
    private const val IDLE_PERIOD_MS = 3_200L
    const val PET_REACTION_MS = 1_100L

    /** Subtle idle breathing, anchored at the mascot's feet by the Canvas renderer. */
    fun breathScale(elapsedMs: Long, reducedMotion: Boolean): Float {
        if (reducedMotion) return 1f
        val phase = (elapsedMs.coerceAtLeast(0L) % IDLE_PERIOD_MS).toDouble() /
            IDLE_PERIOD_MS.toDouble()
        return (1.0 + 0.012 * sin(2.0 * PI * phase)).toFloat()
    }

    /**
     * Single gentle hop on pet; zero at both ends of the reaction window.
     * A stale or invalid deadline must not leave the character hovering.
     */
    fun petHop(nowMs: Long, happyUntilMs: Long, reducedMotion: Boolean): Float {
        if (reducedMotion || nowMs < 0L || happyUntilMs <= nowMs) return 0f
        val remaining = happyUntilMs - nowMs
        if (remaining !in 1L..PET_REACTION_MS) return 0f
        val progress = (PET_REACTION_MS - remaining).toDouble() / PET_REACTION_MS
        return (-8.0 * sin(PI * progress)).toFloat()
    }
}
