package com.chibialarm.app

/** Ordered TTS queue plan shared by the Fitness Coach and guided workout. */
internal data class WorkoutSpeechUtterance(
    val text: String,
    /** The first chunk replaces old speech; later chunks append without interrupting it. */
    val flushQueue: Boolean
)

internal object WorkoutSpeechSequence {
    /**
     * Build a complete, bounded speech queue. When appending to an existing narration,
     * callers can keep every chunk in QUEUE_ADD mode instead of flushing earlier speech.
     */
    fun plan(text: String, flushFirst: Boolean = true): List<WorkoutSpeechUtterance> =
        WorkoutSpeechText.chunks(text).mapIndexed { index, chunk ->
            WorkoutSpeechUtterance(text = chunk, flushQueue = flushFirst && index == 0)
        }
}
