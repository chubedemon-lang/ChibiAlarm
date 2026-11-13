package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small, offline command interpreter. Unrecognized speech never controls a workout. */
object WorkoutVoiceCommands {
    enum class Command {
        PAIN, PAUSE, STATUS, STEP_GUIDE, RESUME, NEXT, PREVIOUS, REPEAT, EASIER, SWAP_EASIER,
        EASIER_AND_REST, SHORTER, SHORTER_REMAINING, REST, COMPLETE_SET, NO_EQUIPMENT, UNKNOWN
    }

    /** Human-readable action shown before applying fallible speech recognition. */
    fun describe(command: Command, utterance: String? = null): String {
        val restSeconds = utterance?.let { WorkoutRestRequest.seconds(it) } ?: 30
        return when (command) {
            Command.PAIN -> "Dừng tập vì cảnh báo an toàn"
            Command.PAUSE -> "Tạm dừng buổi tập"
            Command.STATUS -> "Đọc tiến độ, số hiệp và thời gian còn lại"
            Command.STEP_GUIDE -> "Mở hướng dẫn từng bước cho động tác hiện tại"
            Command.COMPLETE_SET -> "Xác nhận hoàn thành hiệp hiện tại"
            Command.RESUME -> "Tiếp tục buổi tập"
            Command.NEXT -> "Chuyển sang động tác tiếp theo"
            Command.PREVIOUS -> "Quay lại động tác trước"
            Command.REPEAT -> "Đọc lại hướng dẫn"
            Command.EASIER -> "Hiển thị cách tập dễ hơn"
            Command.SWAP_EASIER -> "Thay động tác hiện tại bằng phương án nhẹ hơn"
            Command.EASIER_AND_REST -> "Hiển thị cách tập dễ hơn và thêm $restSeconds giây nghỉ"
            Command.SHORTER -> "Rút ngắn thời gian động tác hiện tại"
            Command.SHORTER_REMAINING -> "Rút ngắn động tác hiện tại và tất cả động tác còn lại"
            Command.REST -> "Bắt đầu nghỉ riêng $restSeconds giây"
            Command.NO_EQUIPMENT -> "Đề xuất thay động tác cần tạ/dây ở phần chưa tập bằng biến thể không cần tải ngoài"
            Command.UNKNOWN -> "Chưa hiểu yêu cầu; không tự thay đổi buổi tập"
        }
    }

    /**
     * A repetition target may be applied only when the utterance has no other
     * recognized action. Mixed commands remain single-action until an explicit
     * multi-action confirmation is supported.
     */
    fun mayEditReps(utterance: String): Boolean = parse(utterance) == Command.UNKNOWN

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
            listOf(
                "khong co dung cu", "khong dung cu", "khong can dung cu", "tay khong",
                "khong dung ta", "khong co ta", "khong co ta don", "bo ta",
                "khong mang ta", "de ta o nha", "khong co do tap", "khong co thiet bi",
                "khong mang dung cu", "khong dung day khang luc", "khong co day khang luc",
                "bo day khang luc"
            ).any(normalized::contains) -> Command.NO_EQUIPMENT
            normalized.contains("tien do") || normalized.contains("con bao nhieu bai") ||
                normalized.contains("con may bai") || normalized.contains("bao nhieu hiep") ||
                normalized.contains("con may hiep") || normalized.contains("may hiep nua") ||
                normalized.contains("hiep con lai") || normalized.contains("so hiep con lai") ||
                normalized.contains("con bao lau") || normalized.contains("bao lau nua") ||
                normalized.contains("may phut nua") || normalized.contains("con bao nhieu phut") ||
                normalized.contains("con bao nhieu giay") || normalized.contains("thoi gian con lai") ||
                normalized.contains("uoc tinh thoi gian") || normalized.contains("khi nao xong") ||
                "progress" in words || "status" in words -> Command.STATUS
            normalized.contains("huong dan tung buoc") || normalized.contains("doc tung buoc") ||
                normalized.contains("xem tung buoc") || normalized.contains("mo huong dan") -> Command.STEP_GUIDE
            normalized.contains("chua xong hiep") || normalized.contains("chua hoan thanh hiep") ||
                normalized.contains("khong xong hiep") || normalized.contains("khong hoan thanh hiep") ||
                normalized.contains("chua xac nhan hiep") -> Command.UNKNOWN
            normalized.contains("xong hiep") || normalized.contains("hoan thanh hiep") ||
                normalized.contains("xac nhan hiep") -> Command.COMPLETE_SET
            (normalized.contains("de hon") || normalized.contains("giam do kho") || normalized.contains("nhe hon")) &&
                (normalized.contains("nghi") || normalized.contains("can nghi")) -> Command.EASIER_AND_REST
            normalized.contains("nghi them") || normalized.contains("nghi mot chut") || normalized.contains("met roi") ||
                normalized.contains("hoi met") || normalized.contains("met qua") ||
                normalized.contains("met lam") || Regex("""\bnghi(?: them)?\s+[0-9]{1,3}\s*(?:giay|gi|s|phut)\b""").containsMatchIn(normalized) ||
                normalized.contains("nghi mot phut") || normalized.contains("nghi hai phut") ||
                normalized.contains("nghi chin muoi giay") || normalized.contains("nghi sau muoi giay") ||
                normalized.contains("nghi ba muoi giay") || normalized.contains("can nghi") ||
                "rest" in words -> Command.REST
            (normalized.contains("rut ngan") || normalized.contains("ngan hon") || normalized.contains("giam thoi gian")) &&
                listOf("con lai", "tat ca", "ca buoi", "toan bo", "phan sau").any { normalized.contains(it) } -> Command.SHORTER_REMAINING
            normalized.contains("rut ngan") || normalized.contains("ngan hon") ||
                normalized.contains("giam thoi gian") -> Command.SHORTER
            normalized.contains("doi dong tac") || normalized.contains("thay dong tac") ||
                normalized.contains("doi bai") || normalized.contains("doi sang bai") ||
                normalized.contains("thay bai") || normalized.contains("thay bang bai") ||
                normalized.contains("bai khac") -> Command.SWAP_EASIER
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
