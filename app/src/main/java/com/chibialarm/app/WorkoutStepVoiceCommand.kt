package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Small offline command grammar for the step-by-step guide; no transcript is retained. */
object WorkoutStepVoiceCommand {
    enum class Command { NEXT, PREVIOUS, REPEAT, REPEAT_ANSWER, SUMMARY, EASIER, SAFETY_NOTE, REST, ASK_EXERCISE, CLOSE, SAFETY, UNKNOWN }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(phrase) + "(\\s|$)").containsMatchIn(text)

    fun parse(utterance: String?): Command {
        if (utterance.isNullOrBlank()) return Command.UNKNOWN
        val raw = utterance.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        // Check the accented and plain-ASCII forms before removing diacritics so
        // "đầu gối" is not mistaken for "đau" (pain).
        if (containsPhrase(raw, "đau") || containsPhrase(raw, "dau")) return Command.SAFETY

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
                "khong doc cach tap de hon", "dung doc cach tap de hon",
                "khong doc luu y", "dung doc luu y", "khong nhac luu y"
            ).any { normalized.contains(it) }) return Command.UNKNOWN
        if (listOf(
                "doc lai cau tra loi", "nhac lai cau tra loi", "nghe lai cau tra loi",
                "lap lai cau tra loi", "doc lai phan tra loi"
            ).any { normalized.contains(it) }) return Command.REPEAT_ANSWER
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
                "tap vao dau", "tac dung", "bai nay tac dong vao dau", "how do i do this", "how many reps",
                "how many sets", "how long rest"
            ).any { normalized.contains(it) }) return Command.ASK_EXERCISE
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
}
