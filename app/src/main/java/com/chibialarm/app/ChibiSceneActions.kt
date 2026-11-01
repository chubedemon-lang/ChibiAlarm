package com.chibialarm.app

/** Pure scene hit testing, independent of Android rendering and screen density. */
object ChibiSceneActions {
    enum class Target { BED, DESK, TRAINING, HERO, NONE }

    fun hit(x: Float, y: Float): Target = when {
        !x.isFinite() || !y.isFinite() -> Target.NONE
        x in 0f..83f && y in 95f..205f -> Target.BED
        x in 238f..320f && y in 85f..205f -> Target.DESK
        x in 92f..228f && y in 15f..218f -> Target.HERO
        x in 0f..320f && y in 205f..240f -> Target.TRAINING
        else -> Target.NONE
    }
}
