package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Answers exercise-technique questions only from the curated offline catalog.
 * It never reads workout notes, infers anatomy, or evaluates a person's live form.
 */
object WorkoutExerciseConversation {
    private const val MAX_INPUT_CHARS = 300
    private const val MAX_MOVES = 500
    private const val MAX_DISPLAY_STEPS = 6
    private const val MAX_STEP_CHARS = 180

    private val questionPhrases = listOf(
        "cach tap", "huong dan", "lam sao tap", "lam sao de tap",
        "tap nhu the nao", "thuc hien nhu the nao", "ky thuat",
        "bai do tap the nao", "dong tac do tap the nao",
        "bai vua noi tap the nao", "dong tac vua noi tap the nao",
        "luu y khi tap", "luu y khi thuc hien", "nghi bao lau",
        "bao nhieu hiep", "bao nhieu lan", "bao nhieu giay",
        "muc tieu bai nay", "bai nay tap sao", "dong tac nay tap sao",
        "nhom co nao", "nhom co chinh", "co nao", "tap vao dau", "tac dung",
        "bai nay tac dong vao dau", "can dung cu gi", "bai nay can gi", "dung cu nao",
        "can ta hay day", "co can ta khong", "co can day khang luc khong", "co can tham khong",
        "nghi giua hiep bao lau", "how do i do this", "how many reps",
        "how many sets", "how long rest", "giai thich", "cach tho", "tho the nao",
        "nhip tho", "hit vao", "tho ra", "how to breathe", "breathing"
    )

    private val questionWords = setOf(
        "cach", "tap", "huong", "dan", "lam", "sao", "de", "thuc", "hien",
        "nhu", "the", "nao", "ky", "thuat", "luu", "y", "khi", "nghi",
        "bao", "nhieu", "hiep", "lan", "giay", "giai", "thich", "bai",
        "dong", "tac", "nay", "la", "gi", "toi", "tao", "minh", "muon",
        "biet", "cho", "tung", "buoc", "theo", "voi", "nua", "phut", "dung",
        "nhom", "co", "vao", "dau", "tac", "can", "cu", "chinh",
        "giua", "dang", "hien", "tai", "do", "vua", "noi", "nhac", "luc",
        "tho", "hit", "vao", "ra", "nhip"
    )

    private data class Match(
        val selected: TrainingMove? = null,
        val ambiguous: List<String> = emptyList()
    )

    fun answer(
        input: String,
        moves: List<TrainingMove> = TrainingCatalog.allMoves
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)
        if (!isExerciseQuestion(query)) return null

        val catalog = moves.take(MAX_MOVES).distinctBy { normalize(it.name) }
        val match = findMove(query, catalog)
        if (match.ambiguous.isNotEmpty()) {
            return "Chibi tìm thấy vài động tác gần giống: " +
                match.ambiguous.take(5).joinToString(", ") +
                ". Hãy nhắc đầy đủ tên bài để tránh hướng dẫn nhầm."
        }

        val move = match.selected ?: return (
            "Chibi chưa nhận ra tên động tác trong thư viện. " +
                "Mày hãy nêu rõ tên bài theo đúng tên trong thư viện để Chibi tránh hướng dẫn nhầm."
            )
        return format(move, input)
    }

    /**
     * Identifies a question that can safely inherit the currently open exercise context.
     * An explicit exercise name is not treated as a generic follow-up; it must be resolved
     * against the catalog instead of being answered for whichever move happens to be open.
     */
    fun refersToPreviousExercise(input: String): Boolean {
        if (input.isBlank() || input.length > 160) return false
        val query = normalize(input)
        return listOf(
            "bai do", "dong tac do", "bai vua noi", "dong tac vua noi",
            "bai vua nhac", "dong tac vua nhac", "bai luc nay", "dong tac luc nay"
        ).any(query::contains)
    }

    /**
     * Detects a named exercise after a technique-question cue when that name is not
     * present in the curated catalog. This prevents a typo/unknown move from silently
     * inheriting whichever exercise happens to be open.
     */
    fun hasUnknownExplicitExerciseName(input: String): Boolean {
        if (input.isBlank() || input.length > 160) return false
        val query = normalize(input)
        if (!isExerciseQuestion(query)) return false
        if (listOf(
                "bai nay", "dong tac nay", "bai hien tai", "dong tac hien tai",
                "bai dang tap", "dong tac dang tap", "bai do", "dong tac do",
                "bai vua noi", "dong tac vua noi", "bai vua nhac", "dong tac vua nhac"
            ).any(query::contains)
        ) return false

        val contextualWords = questionWords + setOf(
            "lau", "hay", "ta", "day", "how", "long", "many", "sets", "reps", "rest",
            "do", "vua", "noi", "nhac", "luc", "nguoi", "moi", "mot", "tai", "nha",
            "khong", "khang", "tham", "hoac", "ghe", "video", "gym", "nhe", "hon",
            "phuong", "an", "dung", "cu", "theo", "va", "neu", "duoc", "giup",
            "ban", "em", "anh", "co", "can", "thoi", "gian"
        )
        val cues = listOf("cach tap", "huong dan", "lam sao tap", "lam sao de tap", "ky thuat")
        val cue = cues.firstOrNull(query::contains) ?: return false
        val candidate = query.substringAfter(cue).split(' ')
            .firstOrNull { it.length >= 2 && it !in contextualWords }
            ?: return false
        val catalogTokens = TrainingCatalog.allMoves.asSequence()
            .flatMap { normalize(it.name).split(' ').asSequence() }
            .toSet()
        return candidate !in catalogTokens
    }

    fun isContextualQuestion(input: String): Boolean {
        if (input.isBlank() || input.length > 160) return false
        if (hasUnknownExplicitExerciseName(input)) return false
        val query = normalize(input)
        if (!isExerciseQuestion(query)) return false
        val refersToCurrentMove = listOf(
            "bai nay", "dong tac nay", "bai hien tai", "dong tac hien tai",
            "bai dang tap", "dong tac dang tap"
        ).any(query::contains)
        if (refersToCurrentMove || refersToPreviousExercise(input)) return true
        val contextualWords = questionWords + setOf(
            "lau", "hay", "ta", "day", "how", "long", "many", "sets", "reps", "rest",
            "do", "vua", "noi", "nhac", "luc"
        )
        val unresolvedNameTokens = query.split(' ')
            .filter { it.length >= 2 && it !in contextualWords }
        return unresolvedNameTokens.isEmpty()
    }

    /**
     * Answers a short follow-up about the move already open in the guided workout.
     * This contextual path avoids asking the user to repeat the exercise name.
     * All wording comes from the curated catalog or the active, user-confirmed dose.
     */
    fun answerForCurrentMove(
        input: String,
        move: TrainingMove,
        currentDose: String = move.dose
    ): String? {
        if (input.isBlank() || input.length > 160) return null
        val query = normalize(input)
        if (!isExerciseQuestion(query)) return null

        fun asks(vararg phrases: String): Boolean = phrases.any(query::contains)
        fun safe(value: String, limit: Int): String = value
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(limit)

        val name = safe(move.name, 80).ifBlank { "Động tác hiện tại" }
        val dose = safe(currentDose, 120).ifBlank { safe(move.dose, 120) }
        val setPlan = WorkoutSetTracker.parse(move.dose, move.restSeconds)
        WorkoutExerciseStepQuestion.answer(input, name, move.steps)?.let { return it }

        return when {
            isBreathingQuestion(query) -> WorkoutBreathingGuide.cue(name)
            asks("nhom co", "co nao", "tap vao dau", "tac dung", "bai nay tac dong vao dau") -> {
                val focus = WorkoutExerciseFocus.descriptionFor(move.name)
                if (focus == null) {
                    "$name: thư viện hiện chưa có mô tả nhóm cơ hoặc chuyển động chính cho bài này."
                } else {
                    "$name. $focus Đây là mô tả khái quát từ thư viện, không phải đánh giá tư thế hay chẩn đoán."
                }
            }
            asks("bao nhieu hiep", "bao nhieu lan", "bao nhieu giay",
                "muc tieu bai nay", "how many reps", "how many sets") -> {
                if (setPlan == null) {
                    "$name là bài tính theo thời gian hoặc nhịp. Mục tiêu hiện tại: $dose. " +
                        "Thư viện không quy đổi bài này thành số hiệp hoặc số lần."
                } else {
                    "Mục tiêu hiện tại của $name: $dose. Đây là mục tiêu của giáo án đang mở; " +
                        "chỉ xác nhận hiệp sau khi mày thực sự hoàn thành."
                }
            }
            asks("nghi bao lau", "nghi giua hiep bao lau", "how long rest") -> {
                val rest = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
                if (rest == null) {
                    "$name không có khoảng nghỉ giữa hiệp cố định trong thư viện. " +
                        "Mày có thể nghỉ thêm nếu cần, không phải vội."
                } else {
                    "$name. $rest. Đây là thời gian đề xuất; có thể nghỉ lâu hơn nếu chưa sẵn sàng."
                }
            }
            asks("can dung cu gi", "bai nay can gi", "dung cu nao", "can ta hay day",
                "co can ta khong", "co can day khang luc khong", "co can tham khong") -> {
                val isCatalogMove = TrainingCatalog.allMoves.any { it.name == move.name }
                when {
                    !isCatalogMove ->
                        "$name: thư viện chưa ghi rõ dụng cụ cho động tác này. Chibi không muốn đoán."
                    move.equipmentOptions.isEmpty() ->
                        "$name không yêu cầu dụng cụ riêng theo thư viện. Mày chỉ cần khoảng trống và mặt sàn ổn định."
                    else -> {
                        val options = move.equipmentOptions.sortedBy { it.ordinal }.map { equipment ->
                            when (equipment) {
                                TrainingEquipment.DUMBBELL -> "tạ đơn nhẹ"
                                TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực"
                                TrainingEquipment.STABLE_CHAIR -> "ghế vững chắc không bánh xe"
                                TrainingEquipment.MAT -> "thảm tập"
                            }
                        }
                        "$name: theo thư viện, mày có thể dùng một trong các lựa chọn: " +
                            options.joinToString(" hoặc ") +
                            ". Không cần có tất cả. Nếu không có lựa chọn nào, hãy chọn bài thay thế trong giáo án; đừng tự dùng vật không chắc chắn."
                    }
                }
            }
            asks("de hon", "nhe hon", "phuong an nhe", "easier option") ->
                WorkoutStepGuide.spokenEasierCue(name, move.easier)
            asks("luu y", "can than", "an toan", "safety") ->
                WorkoutStepGuide.spokenSafetyNoteCue(name, move.avoid)
            else -> {
                val steps = WorkoutStepGuide.split(move.steps).take(4)
                buildString {
                    append("Hướng dẫn ").append(name).append(". ")
                    steps.forEachIndexed { index, step ->
                        append("Bước ").append(index + 1).append(": ")
                            .append(safe(step, 150)).append(". ")
                    }
                    append("Mục tiêu tham khảo: ").append(dose).append(". ")
                    append("Hình minh họa không đánh giá tư thế thực tế. Dừng nếu đau hoặc có triệu chứng bất thường.")
                }
            }
        }.take(1_200)
    }

    private fun isBreathingQuestion(query: String): Boolean = listOf(
        "cach tho", "tho the nao", "nhip tho", "hit vao", "tho ra", "how to breathe", "breathing"
    ).any(query::contains)

    private fun isExerciseQuestion(text: String): Boolean {
        if (WorkoutExerciseStepQuestion.hasExplicitStepReference(text)) return true
        if (questionPhrases.any(text::contains)) return true
        val namesAnExercise = text.contains("bai ") || text.contains("dong tac ")
        return namesAnExercise && (
            text.contains(" the nao") || text.endsWith(" la gi")
        )
    }

    private fun findMove(query: String, moves: List<TrainingMove>): Match {
        val exact = moves.filter { move ->
            val name = normalize(move.name)
            name.isNotBlank() && query.contains(name)
        }
        if (exact.size == 1) return Match(selected = exact.single())
        if (exact.size > 1) return Match(ambiguous = exact.map { it.name })

        val queryTokens = query.split(' ')
            .filter { it.length >= 2 && it !in questionWords }
            .toSet()
        if (queryTokens.isEmpty()) return Match()

        val scored = moves.map { move ->
            val nameTokens = normalize(move.name).split(' ')
                .filter { it.length >= 2 && it !in questionWords }
                .toSet()
            move to nameTokens.count { it in queryTokens }
        }
        val bestScore = scored.maxOfOrNull { it.second } ?: 0
        if (bestScore <= 0) return Match()
        val best = scored.filter { it.second == bestScore }.map { it.first }
        return if (best.size == 1) Match(selected = best.single())
        else Match(ambiguous = best.map { it.name }.distinct().sorted())
    }

    private fun format(move: TrainingMove, input: String): String {
        if (isBreathingQuestion(normalize(input))) return WorkoutBreathingGuide.cue(move.name)
        WorkoutExerciseStepQuestion.answer(input, move.name, move.steps)?.let { return it }
        val steps = WorkoutStepGuide.split(move.steps).take(MAX_DISPLAY_STEPS)
        val rest = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
        return buildString {
            append("Hướng dẫn offline từ thư viện cho ").append(move.name).append(":")
            append("\n🎯 Liều lượng tham khảo: ").append(move.dose).append(".")
            if (rest != null) append("\n⏱ ").append(rest).append(".")
            WorkoutExerciseFocus.descriptionFor(move.name)?.let { append("\n🧭 ").append(it) }
            append("\n📖 Các bước:")
            steps.forEachIndexed { index, step ->
                append("\n").append(index + 1).append(". ").append(step.take(MAX_STEP_CHARS))
            }
            append("\n🌿 Dễ hơn: ").append(move.easier.take(MAX_STEP_CHARS))
            append("\n⚠️ Lưu ý: ").append(move.avoid.take(MAX_STEP_CHARS))
            append("\nGiáo án hiện tại có thể có liều lượng khác. Hình minh họa và hướng dẫn chữ không phân tích tư thế thực tế; nếu đau hoặc có triệu chứng bất thường, hãy dừng tập.")
        }
    }

    private fun normalize(input: String): String {
        val withoutDiacritics = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return withoutDiacritics
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
