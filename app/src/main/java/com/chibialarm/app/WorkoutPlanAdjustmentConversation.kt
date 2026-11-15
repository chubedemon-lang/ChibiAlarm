package com.chibialarm.app

/**
 * Turns explicit equipment or movement-limit requests into preview-only options
 * from the reviewed offline catalog. It never mutates the active workout.
 */
object WorkoutPlanAdjustmentConversation {
    private const val MAX_INPUT_CHARS = 300
    private val warmupAndCooldown = setOf(
        "Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm"
    )

    private data class Candidate(
        val order: Int,
        val name: String,
        val moves: List<String>,
        val penalty: Int,
        val gentleRank: Int,
        val notes: String
    )

    fun answer(
        input: String,
        profile: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        equipmentSelection: Int = 0,
        additionalEquipment: Set<TrainingEquipment> = emptySet(),
        programs: List<TrainingProgram> = TrainingCatalog.programs
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)

        val noEquipment = containsAny(query, "khong co dung cu", "khong co thiet bi")
        val noDumbbell = containsAny(query, "khong co ta", "khong co ta don", "khong dung ta")
        val noBand = containsAny(query, "khong co day", "khong co day khang luc")
        val noChair = containsAny(query, "khong co ghe", "khong co ghe tap")
        val noMat = containsAny(query, "khong co tham", "khong co tham tap")

        val avoidFloor = profile.avoidFloorPositions || containsAny(
            query, "khong muon nam san", "khong muon xuong san",
            "khong the nam san", "khong muon tap tren san"
        )
        val avoidWrist = profile.avoidWristLoad || containsAny(
            query, "khong muon chong tay", "khong the chong tay",
            "khong muon ti co tay", "co tay khong thoai mai"
        )
        val avoidOverhead = profile.avoidOverheadReach || containsAny(
            query, "khong muon dua tay qua dau", "han che dua tay qua dau"
        )
        val preferGentle = containsAny(
            query, "bai nao de hon", "co bai nao de hon",
            "hom nay met", "it nang luong", "muon tap nhe hon", "giam do kho"
        )
        val asksForChange = noEquipment || noDumbbell || noBand || noChair || noMat ||
            avoidFloor != profile.avoidFloorPositions ||
            avoidWrist != profile.avoidWristLoad ||
            avoidOverhead != profile.avoidOverheadReach ||
            preferGentle || containsAny(query, "thay bai", "thay the", "doi bai", "bai thay the")
        if (!asksForChange) return null

        val effectiveProfile = profile.copy(
            avoidFloorPositions = avoidFloor,
            avoidWristLoad = avoidWrist,
            avoidOverheadReach = avoidOverhead
        )

        var effectiveEquipment = equipmentSelection.coerceIn(0, 5)
        var extras = additionalEquipment.toSet()
        when {
            noEquipment || (noDumbbell && noBand) -> {
                effectiveEquipment = 0
                extras = emptySet()
            }
            else -> {
                if (noDumbbell) {
                    extras = extras - TrainingEquipment.DUMBBELL
                    effectiveEquipment = when (effectiveEquipment) {
                        2 -> 5 // Keep the explicitly selected resistance-band option.
                        4 -> 0
                        else -> effectiveEquipment
                    }
                }
                if (noBand) {
                    extras = extras - TrainingEquipment.RESISTANCE_BAND
                    effectiveEquipment = when (effectiveEquipment) {
                        2 -> 4 // Keep the explicitly selected dumbbell option.
                        5 -> 0
                        else -> effectiveEquipment
                    }
                }
                if (noChair) {
                    extras = extras - TrainingEquipment.STABLE_CHAIR
                    if (effectiveEquipment == 3) effectiveEquipment = 0
                }
                if (noMat) {
                    extras = extras - TrainingEquipment.MAT
                    if (effectiveEquipment == 1) effectiveEquipment = 0
                }
            }
        }

        val gentleOrder = listOf(
            "starter", "chair", "lowcardio", "quickrecovery", "mobility",
            "quickmobility", "desk", "recovery"
        )
        val candidates = programs.mapIndexedNotNull { index, program ->
            val constrained = WorkoutConstraintFilter.apply(program, effectiveProfile)
            val equipped = WorkoutEquipmentFilter.apply(
                constrained.program, effectiveEquipment, extras
            )
            val mainMoves = equipped.program.moves
                .filterNot { it.name in warmupAndCooldown }
                .map { it.name }
            if (mainMoves.isEmpty()) return@mapIndexedNotNull null

            val notes = buildList {
                if (constrained.changed) add("đã lọc theo hạn chế vận động")
                if (equipped.changed) add("đã bỏ bài cần dụng cụ chưa chọn")
            }.joinToString("; ")

            Candidate(
                order = index,
                name = program.name,
                moves = mainMoves.take(3),
                penalty = constrained.removedMoves.size + equipped.removedMoves.size,
                gentleRank = gentleOrder.indexOf(program.id).let { if (it < 0) Int.MAX_VALUE else it },
                notes = notes
            )
        }.sortedWith(
            compareBy<Candidate> { if (preferGentle) it.gentleRank else 0 }
                .thenBy { it.penalty }
                .thenBy { it.order }
        ).take(3)

        if (candidates.isEmpty()) {
            return "Chibi chưa tìm thấy chương trình còn động tác chính phù hợp với các điều kiện này. Chibi không tự bịa bài thay thế. Hãy nới một bộ lọc hoặc hỏi chuyên gia phù hợp trước khi thử động tác mới."
        }

        return buildString {
            append("Chibi đã lọc thử từ thư viện offline theo điều kiện mày vừa nói. ")
            append("Đây chỉ là bản xem trước; giáo án và buổi đang tập chưa bị thay đổi.")
            append("\n\nPhương án để xem trước:")
            candidates.forEachIndexed { index, candidate ->
                append("\n").append(index + 1).append(". ").append(candidate.name)
                append("\n   ").append(candidate.moves.joinToString(" • "))
                if (candidate.notes.isNotBlank()) {
                    append("\n   ").append(candidate.notes)
                }
            }
            append("\n\nMở mục “Xem bài” để kiểm tra liều lượng và hướng dẫn, rồi tự xác nhận trước khi bắt đầu. ")
            append("Thay hoặc bỏ bài có thể làm thay đổi nhóm cơ/cường độ và không tương đương hoàn toàn. ")
            append("Hình minh họa không phân tích tư thế thực tế; dừng tập nếu đau, chóng mặt, đau ngực hoặc khó thở bất thường.")
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any(text::contains)

    private fun normalize(input: String): String {
        val withoutDiacritics = java.text.Normalizer.normalize(
            input.lowercase(java.util.Locale.ROOT).replace('đ', 'd'),
            java.text.Normalizer.Form.NFD
        )
        return withoutDiacritics
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
