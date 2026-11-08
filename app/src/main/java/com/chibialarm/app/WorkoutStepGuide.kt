package com.chibialarm.app

/** Split catalog instructions into short, independently replayable spoken steps. */
object WorkoutStepGuide {
    fun split(instructions: String): List<String> {
        val normalized = instructions.trim()
        if (normalized.isEmpty()) return listOf("Đọc hướng dẫn trên màn hình và tập theo sức mình.")
        // Catalog entries can be newline-separated or sentence-separated.
        val lines = normalized.split(Regex("[\\r\\n]+"))
            .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.trim().trimStart('•', '-', ' ', '\t') }
            .filter { it.isNotBlank() }
        return lines.ifEmpty { listOf(normalized) }
    }
}
