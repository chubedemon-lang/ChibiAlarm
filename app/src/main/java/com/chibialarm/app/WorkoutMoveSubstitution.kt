package com.chibialarm.app

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Curated, offline substitutions for a few common exercise patterns after user confirmation.
 * This is a convenience for changing an unstarted step, not injury treatment.
 */
object WorkoutMoveSubstitution {
    private const val MAX_MOVES = 500
    private const val MAX_CHECKPOINT_CHARS = 4_096

    private val easierNames = mapOf(
        "Squat không tạ" to listOf("Đứng lên – ngồi xuống"),
        "Squat chậm 3 giây" to listOf("Squat không tạ", "Đứng lên – ngồi xuống"),
        "Bước lùi – lunge" to listOf("Squat không tạ", "Đứng lên – ngồi xuống"),
        // Offer a forearm-supported option as well as hand-supported regressions.
        // The constraint filter below will remove the latter when wrist loading is avoided.
        "Chống đẩy sàn" to listOf("Chống đẩy gối", "Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Chống đẩy tường" to listOf("Ép ngực tường bằng cẳng tay"),
        "Cat-cow nhẹ" to listOf("Mở vai và ngực"),
        "Chống đẩy gối" to listOf("Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Chống đẩy trên bề mặt cao" to listOf("Chống đẩy tường", "Ép ngực tường bằng cẳng tay"),
        "Mountain climber chậm" to listOf("Đi bộ nhanh tại chỗ"),
        "Bật nhảy dang tay chân" to listOf("Bước ngang nhẹ"),
        "Bird-dog" to listOf("Dead bug cơ bụng"),
        "Plank khuỷu hoặc plank gối" to listOf("Bird-dog", "Dead bug cơ bụng"),
        "Side plank gối" to listOf("Bird-dog", "Dead bug cơ bụng"),
        "Kéo tạ đơn hoặc dây kháng lực" to listOf("Siết bả vai đứng"),
        "Gập hông với tạ đơn hoặc dây" to listOf("Gập hông không dụng cụ", "Nâng hông trên sàn"),
        "Đẩy vai với tạ đơn hoặc dây" to listOf("Mở vai và ngực", "Ép ngực tường bằng cẳng tay")
    )

    // Explanations describe observable movement changes, not medical suitability or treatment.
    private val substitutionExplanations = mapOf(
        ("Squat không tạ" to "Đứng lên – ngồi xuống") to
            "Có ghế vững làm mốc để điều chỉnh biên độ đứng và ngồi; chỉ dùng ghế không bánh xe, đặt chắc trên sàn.",
        ("Squat chậm 3 giây" to "Squat không tạ") to
            "Bỏ nhịp hạ chậm ba giây, giữ mẫu squat cơ bản và nhịp độ tự chọn.",
        ("Squat chậm 3 giây" to "Đứng lên – ngồi xuống") to
            "Bỏ nhịp hạ chậm và dùng ghế vững làm mốc cho động tác đứng lên, ngồi xuống.",
        ("Bước lùi – lunge" to "Squat không tạ") to
            "Bỏ bước lùi và yêu cầu phối hợp chân trước–sau; vẫn cần đứng vững và kiểm soát biên độ.",
        ("Bước lùi – lunge" to "Đứng lên – ngồi xuống") to
            "Bỏ bước lùi, dùng ghế vững làm mốc và điều chỉnh độ sâu theo khả năng.",
        ("Chống đẩy sàn" to "Chống đẩy gối") to
            "Đặt gối xuống sàn để thay đổi đòn bẩy; động tác vẫn chống bằng bàn tay.",
        ("Chống đẩy sàn" to "Chống đẩy tường") to
            "Chuyển điểm tựa từ sàn lên tường đứng, giúp điều chỉnh độ nghiêng bằng khoảng cách tới tường.",
        ("Chống đẩy sàn" to "Ép ngực tường bằng cẳng tay") to
            "Tì cẳng tay lên tường thay vì chống bằng lòng bàn tay; đây không phải bảo đảm phù hợp với mọi vấn đề cổ tay.",
        ("Chống đẩy tường" to "Ép ngực tường bằng cẳng tay") to
            "Đổi điểm tiếp xúc từ bàn tay sang cẳng tay khi đẩy tường; chỉ thực hiện nếu tư thế này dễ chịu.",
        ("Cat-cow nhẹ" to "Mở vai và ngực") to
            "Bỏ tư thế quỳ và chuyển sang vận động vai, ngực ở tư thế đứng.",
        ("Chống đẩy gối" to "Chống đẩy tường") to
            "Chuyển từ chống tay trên sàn sang chống tường đứng để dễ điều chỉnh độ nghiêng.",
        ("Chống đẩy gối" to "Ép ngực tường bằng cẳng tay") to
            "Chuyển điểm tựa sang cẳng tay trên tường; bỏ qua nếu tư thế này gây khó chịu ở cổ tay.",
        ("Chống đẩy trên bề mặt cao" to "Chống đẩy tường") to
            "Dùng tường làm điểm tựa cố định thay cho bề mặt kê cao, với độ nghiêng điều chỉnh bằng vị trí đứng.",
        ("Chống đẩy trên bề mặt cao" to "Ép ngực tường bằng cẳng tay") to
            "Dùng cẳng tay tì tường thay cho bàn tay trên bề mặt kê cao; chỉ chọn nếu dễ chịu.",
        ("Mountain climber chậm" to "Đi bộ nhanh tại chỗ") to
            "Bỏ tư thế chống tay và kéo gối liên tục; chuyển sang bước tại chỗ theo nhịp có thể kiểm soát.",
        ("Bật nhảy dang tay chân" to "Bước ngang nhẹ") to
            "Thay động tác bật và tiếp đất bằng bước ngang luân phiên, không cần rời cả hai chân khỏi sàn.",
        ("Bird-dog" to "Dead bug cơ bụng") to
            "Chuyển từ tư thế chống tay và gối sang nằm ngửa, phối hợp tay chân luân phiên.",
        ("Plank khuỷu hoặc plank gối" to "Bird-dog") to
            "Thay việc giữ tư thế plank liên tục bằng các nhịp nâng tay/chân có kiểm soát; vẫn cần tư thế sàn.",
        ("Plank khuỷu hoặc plank gối" to "Dead bug cơ bụng") to
            "Chuyển từ giữ thân người trong plank sang phối hợp tay chân khi nằm ngửa.",
        ("Side plank gối" to "Bird-dog") to
            "Bỏ tư thế giữ nghiêng và chuyển sang các nhịp nâng tay/chân luân phiên trên sàn.",
        ("Side plank gối" to "Dead bug cơ bụng") to
            "Bỏ tư thế giữ nghiêng, chuyển sang phối hợp tay chân khi nằm ngửa.",
        ("Kéo tạ đơn hoặc dây kháng lực" to "Siết bả vai đứng") to
            "Bỏ tạ và dây kháng lực; tập trung vào việc siết rồi thả bả vai ở tư thế đứng.",
        ("Gập hông với tạ đơn hoặc dây" to "Gập hông không dụng cụ") to
            "Bỏ tải ngoài và tập mẫu gập hông bằng trọng lượng cơ thể.",
        ("Gập hông với tạ đơn hoặc dây" to "Nâng hông trên sàn") to
            "Chuyển từ gập hông đứng có tải sang nâng hông khi nằm trên sàn; cần chỗ nằm vững.",
        ("Đẩy vai với tạ đơn hoặc dây" to "Mở vai và ngực") to
            "Bỏ tải ngoài và chuyển sang vận động vai, ngực không dùng tạ hoặc dây.",
        ("Đẩy vai với tạ đơn hoặc dây" to "Ép ngực tường bằng cẳng tay") to
            "Bỏ tạ và dây, chuyển sang đẩy tường bằng cẳng tay; chỉ thực hiện trong biên độ dễ chịu."
    )

    private val catalogMoves: List<TrainingMove> by lazy {
        TrainingCatalog.allMoves
    }

    fun alternativesFor(
        move: TrainingMove,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        availableEquipment: Set<TrainingEquipment> = emptySet()
    ): List<TrainingMove> =
        easierNames[move.name].orEmpty()
            .mapNotNull { name -> catalogMoves.firstOrNull { it.name == name } }
            .filter {
                WorkoutConstraintFilter.isAllowed(it, constraints) &&
                    TrainingEquipmentAvailability.allows(it, availableEquipment)
            }
            .distinctBy { it.name }

    /** Explain only a curated pair; unknown pairs receive no invented rationale. */
    fun explanationFor(original: TrainingMove, replacement: TrainingMove): String? =
        substitutionExplanations[original.name to replacement.name]

    /** Returns a copied list only when the selected replacement is explicitly allowed. */
    fun replace(
        currentMoves: List<TrainingMove>,
        index: Int,
        replacement: TrainingMove,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        availableEquipment: Set<TrainingEquipment> = emptySet()
    ): List<TrainingMove>? {
        val current = currentMoves.getOrNull(index) ?: return null
        val approved = alternativesFor(current, constraints, availableEquipment)
            .firstOrNull { it.name == replacement.name } ?: return null
        // Insert the canonical catalog object, never caller-supplied exercise instructions.
        return currentMoves.toMutableList().also { it[index] = approved }
    }

    /** Store only bounded index/name pairs; no free-form transcript or health text is accepted. */
    fun encode(
        substitutions: Map<Int, TrainingMove>,
        originalMoves: List<TrainingMove>,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        availableEquipment: Set<TrainingEquipment> = emptySet()
    ): String {
        if (originalMoves.isEmpty() || originalMoves.size > MAX_MOVES) return ""
        return substitutions.entries.asSequence()
            .filter { (index, replacement) ->
                index in originalMoves.indices &&
                    alternativesFor(originalMoves[index], constraints, availableEquipment)
                        .any { it.name == replacement.name }
            }
            .sortedBy { it.key }
            .take(MAX_MOVES)
            .joinToString(";") { (index, replacement) ->
                "$index:${URLEncoder.encode(replacement.name, "UTF-8")}"
            }
            .take(MAX_CHECKPOINT_CHARS)
    }

    /** Invalid, duplicate, stale and unapproved substitutions are ignored on restore. */
    fun decode(
        raw: String?,
        originalMoves: List<TrainingMove>,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        availableEquipment: Set<TrainingEquipment> = emptySet()
    ): Map<Int, TrainingMove> {
        if (raw.isNullOrBlank() || raw.length > MAX_CHECKPOINT_CHARS ||
            originalMoves.isEmpty() || originalMoves.size > MAX_MOVES
        ) return emptyMap()

        val result = linkedMapOf<Int, TrainingMove>()
        raw.split(';').take(MAX_MOVES).forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return@forEach
            val index = entry.substring(0, separator).toIntOrNull() ?: return@forEach
            if (index !in originalMoves.indices || index in result) return@forEach
            val name = runCatching {
                URLDecoder.decode(entry.substring(separator + 1), "UTF-8")
            }.getOrNull() ?: return@forEach
            val replacement = alternativesFor(originalMoves[index], constraints, availableEquipment)
                .firstOrNull { it.name == name } ?: return@forEach
            result[index] = replacement
        }
        return result.toSortedMap()
    }

    fun apply(
        originalMoves: List<TrainingMove>,
        raw: String?,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(),
        availableEquipment: Set<TrainingEquipment> = emptySet()
    ): List<TrainingMove> {
        val result = originalMoves.toMutableList()
        decode(raw, originalMoves, constraints, availableEquipment).forEach { (index, replacement) ->
            result[index] = replacement
        }
        return result
    }
}
