package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small, offline command interpreter. Unrecognized speech never controls a workout. */
object WorkoutVoiceCommands {
    enum class Command { PAIN, PAUSE, RESUME, NEXT, PREVIOUS, REPEAT, EASIER, SHORTER, SHORTER_REMAINING, REST, UNKNOWN }

    /** Human-readable action shown before applying fallible speech recognition. */
    fun describe(command: Command): String = when (command) {
        Command.PAIN -> "Dừng tập vì cảnh báo an toàn"
        Command.PAUSE -> "Tạm dừng buổi tập"
        Command.RESUME -> "Tiếp tục buổi tập"
        Command.NEXT -> "Chuyển sang động tác tiếp theo"
        Command.PREVIOUS -> "Quay lại động tác trước"
        Command.REPEAT -> "Đọc lại hướng dẫn"
        Command.EASIER -> "Hiển thị cách tập dễ hơn"
        Command.SHORTER -> "Rút ngắn thời gian động tác hiện tại"
        Command.SHORTER_REMAINING -> "Rút ngắn động tác hiện tại và tất cả động tác còn lại"
        Command.REST -> "Thêm 30 giây nghỉ"
        Command.UNKNOWN -> "Chưa hiểu yêu cầu; không tự thay đổi buổi tập"
    }

    fun parse(utterance: String): Command {
        val normalized = Normalizer.normalize(utterance.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty() || normalized.length > 160) return Command.UNKNOWN
        // 'bắt đầu' contains the unaccented token 'dau'; it is not a pain report.
        // Remove only this phrase for safety-token detection, preserving any actual 'đau' later.
        val safetyWords = normalized.replace(Regex("\\bbat dau\\b"), " ").split(' ').toSet()
        val words = normalized.split(' ').toSet()
        return when {
            normalized.contains("bi dau") || normalized.contains("dang dau") ||
                normalized.contains("chan thuong") || normalized.contains("kho tho") ||
                normalized.contains("chong mat") || normalized.contains("choang vang") ||
                normalized.contains("dau nguc") || normalized.contains("dau dau") ||
                normalized.contains("dau o ") || normalized.contains("dau qua") ||
                normalized.contains("dau goi") || normalized.contains("dau lung") ||
                normalized.contains("buon non") || normalized.contains("muon ngat") ||
                normalized.contains("te tay") || normalized.contains("te chan") ||
                ("dau" in safetyWords && !normalized.contains("o dau")) || "ngat" in words -> Command.PAIN
            normalized.contains("tam dung") || normalized.contains("dung lai") ||
                normalized.contains("dung tap") || normalized.contains("khong tap noi") ||
                normalized.contains("het suc") || "pause" in words -> Command.PAUSE
            normalized.contains("nghi them") || normalized.contains("nghi mot chut") || normalized.contains("met roi") ||
                normalized.contains("hoi met") || normalized.contains("met qua") ||
                normalized.contains("met lam") || normalized.matches(Regex(".*\\\\bnghi\\\\s+[0-9]{1,3}\\\\s*(giay|gi|s|phut)\\\\b.*")) ||
                normalized.contains("nghi mot phut") || normalized.contains("nghi hai phut") ||
                normalized.contains("nghi chin muoi giay") || normalized.contains("nghi sau muoi giay") ||
                normalized.contains("nghi ba muoi giay") || normalized.contains("can nghi") ||
                "rest" in words -> Command.REST
            (normalized.contains("rut ngan") || normalized.contains("ngan hon") || normalized.contains("giam thoi gian")) &&
                listOf("con lai", "tat ca", "ca buoi", "toan bo", "phan sau").any { normalized.contains(it) } -> Command.SHORTER_REMAINING
            normalized.contains("rut ngan") || normalized.contains("ngan hon") ||
                normalized.contains("giam thoi gian") -> Command.SHORTER
            normalized.contains("de hon") || normalized.contains("giam do kho") ||
                normalized.contains("nhe hon") -> Command.EASIER
            normalized.contains("lap lai") || normalized.contains("nhac lai") || normalized.contains("noi lai") ||
                "repeat" in words -> Command.REPEAT
            normalized.contains("bai truoc") || normalized.contains("quay lai") ||
                "previous" in words -> Command.PREVIOUS
            normalized.contains("bai tiep") || normalized.contains("tiep theo") ||
                normalized.contains("bo qua bai") || normalized.contains("chuyen bai") ||
                "next" in words -> Command.NEXT
            normalized.contains("tiep tuc") || normalized.contains("bat dau") ||
                "resume" in words -> Command.RESUME
            else -> Command.UNKNOWN
        }
    }
}
