package com.chibialarm.app

import kotlin.math.PI
import kotlin.math.sin

/**
 * Small, deterministic, allocation-free ambient animation for the offline Canvas world.
 * The values affect presentation only: never reward, account or persistent RPG state.
 */
internal object ChibiAmbientMotion {
    private const val CLOUD_PERIOD_MS = 24_000L
    private const val FIREFLY_PERIOD_MS = 3_600L

    /** Clouds drift gently without moving their hit targets or scene geometry. */
    fun cloudOffset(elapsedMs: Long, reducedMotion: Boolean): Float {
        if (reducedMotion) return 0f
        val phase = (elapsedMs.coerceAtLeast(0L) % CLOUD_PERIOD_MS).toDouble() /
            CLOUD_PERIOD_MS.toDouble()
        return (sin(2.0 * PI * phase) * 4.5).toFloat()
    }

    /** Glow is bounded and stable when the user disables system animations. */
    fun fireflyAlpha(elapsedMs: Long, index: Int, reducedMotion: Boolean): Int {
        require(index in 0..2) { "Unknown firefly" }
        if (reducedMotion) return 140
        val phase = (elapsedMs.coerceAtLeast(0L) % FIREFLY_PERIOD_MS).toDouble() /
            FIREFLY_PERIOD_MS.toDouble() + index / 3.0
        return (135.0 + 70.0 * sin(2.0 * PI * phase)).toInt().coerceIn(65, 205)
    }

    /** Floating lights never shift more than 3 pixels from their static anchors. */
    fun fireflyOffset(elapsedMs: Long, index: Int, reducedMotion: Boolean): Float {
        require(index in 0..2) { "Unknown firefly" }
        if (reducedMotion) return 0f
        val phase = (elapsedMs.coerceAtLeast(0L) % FIREFLY_PERIOD_MS).toDouble() /
            FIREFLY_PERIOD_MS.toDouble() + index / 3.0
        return (3.0 * sin(2.0 * PI * phase)).toFloat()
    }
}
