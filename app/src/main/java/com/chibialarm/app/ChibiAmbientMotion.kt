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
    private const val BUTTERFLY_PERIOD_MS = 6_400L

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

    /**
     * Two original hand-drawn butterflies in the daylight garden. All values are
     * deterministic and bounded, so drawing never touches account or reward state.
     * A disabled system animation leaves the butterflies stationary.
     */
    private fun butterflyPhase(elapsedMs: Long, index: Int): Double {
        require(index in 0..1) { "Unknown butterfly" }
        return (elapsedMs.coerceAtLeast(0L) % BUTTERFLY_PERIOD_MS).toDouble() /
            BUTTERFLY_PERIOD_MS.toDouble() + index * 0.5
    }

    fun butterflyXOffset(elapsedMs: Long, index: Int, reducedMotion: Boolean): Float {
        val phase = butterflyPhase(elapsedMs, index)
        return if (reducedMotion) 0f else (11.0 * sin(2.0 * PI * phase)).toFloat()
    }

    fun butterflyYOffset(elapsedMs: Long, index: Int, reducedMotion: Boolean): Float {
        val phase = butterflyPhase(elapsedMs, index)
        return if (reducedMotion) 0f else (4.0 * sin(4.0 * PI * phase)).toFloat()
    }

    fun butterflyWingScale(elapsedMs: Long, index: Int, reducedMotion: Boolean): Float {
        require(index in 0..1) { "Unknown butterfly" }
        if (reducedMotion) return 0.7f
        val phase = (elapsedMs.coerceAtLeast(0L) % 420L).toDouble() / 420.0
        return (0.65 + 0.35 * kotlin.math.abs(sin(2.0 * PI * phase + index))).toFloat()
    }
    /** Three tiny watering droplets per planter, using an 850ms bounded timeline. */
    fun wateringDropY(remainingMs: Long, index: Int, reducedMotion: Boolean): Float {
        require(index in 0..2) { "Unknown watering droplet" }
        val start = 96f + index * 8f
        if (reducedMotion) return start
        val elapsed = (850L - remainingMs.coerceIn(0L, 850L)).toFloat() / 850f
        return start + 55f * elapsed
    }

    fun wateringDropAlpha(remainingMs: Long, reducedMotion: Boolean): Int {
        if (remainingMs <= 0L) return 0
        if (reducedMotion) return 180
        return (210f * (remainingMs.coerceAtMost(850L).toFloat() / 850f))
            .toInt().coerceIn(0, 210)
    }

}
