package com.chibialarm.app

/** Ordered TTS queue plan shared by the Fitness Coach and guided workout. */
internal data class WorkoutSpeechUtterance(
    val text: String,
    /** The first chunk replaces old speech; later chunks append without interrupting it. */
    val flushQueue: Boolean
)

internal object WorkoutSpeechSequence {
    fun plan(text: String): List<WorkoutSpeechUtterance> =
        WorkoutSpeechText.chunks(text).mapIndexed { index, chunk ->
            WorkoutSpeechUtterance(text = chunk, flushQueue = index == 0)
        }
}
