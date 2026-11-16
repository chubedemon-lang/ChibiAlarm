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
        "luu y khi tap", "luu y khi thuc hien", "nghi bao lau",
        "bao nhieu hiep", "bao nhieu lan", "bao nhieu giay",
        "giai thich"
    )

    private val questionWords = setOf(
        "cach", "tap", "huong", "dan", "lam", "sao", "de", "thuc", "hien",
        "nhu", "the", "nao", "ky", "thuat", "luu", "y", "khi", "nghi",
        "bao", "nhieu", "hiep", "lan", "giay", "giai", "thich", "bai",
        "dong", "tac", "nay", "la", "gi", "toi", "tao", "minh", "muon",
        "biet", "cho", "tung", "buoc", "theo", "voi", "nua", "phut", "dung"
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
                "Mày hãy nêu rõ tên bài, ví dụ: “cách tập Squat không tạ” hoặc “hướng dẫn Plank”."
            )
        return format(move)
    }

    private fun isExerciseQuestion(text: String): Boolean {
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

    private fun format(move: TrainingMove): String {
        val steps = WorkoutStepGuide.split(move.steps).take(MAX_DISPLAY_STEPS)
        val rest = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
        return buildString {
            append("Hướng dẫn offline từ thư viện cho ").append(move.name).append(":")
            append("\n🎯 Liều lượng tham khảo: ").append(move.dose).append(".")
            if (rest != null) append("\n⏱ ").append(rest).append(".")
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
