package com.chibialarm.app

import kotlin.math.abs

/**
 * Distinguishes an intentional tap from a scroll, swipe or cancelled gesture.
 * Pure Kotlin so accidental RPG interactions can be regression-tested without
 * requiring an Android device. No reward state is stored here.
 */
internal class ChibiTapGesture(private val touchSlopPx: Float) {
    init { require(touchSlopPx.isFinite() && touchSlopPx >= 0f) }

    private var startX = 0f
    private var startY = 0f
    private var active = false

    fun begin(x: Float, y: Float): Boolean {
        cancel()
        if (!x.isFinite() || !y.isFinite()) return false
        startX = x
        startY = y
        active = true
        return true
    }

    fun move(x: Float, y: Float): Boolean {
        if (!active) return false
        if (!withinSlop(x, y)) {
            cancel()
            return false
        }
        return true
    }

    /** Returns true exactly once, only after a valid down within the tap slop. */
    fun end(x: Float, y: Float): Boolean {
        val valid = active && withinSlop(x, y)
        cancel()
        return valid
    }

    fun cancel() {
        active = false
    }

    private fun withinSlop(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() &&
            abs(x - startX) <= touchSlopPx &&
            abs(y - startY) <= touchSlopPx
}
