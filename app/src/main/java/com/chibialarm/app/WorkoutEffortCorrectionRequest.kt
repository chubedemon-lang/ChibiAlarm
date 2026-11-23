package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/** Recognizes an explicit request to correct the latest self-reported set rating. */
internal object WorkoutEffortCorrectionRequest {
    private const val MAX_INPUT_CHARS = 160
    private val correctionCues = listOf(
        "sua cam nhan",
        "sua muc cam nhan",
        "doi cam nhan",
        "doi muc cam nhan",
        "cap nhat cam nhan",
        "chon nham cam nhan",
        "chon nham muc cam nhan",
        "sua danh gia",
        "doi danh gia",
        "cap nhat danh gia",
        "danh gia nham",
        "sua muc gang suc",
        "doi muc gang suc"
    )
    private val setCues = listOf("hiep vua roi", "hiep gan nhat", "hiep cuoi", "hiep")

    fun isRequest(input: String): Boolean {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return false
        val query = normalize(input)
        return correctionCues.any(query::contains) && setCues.any(query::contains)
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
