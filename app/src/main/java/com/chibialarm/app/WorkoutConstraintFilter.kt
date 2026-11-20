package com.chibialarm.app

import java.security.MessageDigest

/**
 * Session-only movement preferences, not diagnoses or rehabilitation prescriptions.
 * Never persist these as a health profile without separate consent and data controls.
 */
data class WorkoutConstraintProfile(
    val avoidWristLoad: Boolean = false,
    val avoidFloorPositions: Boolean = false,
    val avoidOverheadReach: Boolean = false,
    val avoidJumping: Boolean = false,
    val avoidKneeling: Boolean = false
) {
    companion object {
        /**
         * Converts visible session selections into the same filters used by plan previews,
         * history variety and workout launch. Low-impact explicitly means no jumping.
         */
        fun fromSessionSelections(
            impact: Int,
            avoidWristLoad: Boolean = false,
            avoidFloorPositions: Boolean = false,
            avoidOverheadReach: Boolean = false,
            avoidKneeling: Boolean = false
        ): WorkoutConstraintProfile {
            require(impact in 0..2)
            return WorkoutConstraintProfile(
                avoidWristLoad = avoidWristLoad,
                avoidFloorPositions = avoidFloorPositions,
                avoidOverheadReach = avoidOverheadReach,
                avoidJumping = impact == 1,
                avoidKneeling = avoidKneeling
            )
        }
    }
}

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
    private val jumpingAlternatives = mapOf(
        "Bật nhảy dang tay chân" to "Bước ngang nhẹ"
    )
    private val kneelingAlternatives = mapOf(
        "Bird-dog" to "Dead bug cơ bụng",
        "Cat-cow nhẹ" to "Mở vai và ngực",
        "Chống đẩy gối" to "Chống đẩy tường"
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
        val jumpingAllowed = !profile.avoidJumping || !move.requiresJumping
        val kneelingAllowed = !profile.avoidKneeling || !move.requiresKneeling
        return wristAllowed && floorAllowed && overheadAllowed && jumpingAllowed && kneelingAllowed
    }

    /** Stable identity for a planned sequence; active constraints invalidate old checkpoints. */
    fun fingerprint(
        moves: List<TrainingMove>,
        profile: WorkoutConstraintProfile = WorkoutConstraintProfile()
    ): String {
        // Preserve legacy checkpoint fingerprints for existing wrist/floor-only profiles.
        // The new preference adds a distinct prefix only when it is actually enabled.
        val constraintPrefix = when {
            profile.avoidKneeling ->
                "constraints:${profile.avoidWristLoad}:${profile.avoidFloorPositions}:${profile.avoidOverheadReach}:${profile.avoidJumping}:kneeling\u001d"
            profile.avoidJumping ->
                "constraints:${profile.avoidWristLoad}:${profile.avoidFloorPositions}:${profile.avoidOverheadReach}:jump\u001d"
            profile.avoidOverheadReach ->
                "constraints:${profile.avoidWristLoad}:${profile.avoidFloorPositions}:overhead\u001d"
            profile.avoidWristLoad || profile.avoidFloorPositions ->
                "constraints:${profile.avoidWristLoad}:${profile.avoidFloorPositions}\u001d"
            else -> ""
        }
        val canonical = constraintPrefix + moves.joinToString("\u001f") {
            "${it.name}\u001e${it.dose}\u001e${it.seconds}\u001e${it.restSeconds}\u001e" +
                it.equipmentOptions.map { equipment -> equipment.name }.sorted().joinToString(",")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun apply(
        program: TrainingProgram,
        profile: WorkoutConstraintProfile
    ): Result {
        if (!profile.avoidWristLoad && !profile.avoidFloorPositions &&
            !profile.avoidOverheadReach && !profile.avoidJumping && !profile.avoidKneeling
        ) {
            return Result(program, emptyList(), emptyList())
        }

        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val substitutions = mutableListOf<Substitution>()
        val removed = mutableListOf<String>()
        val filteredMoves = program.moves.mapNotNull { move ->
            val replacementName = alternatives[move.name].takeIf { profile.avoidWristLoad }
                ?: kneelingAlternatives[move.name].takeIf { profile.avoidKneeling && move.requiresKneeling }
                ?: if (profile.avoidJumping && move.requiresJumping) jumpingAlternatives[move.name] else null
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
