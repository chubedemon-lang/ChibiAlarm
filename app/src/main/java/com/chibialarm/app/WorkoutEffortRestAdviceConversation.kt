package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Explains the optional rest suggestion using only confirmed, structured effort check-ins
 * for the active exercise. It is read-only: it never starts a timer or changes the plan.
 */
internal object WorkoutEffortRestAdviceConversation {
    private const val MAX_INPUT_CHARS = 300
    private const val MAX_SETS = 20

    private val adviceCues = listOf(
        "nghi them bao lau",
        "nghi them may giay",
        "nghi them bao nhieu giay",
        "nen nghi them",
        "nen nghi bao lau theo cam nhan",
        "theo cam nhan nen nghi",
        "chibi de xuat nghi",
        "de xuat nghi bao lau",
        "vi sao phai nghi them",
        "tai sao phai nghi them",
        "nghi theo muc do gang suc"
    )

    fun answer(
        input: String,
        prescribedRestSeconds: Int?,
        completedSets: Int?,
        effortLevels: List<WorkoutEffortFeedback.Level?>?,
        effortScores: List<Int?>? = null
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)
        if (adviceCues.none(query::contains)) return null

        val prescribed = prescribedRestSeconds?.takeIf { it in 15..180 } ?: return null
        val confirmedCount = completedSets?.takeIf { it in 1..MAX_SETS } ?: return null
        val reports = effortLevels ?: emptyList()
        if (reports.size > MAX_SETS) return null
        val scores = effortScores ?: emptyList()
        if (scores.size > MAX_SETS || scores.any { it != null && it !in 1..10 }) return null

        val confirmedReports = reports.take(confirmedCount)
        val latest = confirmedReports.getOrNull(confirmedCount - 1)
        val latestScore = scores.getOrNull(confirmedCount - 1)
        if (latestScore != null) {
            val scoreLevel = when (latestScore) {
                in 1..3 -> WorkoutEffortFeedback.Level.EASY
                in 4..6 -> WorkoutEffortFeedback.Level.MODERATE
                in 7..8 -> WorkoutEffortFeedback.Level.HARD
                in 9..10 -> WorkoutEffortFeedback.Level.VERY_HARD
                else -> return null
            }
            if (latest != scoreLevel) return null
        }
        if (latest == null) {
            return "Hiệp $confirmedCount chưa có check-in cảm nhận hợp lệ. " +
                "Theo giáo án, thời gian nghỉ là $prescribed giây; nếu vẫn chưa sẵn sàng, " +
                "bạn có thể nghỉ thêm theo cảm nhận. Chibi không tự quyết định thay bạn."
        }

        if (latest == WorkoutEffortFeedback.Level.VERY_HARD) {
            return "Bạn tự đánh giá hiệp $confirmedCount là “${latest.label}”. " +
                "Hãy giữ buổi tập tạm dừng trước bước tiếp theo; thời gian nghỉ không tự động " +
                "cho phép tiếp tục. Nếu có đau, chóng mặt, đau ngực hoặc khó thở bất thường, " +
                "hãy kết thúc buổi tập và tìm hỗ trợ phù hợp."
        }

        val recommendation = runCatching {
            WorkoutEffortFeedback.recommend(
                level = latest,
                prescribedRestSeconds = prescribed,
                previousLevelsForSameMove = confirmedReports.dropLast(1),
                score = latestScore
            )
        }.getOrNull() ?: return null

        val scoreLabel = latestScore?.let { " (tự đánh giá $it/10)" }.orEmpty()
        return if (latest == WorkoutEffortFeedback.Level.HARD) {
            "Theo check-in hiệp $confirmedCount: ${latest.label}$scoreLabel. " +
                "Chibi gợi ý nghỉ ${recommendation.restSeconds} giây " +
                "(giáo án: $prescribed giây). Đây chỉ là gợi ý từ cảm nhận bạn tự khai báo; " +
                "đồng hồ không tự chạy và Chibi không tăng số lần, số hiệp hay tải tập."
        } else {
            "Theo check-in hiệp $confirmedCount: ${latest.label}$scoreLabel. " +
                "Chibi chưa thấy lý do từ check-in để tăng thời gian nghỉ: " +
                "giữ mức $prescribed giây theo giáo án. Nếu bạn vẫn chưa sẵn sàng, " +
                "có thể nghỉ lâu hơn; không cần cố hoàn thành ngay."
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
