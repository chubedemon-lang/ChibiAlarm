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
    /**
     * Two hearts rise briefly after a deliberate pet. These values are presentation-only:
     * the reward is still guarded by the durable, once-per-day Room receipt.
     * Reject stale deadlines rather than drawing hearts indefinitely.
     */
    fun petHeartRise(remainingMs: Long, reducedMotion: Boolean): Float {
        if (reducedMotion || remainingMs !in 1L..PET_REACTION_MS) return 0f
        return 18f * (1f - remainingMs.toFloat() / PET_REACTION_MS)
    }

    fun petHeartAlpha(remainingMs: Long, reducedMotion: Boolean): Int {
        if (remainingMs !in 1L..PET_REACTION_MS) return 0
        if (reducedMotion) return 255
        return (255f * remainingMs.toFloat() / PET_REACTION_MS).toInt().coerceIn(0, 255)
    }

    /** Dust puffs at the mascot's feet: decorative only, never a reward event. */
    fun footstepDustAlpha(elapsedMs: Long, walking: Boolean, reducedMotion: Boolean): Int {
        if (!walking || reducedMotion) return 0
        val phase = (elapsedMs.coerceAtLeast(0L) % 480L).toFloat() / 480f
        return ((1f - phase) * 110f).toInt().coerceIn(0, 110)
    }

    fun footstepDustRadius(elapsedMs: Long, walking: Boolean, reducedMotion: Boolean): Float {
        if (!walking || reducedMotion) return 0f
        val phase = (elapsedMs.coerceAtLeast(0L) % 480L).toFloat() / 480f
        return 2f + phase * 4f
    }

}
