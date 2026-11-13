package com.chibialarm.app

import java.security.MessageDigest

/**
 * Session-only movement preferences, not diagnoses or rehabilitation prescriptions.
 * Never persist these as a health profile without separate consent and data controls.
 */
data class WorkoutConstraintProfile(
    val avoidWristLoad: Boolean = false,
    val avoidFloorPositions: Boolean = false,
    val avoidOverheadReach: Boolean = false
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
                it.name !in setOf("Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm")
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
                    append("Các động tác được giữ lại đã được lọc theo lựa chọn hiện tại. Việc thay hoặc bỏ bài có thể làm thay đổi nhóm cơ/cường độ, không tương đương hoàn toàn; hãy đọc hướng dẫn và bỏ qua nếu không thoải mái.")
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
        "Gập hông với tạ đơn hoặc dây" to "Gập hông không dụng cụ",
        "Đẩy vai với tạ đơn hoặc dây" to "Mở vai và ngực"
    )

    private val wristLoadHints = listOf(
        "chống đẩy", "bird-dog", "cat-cow", "mountain climber",
        "tạ đơn hoặc dây", "dây kháng lực"
    )

    // Reviewed catalog movements that require getting down to or supporting the body on the floor.
    private val floorPositionHints = listOf(
        "nâng hông trên sàn", "bird-dog", "dead bug", "plank khuỷu",
        "side plank", "cat-cow", "mountain climber", "chống đẩy sàn", "chống đẩy gối"
    )

    fun isAllowed(move: TrainingMove, profile: WorkoutConstraintProfile): Boolean {
        val wristAllowed = !profile.avoidWristLoad || (!move.requiresExternalLoad &&
            wristLoadHints.none { hint -> move.name.contains(hint, ignoreCase = true) })
        val floorAllowed = !profile.avoidFloorPositions ||
            floorPositionHints.none { hint -> move.name.contains(hint, ignoreCase = true) }
        val overheadAllowed = !profile.avoidOverheadReach || !move.requiresOverheadReach
        return wristAllowed && floorAllowed && overheadAllowed
    }

    /** Stable identity for a planned sequence; active constraints invalidate old checkpoints. */
    fun fingerprint(
        moves: List<TrainingMove>,
        profile: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): String {
        val constraintPrefix = if (profile.avoidWristLoad || profile.avoidFloorPositions) {
            "constraints:${profile.avoidWristLoad}:${profile.avoidFloorPositions}:${profile.avoidOverheadReach}\u001d"
        } else ""
        val canonical = constraintPrefix + moves.joinToString("\u001f") {
            "${it.name}\u001e${it.dose}\u001e${it.seconds}\u001e${it.restSeconds}\u001e" +
                it.equipmentOptions.map { equipment -> equipment.name }.sorted().joinToString(",") +
                "\u001e${it.requiresOverheadReach}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun apply(
        program: TrainingProgram,
        profile: WorkoutConstraintProfile
    ): Result {
        if (!profile.avoidWristLoad && !profile.avoidFloorPositions && !profile.avoidOverheadReach) {
            return Result(program, emptyList(), emptyList())
        }

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val substitutions = mutableListOf<Substitution>()
        val removed = mutableListOf<String>()
        val filteredMoves = program.moves.mapNotNull { move ->
            val replacementName = if (profile.avoidWristLoad) alternatives[move.name] else null
            if (replacementName != null) {
                val replacement = catalog[replacementName]
                if (replacement != null && isAllowed(replacement, profile)) {
                    substitutions += Substitution(move.name, replacement.name)
                    replacement
                } else {
                    removed += move.name
                    null
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
