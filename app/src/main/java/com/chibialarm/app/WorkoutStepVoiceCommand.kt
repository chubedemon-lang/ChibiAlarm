package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small offline command grammar for the step-by-step guide; no transcript is retained. */
object WorkoutStepVoiceCommand {
    enum class Command { FIRST, NEXT, PREVIOUS, PAUSE, RESUME, AUTO_SPEAK_ON, AUTO_SPEAK_OFF, REPEAT, REPEAT_ANSWER, SUMMARY, STATUS, EASIER, OPEN_EASIER_ALTERNATIVES, SAFETY_NOTE, SPEECH_SLOWER, SPEECH_FASTER, SPEECH_NORMAL, REST, ASK_EXERCISE, CLOSE, SAFETY, UNKNOWN }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(phrase) + "(\\s|$)").containsMatchIn(text)

    fun parse(utterance: String?): Command {
        if (utterance.isNullOrBlank()) return Command.UNKNOWN
        val raw = utterance.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        // Check the accented and plain-ASCII forms before removing diacritics so
        // "đầu gối" is not mistaken for "đau" (pain). A small exact allowlist keeps
        // the unaccented navigation phrase "buoc dau tien" from looking like pain;
        // longer utterances that also mention pain still fail closed.
        val unaccentedFirstStepOnly = raw in setOf(
            "buoc dau", "buoc dau tien", "buoc dau la gi", "doc buoc dau",
            "giai thich buoc dau", "ve buoc dau", "quay ve buoc dau",
            "tro ve buoc dau", "bat dau lai huong dan",
            "khong ve buoc dau", "dung ve buoc dau",
            "khong quay ve buoc dau", "dung quay ve buoc dau"
        )
        if (containsPhrase(raw, "đau") ||
            (containsPhrase(raw, "dau") && !unaccentedFirstStepOnly)) return Command.SAFETY

        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (listOf("kho tho", "choang", "chong mat", "tuc nguc", "kho chiu")
                .any { containsPhrase(normalized, it) }) return Command.SAFETY

        if (listOf(
                "khong doc muc tieu", "dung doc muc tieu", "khong doc so hiep",
                "khong doc tien do", "dung doc tien do", "khong can doc tien do",
                "khong doc thoi gian nghi", "dung doc thoi gian nghi",
                "khong hoi thoi gian nghi", "dung hoi thoi gian nghi",
                "khong doc cach tap de hon", "dung doc cach tap de hon",
                "khong doi bai de hon", "dung doi bai de hon",
                "khong muon doi bai", "khong muon thay dong tac",
                "khong doi sang bai nhe hon", "dung doi sang bai nhe hon",
                "khong thay dong tac", "dung thay dong tac",
                "khong xem bai thay the", "dung xem bai thay the",
                "khong mo phuong an thay the", "dung mo phuong an thay the",
                "khong doc luu y", "dung doc luu y", "khong nhac luu y",
                "khong doc buoc", "dung doc buoc", "khong noi buoc", "dung noi buoc",
                "khong giai thich buoc", "dung giai thich buoc",
                "khong huong dan buoc", "dung huong dan buoc",
                "khong doc cham hon", "dung doc cham hon",
                "khong noi cham lai", "dung noi cham lai",
                "khong doc nhanh hon", "dung doc nhanh hon",
                "khong noi nhanh hon", "dung noi nhanh hon",
                "khong tang toc do", "dung tang toc do",
                "khong can doc nhanh hon", "giu toc do hien tai",
                "khong ve buoc dau", "dung ve buoc dau",
                "khong quay ve buoc dau", "dung quay ve buoc dau",
                "do not restart guide", "don t restart guide",
                "khong tam dung buoi tap", "dung tam dung buoi tap",
                "khong tiep tuc buoi tap", "dung tiep tuc buoi tap",
                "khong pause workout", "do not pause workout", "don t pause workout",
                "khong resume workout", "do not resume workout", "don t resume workout",
                "khong tu doc", "dung tu doc", "khong doc tu dong", "dung doc tu dong",
                "khong bat tu doc", "dung bat tu doc", "khong tat tu doc", "dung tat tu doc",
                "do not turn on auto speak", "don t turn on auto speak",
                "do not turn off auto speak", "don t turn off auto speak"
            ).any { normalized.contains(it) }) return Command.UNKNOWN
        if (WorkoutExerciseStepQuestion.isQuestion(utterance)) return Command.ASK_EXERCISE
        if (listOf(
                "doi bai de hon", "doi sang bai nhe hon", "thay dong tac de hon",
                "thay dong tac nay bang bai nhe hon", "xem bai thay the",
                "mo phuong an thay the", "xem phuong an thay the",
                "bai thay the nhe hon", "co bai nao thay the"
            ).any { normalized.contains(it) }) return Command.OPEN_EASIER_ALTERNATIVES
        if (listOf("tam dung buoi tap", "pause workout", "pause the workout")
                .any { normalized.contains(it) }) return Command.PAUSE
        if (listOf("tiep tuc buoi tap", "resume workout", "resume the workout")
                .any { normalized.contains(it) }) return Command.RESUME
        if (listOf(
                "tat tu doc", "tat doc tu dong", "ngung tu doc",
                "auto speak off", "turn off auto speak", "disable auto narration",
                "stop automatic narration"
            ).any { normalized.contains(it) }) return Command.AUTO_SPEAK_OFF
        if (listOf(
                "bat tu doc", "bat doc tu dong", "tu doc buoc", "doc buoc tu dong",
                "doc tu dong", "auto speak on", "turn on auto speak", "enable auto narration"
            ).any { normalized.contains(it) }) return Command.AUTO_SPEAK_ON
        if (listOf(
                "noi cham lai", "doc cham hon", "noi cham hon", "cham hon nua",
                "slow down", "speak slower"
            ).any { normalized.contains(it) }) return Command.SPEECH_SLOWER
        if (listOf(
                "noi nhanh hon", "doc nhanh hon", "tang toc do doc",
                "tang toc do", "nhanh hon nua", "speak faster", "speed up"
            ).any { normalized.contains(it) }) return Command.SPEECH_FASTER
        if (listOf(
                "toc do binh thuong", "doc binh thuong", "noi nhu cu",
                "toc do mac dinh", "reset speech speed"
            ).any { normalized.contains(it) }) return Command.SPEECH_NORMAL
        if (listOf(
                "doc lai cau tra loi", "nhac lai cau tra loi", "nghe lai cau tra loi",
                "lap lai cau tra loi", "doc lai phan tra loi"
            ).any { normalized.contains(it) }) return Command.REPEAT_ANSWER
        if (listOf(
                "doc tien do", "tien do buoi tap", "xem tien do", "tien do hien tai",
                "con bao nhieu bai", "con may bai", "con may hiep", "hiep con lai",
                "thoi gian con lai", "bao lau nua xong", "may phut nua", "khi nao xong",
                // These ask for the live rest countdown, not the exercise's prescribed rest.
                "con bao nhieu giay nghi", "con may giay nghi", "nghi con bao lau",
                "nghi con may giay", "con bao lau nua nghi", "thoi gian nghi con lai",
                "con bao nhieu phut nghi", "con bao nhieu giay nua het nghi",
                "con may giay nua het nghi",
                "status", "progress"
            ).any { normalized.contains(it) }) return Command.STATUS
        if (listOf(
                "doc muc tieu", "doc lai muc tieu", "doc so hiep", "doc so lan",
                "muc tieu bai tap", "read workout target", "read workout summary"
            ).any { normalized.contains(it) }) return Command.SUMMARY
        if (listOf(
                "doc cach tap de hon", "doc cach de hon", "doc phuong an nhe hon",
                "read easier option", "cach tap de hon", "tap de hon"
            ).any { normalized.contains(it) }) return Command.EASIER
        if (listOf(
                "doc luu y an toan", "doc luu y", "nhac luu y an toan",
                "nhac lai luu y", "doc canh bao an toan", "doc phan luu y",
                "safety note"
            ).any { normalized.contains(it) }) return Command.SAFETY_NOTE
        if (listOf("khong nghi them", "dung nghi them", "khong them thoi gian nghi")
                .any { normalized.contains(it) }) return Command.UNKNOWN
        if (listOf("nghi them", "them thoi gian nghi", "keo dai thoi gian nghi", "rest more", "extend rest")
                .any { normalized.contains(it) }) return Command.REST
        if (listOf(
                "cach tap", "huong dan bai nay", "lam sao tap", "lam sao de tap",
                "tap nhu the nao", "thuc hien nhu the nao", "ky thuat",
                "luu y khi tap", "luu y khi thuc hien", "nghi bao lau",
                "bao nhieu hiep", "bao nhieu lan", "bao nhieu giay",
                "muc tieu bai nay", "giai thich bai nay", "bai nay tap sao",
                "dong tac nay tap sao", "nhom co nao", "nhom co chinh", "co nao",
                "tap vao dau", "tac dung", "bai nay tac dong vao dau", "can dung cu gi", "bai nay can gi",
                "dung cu nao", "can ta hay day", "co can ta khong", "co can day khang luc khong",
                "co can tham khong", "how do i do this", "how many reps",
                "how many sets", "how long rest"
            ).any { normalized.contains(it) }) return Command.ASK_EXERCISE
        if (listOf(
                "ve buoc dau", "quay ve buoc dau", "tro ve buoc dau",
                "buoc dau tien", "bat dau lai huong dan",
                "restart guide", "start guide over", "return to first step"
            ).any { normalized.contains(it) }) return Command.FIRST
        if (listOf("buoc tiep", "tiep theo", "buoc sau", "sang buoc", "next step")
                .any { normalized.contains(it) }) return Command.NEXT
        if (listOf("buoc truoc", "quay lai", "lui lai", "previous step")
                .any { normalized.contains(it) }) return Command.PREVIOUS
        if (listOf("doc lai", "nghe lai", "lap lai", "nhac lai", "repeat step")
                .any { normalized.contains(it) }) return Command.REPEAT
        if (listOf("dong huong dan", "thoat huong dan", "dung huong dan", "close guide")
                .any { normalized.contains(it) }) return Command.CLOSE
        return Command.UNKNOWN
    }

    /**
     * Returns a one-based destination only for an explicit navigation phrase.
     * Questions such as "bước 2 là gì?" remain coach questions, not navigation.
     * Safety language always takes priority, and out-of-range steps are rejected.
     */
    fun parseNavigationTarget(utterance: String?, totalSteps: Int): Int? {
        if (utterance.isNullOrBlank() || utterance.length > 120 || totalSteps !in 1..100) return null
        if (parse(utterance) == Command.SAFETY) return null

        val raw = utterance.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        val match = Regex(
            """^(?:(?:chuyen|nhay|di|toi|den|mo|quay lai|quay ve|tro ve)\s+(?:(?:toi|den|sang)\s+)?|(?:dua toi den|dua tao den)\s+)buoc(?:\s+so)?\s+(?:thu\s+)?([0-9]{1,2}|nhat|nhi|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)$"""
        ).matchEntire(normalized) ?: return null
        val token = match.groupValues[1]
        val position = token.toIntOrNull() ?: when (token) {
            "nhat", "mot" -> 1
            "nhi", "hai" -> 2
            "ba" -> 3
            "bon", "tu" -> 4
            "nam" -> 5
            "sau" -> 6
            "bay" -> 7
            "tam" -> 8
            "chin" -> 9
            "muoi" -> 10
            else -> null
        } ?: return null
        return position.takeIf { it in 1..totalSteps }
    }

}
