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
    // Keep this aligned with FitnessPlanAdvisor's impact=1 recommendations.
    // Only catalog programs explicitly chosen for low-impact sessions are eligible.
    private val lowImpactProgramIds = setOf(
        "quicklowcardio", "quickmobility", "lowcardio", "mobility",
        "quickrecovery", "recovery"
    )

    private data class Candidate(
        val order: Int,
        val name: String,
        val moves: List<String>,
        val penalty: Int,
        val gentleRank: Int,
        val notes: String,
        val estimatedSeconds: Int,
        val goalRank: Int
    )

    fun answer(
        input: String,
        profile: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        equipmentSelection: Int = 0,
        additionalEquipment: Set<TrainingEquipment> = emptySet(),
        programs: List<TrainingProgram> = TrainingCatalog.programs,
        level: Int = 0,
        goal: Int? = null,
        impact: Int = 0
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        require(impact in 0..2)
        val query = normalize(input)
        val naturalRequest = FitnessNaturalRequest.parse(input)
        // Preserve the already selected session impact preference when the user asks a
        // follow-up without repeating it. A fresh symptom report always wins.
        val effectiveImpact = maxOf(impact, naturalRequest.impact ?: 0)
        if (effectiveImpact == 2) return if (naturalRequest.impact == 2) {
            naturalRequest.reply
        } else {
            "Mày đã chọn trạng thái đang đau hoặc có chấn thương. Chibi không đề xuất đổi bài hay khởi chạy buổi tập khi chưa được chuyên gia đánh giá. Hãy dừng nếu đang có triệu chứng."
        }
        val preferLowImpact = effectiveImpact == 1
        val remainingMinutes = naturalRequest.minutes.takeIf { hasRemainingTimeCue(query) }
        val effectiveLevel = (naturalRequest.level ?: level).coerceIn(0, 2)
        val effectiveGoal = (naturalRequest.goal ?: goal)?.coerceIn(0, 5)

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
        val requestsAvoidKneeling = containsAny(
            query, "khong quy goi", "khong muon quy goi", "khong the quy goi",
            "tranh quy goi", "dung quy goi", "han che quy goi"
        )
        val allowsKneeling = containsAny(
            query, "co the quy goi", "quy goi duoc", "khong can tranh quy goi"
        )
        val effectiveAvoidKneeling = when {
            requestsAvoidKneeling -> true
            allowsKneeling -> false
            else -> profile.avoidKneeling
        }
        val preferGentle = containsAny(
            query, "bai nao de hon", "co bai nao de hon",
            "hom nay met", "it nang luong", "muon tap nhe hon", "giam do kho"
        )
        val asksForChange = remainingMinutes != null || noEquipment || noDumbbell || noBand || noChair || noMat ||
            avoidFloor != profile.avoidFloorPositions ||
            avoidWrist != profile.avoidWristLoad ||
            avoidOverhead != profile.avoidOverheadReach ||
            effectiveAvoidKneeling != profile.avoidKneeling ||
            preferLowImpact || preferGentle ||
            containsAny(query, "thay bai", "thay the", "doi bai", "bai thay the")
        if (!asksForChange) return null

        val effectiveProfile = profile.copy(
            avoidFloorPositions = avoidFloor,
            avoidWristLoad = avoidWrist,
            avoidOverheadReach = avoidOverhead,
            avoidKneeling = effectiveAvoidKneeling
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

        // A history-derived follow-up names one exercise explicitly. Resolve it against the
        // canonical catalog and show only reviewed alternatives; never silently switch a plan.
        val requestedMove = targetedMoveName(input)
        if (requestedMove != null) {
            val catalog = TrainingCatalog.allMoves
            val normalizedTarget = normalize(requestedMove)
            val exactMove = catalog.firstOrNull { normalize(it.name) == normalizedTarget }
            val partialMatches = if (exactMove == null) {
                catalog.filter { move ->
                    val candidate = normalize(move.name)
                    candidate.startsWith(normalizedTarget) || normalizedTarget.startsWith(candidate)
                }.distinctBy { normalize(it.name) }
            } else emptyList()
            val sourceMove = exactMove ?: partialMatches.singleOrNull()
            if (sourceMove == null) {
                return if (partialMatches.size > 1) {
                    "Tên động tác \"$requestedMove\" chưa đủ cụ thể. Chibi tìm thấy: " +
                        partialMatches.take(4).joinToString(", ") { it.name } +
                        ". Hãy chọn đúng tên trong thư viện; Chibi chưa đoán hoặc đổi bài."
                } else {
                    "Chibi chưa tìm thấy động tác \"$requestedMove\" trong thư viện đã duyệt. " +
                        "Hãy kiểm tra tên bài; giáo án chưa bị thay đổi."
                }
            }

            val availableEquipment =
                TrainingEquipmentAvailability.fromPreference(effectiveEquipment) + extras
            val alternatives = WorkoutMoveSubstitution.alternativesFor(
                sourceMove, effectiveProfile, availableEquipment
            ).filter { TrainingEquipmentAvailability.allows(it, availableEquipment) }
                .distinctBy { it.name }
                .take(3)
            if (alternatives.isEmpty()) {
                return "Chibi chưa tìm thấy biến thể đã duyệt phù hợp với dụng cụ và các hạn chế hiện tại " +
                    "cho ${sourceMove.name}. Chibi không tự chọn bài khác; giáo án chưa bị thay đổi."
            }

            return buildString {
                append("Động tác được hỏi: ").append(sourceMove.name)
                append("\nCác phương án thay thế đã có trong thư viện và vượt qua bộ lọc hiện tại:")
                alternatives.forEachIndexed { index, move ->
                    append("\n").append(index + 1).append(". ").append(move.name)
                    append(" — ").append(move.dose)
                    append("\n   Hướng dẫn: ").append(move.steps.take(220))
                    append("\n   Lưu ý: ").append(move.avoid.take(160))
                }
                append("\n\nĐây chỉ là bản xem trước; Chibi chưa thay đổi giáo án hoặc buổi đang tập.")
                append(" Các bài thay thế không tương đương hoàn toàn về nhóm cơ/cường độ.")
                append(" Hãy đọc hướng dẫn và chỉ chọn nếu thấy thoải mái; dừng nếu đau hoặc có triệu chứng bất thường.")
            }
        }

        val gentleOrder = listOf(
            "starter", "chair", "lowcardio", "quickrecovery", "mobility",
            "quickmobility", "desk", "recovery"
        )
        val candidates = programs.mapIndexedNotNull { index, program ->
            if (preferLowImpact && program.id !in lowImpactProgramIds) {
                return@mapIndexedNotNull null
            }
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
                if (preferLowImpact) add("đã giới hạn ở chương trình ít tác động, không nhảy")
                if (effectiveProfile.avoidKneeling) add("đã lọc động tác phải quỳ gối")
            }.joinToString("; ")

            Candidate(
                order = index,
                name = program.name,
                moves = mainMoves.take(3),
                penalty = constrained.removedMoves.size + equipped.removedMoves.size,
                gentleRank = gentleOrder.indexOf(program.id).let { if (it < 0) Int.MAX_VALUE else it },
                notes = notes,
                estimatedSeconds = estimateSeconds(equipped.program, effectiveLevel),
                goalRank = goalRank(program.id, effectiveGoal)
            )
        }
        val orderedCandidates = candidates.sortedWith(
            if (remainingMinutes != null) {
                val targetSeconds = remainingMinutes.toLong() * 60L
                compareBy<Candidate> { it.estimatedSeconds.toLong() > targetSeconds }
                    .thenBy { if (preferGentle) it.gentleRank else 0 }
                    .thenBy { it.goalRank }
                    .thenBy { kotlin.math.abs(it.estimatedSeconds.toLong() - targetSeconds) }
                    .thenBy { it.penalty }
                    .thenBy { it.order }
            } else {
                compareBy<Candidate> { if (preferGentle) it.gentleRank else 0 }
                    .thenBy { it.goalRank }
                    .thenBy { it.penalty }
                    .thenBy { it.order }
            }
        ).take(3)

        if (candidates.isEmpty()) {
            return if (preferLowImpact) {
                "Chibi chưa tìm thấy chương trình đã duyệt ít tác động phù hợp với dụng cụ và hạn chế hiện tại. Chibi không tự chuyển sang bài cường độ cao hoặc tự bịa bài thay thế; giáo án chưa thay đổi."
            } else {
                "Chibi chưa tìm thấy chương trình còn động tác chính phù hợp với các điều kiện này. Chibi không tự bịa bài thay thế. Hãy nới một bộ lọc hoặc hỏi chuyên gia phù hợp trước khi thử động tác mới."
            }
        }

        return buildString {
            append("Chibi đã lọc thử từ thư viện offline theo điều kiện mày vừa nói. ")
            if (preferLowImpact) {
                append("Chibi chỉ giữ các chương trình đã duyệt theo hướng ít tác động, không nhảy. ")
            }
            append("Đây chỉ là bản xem trước; giáo án và buổi đang tập chưa bị thay đổi.")
            if (remainingMinutes != null) {
                append("\nMày còn ").append(remainingMinutes).append(" phút. Chibi ưu tiên mục tiêu đã chọn và phương án nằm trong thời gian này nếu có.")
                append(" Thời lượng là ước tính gồm vận động, nghỉ bắt buộc và chuyển tiếp; không phải cam kết hoàn thành.")
            }
            append("\n\nPhương án để xem trước:")
            orderedCandidates.forEachIndexed { index, candidate ->
                append("\n").append(index + 1).append(". ").append(candidate.name)
                append("\n   ").append(candidate.moves.joinToString(" • "))
                if (candidate.notes.isNotBlank()) {
                    append("\n   ").append(candidate.notes)
                }
                if (remainingMinutes != null) {
                    append("\n   Ước tính: ").append(durationLabel(candidate.estimatedSeconds))
                    if (candidate.estimatedSeconds > remainingMinutes * 60) {
                        append(" — dài hơn thời gian còn lại; Chibi không tự cắt hiệp bắt buộc.")
                    }
                }
            }
            append("\n\nMở mục “Xem bài” để kiểm tra liều lượng và hướng dẫn, rồi tự xác nhận trước khi bắt đầu. ")
            append("Thay hoặc bỏ bài có thể làm thay đổi nhóm cơ/cường độ và không tương đương hoàn toàn. ")
            append("Hình minh họa không phân tích tư thế thực tế; dừng tập nếu đau, chóng mặt, đau ngực hoặc khó thở bất thường.")
        }
    }

    private fun targetedMoveName(input: String): String? {
        val match = Regex("""(?:động tác|bài tập)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(input) ?: return null
        return match.groupValues[1].trim().trimEnd('.', '!', '?')
            .takeIf { it.isNotBlank() && it.length <= 100 }
    }

    private fun hasRemainingTimeCue(query: String): Boolean {
        // A plain duration belongs to initial plan setup. Only an affirmative "còn X phút"
        // statement is treated as the time left in an active session.
        if (containsAny(query, "khong con", "khong con lai")) return false
        return Regex("""(?:^| )(?:chi )?con(?: lai)?(?: (?:dung|khoang|la))? """)
            .containsMatchIn(query)
    }

    private fun estimateSeconds(program: TrainingProgram, level: Int): Int {
        val active = program.moves.sumOf { WorkoutDurationPlanner.activeSeconds(it).toLong() }
        val rest = program.moves.sumOf { WorkoutDurationPlanner.prescribedRestSeconds(it).toLong() }
        val transitions = (program.moves.size - 1).coerceAtLeast(0).toLong() *
            WorkoutDurationPlanner.transitionAllowance(level)
        return (active + rest + transitions).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun goalRank(programId: String, goal: Int?): Int {
        val preferred = when (goal) {
            0 -> setOf("starter", "chair", "full", "legs", "upper", "weighted", "quick")
            1 -> setOf("lowcardio", "quicklowcardio")
            2 -> setOf("mobility", "quickmobility", "quickrecovery")
            3 -> setOf("desk", "quickmobility")
            4 -> setOf("starter", "chair", "quick", "quickrecovery")
            5 -> setOf("advanced", "weighted", "full", "legs")
            else -> return 0
        }
        return if (programId in preferred) 0 else 1
    }

    private fun durationLabel(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return if (minutes == 0) "$seconds giây" else "$minutes phút $seconds giây"
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
