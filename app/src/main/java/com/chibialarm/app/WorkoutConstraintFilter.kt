package com.chibialarm.app

import java.security.MessageDigest

/**
 * Session-only exercise substitutions for a user who wants to avoid loading through the wrists.
 * This is a movement preference, not a diagnosis or a rehabilitation prescription.
 */
data class WorkoutConstraintProfile(
    val avoidWristLoad: Boolean = false
)

object WorkoutConstraintFilter {
    data class Substitution(
        val originalName: String,
        val replacementName: String
    )

    data class Result(
        val program: TrainingProgram,
        val substitutions: List<Substitution>,
        val removedMoves: List<String>
    ) {
        val changed: Boolean get() = substitutions.isNotEmpty() || removedMoves.isNotEmpty()

        val canStart: Boolean
            get() = program.moves.any {
                it.name != "Khởi động tại chỗ" && it.name != "Thả lỏng"
            }

        val notice: String
            get() {
                if (!changed) return ""
                val replacements = substitutions.distinctBy { it.originalName to it.replacementName }
                    .joinToString(" • ") { "${it.originalName} → ${it.replacementName}" }
                val removed = removedMoves.distinct().joinToString(", ")
                return buildString {
                    if (replacements.isNotBlank()) {
                        append("Đã thay động tác: ").append(replacements).append(". ")
                    }
                    if (removed.isNotBlank()) {
                        append("Đã bỏ động tác chưa có biến thể phù hợp: ").append(removed).append(". ")
                    }
                    append("Các lựa chọn này tránh chống tay hoặc cầm tải qua cổ tay, nhưng không tương đương hoàn toàn về nhóm cơ/cường độ. Hãy đọc hướng dẫn và bỏ qua nếu không thoải mái.")
                }
            }
    }

    private val alternatives = mapOf(
        "Chống đẩy tường" to "Ép ngực tường bằng cẳng tay",
        "Chống đẩy sàn" to "Ép ngực tường bằng cẳng tay",
        "Chống đẩy gối" to "Ép ngực tường bằng cẳng tay",
        "Chống đẩy trên bề mặt cao" to "Ép ngực tường bằng cẳng tay",
        "Bird-dog" to "Dead bug cơ bụng",
        "Cat-cow nhẹ" to "Mở vai và ngực",
        "Mountain climber chậm" to "Đi bộ nhanh tại chỗ",
        "Kéo tạ đơn hoặc dây kháng lực" to "Siết bả vai đứng",
        "Gập hông với tạ đơn hoặc dây" to "Nâng hông trên sàn",
        "Đẩy vai với tạ đơn hoặc dây" to "Mở vai và ngực"
    )

    private val wristLoadHints = listOf(
        "chống đẩy", "bird-dog", "cat-cow", "mountain climber",
        "tạ đơn hoặc dây", "dây kháng lực"
    )

    fun isAllowed(move: TrainingMove, profile: WorkoutConstraintProfile): Boolean =
        !profile.avoidWristLoad ||
            wristLoadHints.none { hint -> move.name.contains(hint, ignoreCase = true) }

    /** Stable identity for a planned sequence; prevents restoring progress into a different plan. */
    fun fingerprint(moves: List<TrainingMove>): String {
        val canonical = moves.joinToString("\u001f") {
            "${it.name}\u001e${it.dose}\u001e${it.seconds}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun apply(
        program: TrainingProgram,
        profile: WorkoutConstraintProfile
    ): Result {
        if (!profile.avoidWristLoad) return Result(program, emptyList(), emptyList())

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val substitutions = mutableListOf<Substitution>()
        val removed = mutableListOf<String>()
        val filteredMoves = program.moves.mapNotNull { move ->
            val replacementName = alternatives[move.name]
            if (replacementName != null) {
                val replacement = catalog[replacementName]
                if (replacement == null) {
                    removed += move.name
                    null
                } else {
                    substitutions += Substitution(move.name, replacement.name)
                    replacement
                }
            } else if (isAllowed(move, profile)) {
                move
            } else {
                removed += move.name
                null
            }
        }
        return Result(program.copy(moves = filteredMoves), substitutions, removed)
    }
}
