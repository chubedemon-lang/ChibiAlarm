package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small, offline command interpreter. Unrecognized speech never controls a workout. */
object WorkoutVoiceCommands {
    enum class Command { PAUSE, RESUME, NEXT, PREVIOUS, REPEAT, EASIER, REST, UNKNOWN }

    fun parse(utterance: String): Command {
        val normalized = Normalizer.normalize(utterance.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty() || normalized.length > 160) return Command.UNKNOWN
        val words = normalized.split(' ').toSet()
        return when {
            normalized.contains("bi dau") || normalized.contains("dang dau") ||
                normalized.contains("chan thuong") -> Command.PAUSE
            normalized.contains("tam dung") || normalized.contains("dung lai") ||
                normalized.contains("dung tap") || "pause" in words -> Command.PAUSE
            normalized.contains("nghi them") || normalized.contains("nghi mot chut") ||
                "rest" in words -> Command.REST
            normalized.contains("de hon") || normalized.contains("giam do kho") ||
                normalized.contains("nhe hon") -> Command.EASIER
            normalized.contains("lap lai") || normalized.contains("noi lai") ||
                "repeat" in words -> Command.REPEAT
            normalized.contains("bai truoc") || normalized.contains("quay lai") ||
                "previous" in words -> Command.PREVIOUS
            normalized.contains("bai tiep") || normalized.contains("tiep theo") ||
                "next" in words -> Command.NEXT
            normalized.contains("tiep tuc") || normalized.contains("bat dau") ||
                "resume" in words -> Command.RESUME
            else -> Command.UNKNOWN
        }
    }
}
