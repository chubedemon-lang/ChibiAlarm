package com.chibialarm.app

/** Offline two-phase exercise cues. Illustrations are not a form assessment. */
object WorkoutPoseMotion {
    fun hasMotion(pose: WorkoutPoseGuide.Pose): Boolean = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.HIP_HINGE,
        WorkoutPoseGuide.Pose.RESISTANCE_ROW,
        WorkoutPoseGuide.Pose.RESISTANCE_HINGE,
        WorkoutPoseGuide.Pose.RESISTANCE_PRESS,
        WorkoutPoseGuide.Pose.PUSH_UP,
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP,
        WorkoutPoseGuide.Pose.WALL_PUSH_UP,
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP,
        WorkoutPoseGuide.Pose.LUNGE,
        WorkoutPoseGuide.Pose.BRIDGE,
        WorkoutPoseGuide.Pose.BIRD_DOG,
        WorkoutPoseGuide.Pose.DEAD_BUG,
        WorkoutPoseGuide.Pose.CAT_COW,
        WorkoutPoseGuide.Pose.MARCH,
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH,
        WorkoutPoseGuide.Pose.HIP_ABDUCTION,
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE,
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS,
        WorkoutPoseGuide.Pose.SIDE_STEP,
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE,
        WorkoutPoseGuide.Pose.CALF_RAISE -> true
        WorkoutPoseGuide.Pose.PLANK,
        WorkoutPoseGuide.Pose.STRETCH,
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH,
        WorkoutPoseGuide.Pose.SIDE_PLANK,
        WorkoutPoseGuide.Pose.BALANCE,
        WorkoutPoseGuide.Pose.REST,
        WorkoutPoseGuide.Pose.STANDING -> false
    }

    /** Screen-reader description for the current illustration, without claiming form assessment. */
    fun accessibilityCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String =
        "${WorkoutPoseGuide.label(pose)}. ${phaseCue(pose, lowered)}. Hình minh họa, không đánh giá tư thế."

    /** One user-requested spoken walkthrough, never automatically repeated with animation. */
    fun spokenWalkthrough(pose: WorkoutPoseGuide.Pose): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> "Minh họa squat. Hạ hông từ từ, sau đó đứng lên. Chỉ xuống sâu trong mức thoải mái."
        WorkoutPoseGuide.Pose.HIP_HINGE -> "Đứng chân rộng bằng hông, gối hơi chùng. Đẩy hông ra sau, giữ lưng trung lập và nghiêng thân nhẹ; siết mông để đứng thẳng. Không cần tạ."
        WorkoutPoseGuide.Pose.RESISTANCE_ROW -> "Giữ thân gập hông nhẹ và lưng trung lập. Kéo khuỷu sát thân, đưa tải về gần hông rồi hạ chậm. Không giật hoặc xoay thân; kiểm tra dây và điểm neo trước khi tập."
        WorkoutPoseGuide.Pose.RESISTANCE_HINGE -> "Đứng chân rộng bằng hông, giữ tải nhẹ sát đùi. Đẩy hông ra sau, gối hơi chùng và lưng trung lập; đưa hông về trước để đứng lên. Không hạ sâu hoặc dùng tải nặng."
        WorkoutPoseGuide.Pose.RESISTANCE_PRESS -> "Đứng hoặc ngồi vững, giữ tạ nhẹ ngang vai. Đẩy lên trong biên độ thoải mái rồi hạ chậm. Không ưỡn lưng, không nín thở; dừng nếu đau vai."
        WorkoutPoseGuide.Pose.PUSH_UP -> "Minh họa chống đẩy sàn. Hạ ngực có kiểm soát, sau đó đẩy lên. Có thể chọn biến thể dễ hơn nếu cần."
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP -> "Minh họa chống đẩy gối. Đặt hai gối trên sàn, giữ thân từ vai tới gối ổn định, hạ ngực vừa tầm rồi đẩy lên."
        WorkoutPoseGuide.Pose.WALL_PUSH_UP -> "Minh họa chống đẩy tường. Đứng cách tường một khoảng thoải mái, đặt tay ngang ngực, gập khuỷu đưa thân gần tường rồi đẩy ra."
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> "Minh họa chống đẩy trên bề mặt cao. Chỉ dùng mặt tựa chắc chắn, không trượt hoặc lật; giữ thân thẳng, hạ ngực rồi đẩy ra. Dừng nếu mặt tựa không vững."
        WorkoutPoseGuide.Pose.LUNGE -> "Minh họa chùng chân. Bước chân trước, hạ gối từ từ rồi đứng lên. Có thể vịn vào điểm tựa chắc chắn."
        WorkoutPoseGuide.Pose.PLANK -> "Minh họa plank. Giữ thân thẳng, thở đều và dừng nếu đau hoặc khó chịu."
        WorkoutPoseGuide.Pose.BRIDGE -> "Minh họa nâng hông. Nằm ngửa, co gối, siết mông nâng hông vừa tầm rồi hạ chậm. Không ưỡn lưng quá mức."
        WorkoutPoseGuide.Pose.BIRD_DOG -> "Minh họa bird-dog. Chống tay và gối, duỗi tay cùng chân đối diện trong tầm kiểm soát rồi đổi bên."
        WorkoutPoseGuide.Pose.DEAD_BUG -> "Minh họa dead bug. Nằm ngửa, giữ thân ổn định, hạ tay và chân đối diện chậm rồi thu về."
        WorkoutPoseGuide.Pose.CAT_COW -> "Minh họa cat-cow. Chống tay và gối, chuyển lưng nhẹ nhàng theo nhịp thở, không ép biên độ."
        WorkoutPoseGuide.Pose.MARCH -> "Minh họa bước tại chỗ. Nâng từng gối luân phiên, bước nhẹ và giữ nhịp thở đều."
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> "Đứng cạnh ghế vững và vịn nhẹ. Nâng từng gối thấp vừa sức, đổi bên và giữ thân thẳng."
        WorkoutPoseGuide.Pose.HIP_ABDUCTION -> "Đứng cạnh ghế vững, giữ nhẹ để thăng bằng. Đưa một chân sang bên trong biên độ nhỏ, hạ chậm rồi đổi bên."
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> "Ngồi hoặc đứng thẳng, thả tay. Kéo nhẹ hai bả vai về sau và xuống, giữ ngắn rồi thả lỏng; không nhún vai hoặc ưỡn lưng."
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> "Đứng đối diện tường, đặt cẳng tay lên tường với khuỷu gập thoải mái. Ấn nhẹ cẳng tay vào tường, giữ cổ tay thả lỏng và thở đều rồi thả. Dừng nếu đau."
        WorkoutPoseGuide.Pose.SIDE_STEP -> "Đứng vững, bước ngang ngắn sang một bên rồi đưa chân kia về gần; đổi hướng. Giữ gối mềm, sàn khô và vịn điểm tựa nếu cần."
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> "Ngồi trên ghế chắc chắn, lưng thẳng. Nâng một gối thấp vừa sức rồi hạ chậm, đổi bên. Dừng nếu đau hoặc chóng mặt."
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH -> "Ngồi ghế vững, duỗi một chân với gót chạm sàn. Nghiêng nhẹ từ hông đến khi căng nhẹ phía sau đùi; đổi bên, không kéo đến đau."
        WorkoutPoseGuide.Pose.STRETCH -> "Minh họa giãn cơ. Thực hiện chậm, không giật nảy và không ép tới mức đau."
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Minh họa plank nghiêng. Chống khuỷu chắc chắn, nâng hông trong mức thoải mái; có thể co gối để dễ hơn."
        WorkoutPoseGuide.Pose.CALF_RAISE -> "Minh họa nhón gót. Vịn điểm tựa chắc chắn nếu cần, nâng gót chậm rồi hạ có kiểm soát."
        WorkoutPoseGuide.Pose.BALANCE -> "Minh họa thăng bằng. Đứng cạnh tường hoặc ghế chắc chắn và giữ điểm tựa trong tầm tay."
        WorkoutPoseGuide.Pose.REST -> "Đang nghỉ. Hít thở bình thường, nghỉ thêm nếu cần."
        WorkoutPoseGuide.Pose.STANDING -> "Chưa có minh họa riêng cho động tác này. Hãy đọc hướng dẫn từng bước bên dưới trước khi tập."
    }

    /**
     * Select a still frame only when the current instruction clearly names one
     * of the two illustrated movement phases. Setup, alignment and compound
     * instructions remain neutral rather than showing a misleading phase.
     */
    fun guidePhaseForStep(pose: WorkoutPoseGuide.Pose, instruction: String): Boolean? {
        val text = instruction.lowercase(java.util.Locale.ROOT)
        val cues: Pair<List<String>, List<String>> = when (pose) {
            WorkoutPoseGuide.Pose.SQUAT -> listOf("hạ hông", "hạ xuống", "ngồi xuống") to listOf("đứng lên")
            WorkoutPoseGuide.Pose.HIP_HINGE,
            WorkoutPoseGuide.Pose.RESISTANCE_HINGE -> listOf("đẩy hông ra sau", "nghiêng thân") to listOf("đứng thẳng", "đưa hông về trước")
            WorkoutPoseGuide.Pose.RESISTANCE_ROW -> listOf("hạ tay", "hạ tải") to listOf("kéo khuỷu", "kéo tay")
            WorkoutPoseGuide.Pose.RESISTANCE_PRESS -> listOf("hạ tải", "hạ về") to listOf("đẩy tải lên", "đẩy lên")
            WorkoutPoseGuide.Pose.PUSH_UP,
            WorkoutPoseGuide.Pose.KNEE_PUSH_UP,
            WorkoutPoseGuide.Pose.WALL_PUSH_UP,
            WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> listOf("hạ ngực", "gập khuỷu", "đưa ngực gần tường") to listOf("đẩy lên", "đẩy ra")
            WorkoutPoseGuide.Pose.LUNGE -> listOf("hạ gối", "hạ thân") to listOf("đứng lên", "trở về")
            WorkoutPoseGuide.Pose.BRIDGE -> listOf("hạ hông", "hạ chậm") to listOf("nâng hông", "siết mông nâng")
            WorkoutPoseGuide.Pose.BIRD_DOG -> listOf("thu tay", "thu chân") to listOf("duỗi tay", "duỗi chân")
            WorkoutPoseGuide.Pose.DEAD_BUG -> listOf("co về", "thu tay", "thu chân") to listOf("duỗi tay", "duỗi chân")
            WorkoutPoseGuide.Pose.CAT_COW -> listOf("cuộn lưng") to listOf("thả lưng")
            WorkoutPoseGuide.Pose.MARCH,
            WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> listOf("hạ chân", "hạ gối", "đưa chân xuống") to listOf("nâng gối", "nhấc gối")
            WorkoutPoseGuide.Pose.HIP_ABDUCTION -> listOf("hạ chân") to listOf("đưa chân sang bên", "nhấc chân")
            WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> listOf("thả lỏng bả vai", "thả bả vai") to listOf("kéo nhẹ hai bả vai", "siết bả vai")
            WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> listOf("thả cẳng tay") to listOf("ấn nhẹ cẳng tay", "ấn cẳng tay")
            WorkoutPoseGuide.Pose.SIDE_STEP -> listOf("đưa chân về", "khép chân") to listOf("bước ngang", "bước sang")
            WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> listOf("hạ chân") to listOf("nâng gối")
            WorkoutPoseGuide.Pose.CALF_RAISE -> listOf("hạ gót") to listOf("nhón gót", "nâng gót")
            else -> return null
        }
        val alternate = cues.first.any { it in text }
        val primary = cues.second.any { it in text }
        return when {
            alternate && !primary -> true
            primary && !alternate -> false
            else -> null
        }
    }

    /** Spoken step guidance that does not invent a movement phase for setup or hold cues. */
    fun spokenInstructionStepCue(
        pose: WorkoutPoseGuide.Pose,
        step: String,
        position: Int,
        total: Int
    ): String {
        val safeTotal = total.coerceIn(1, 100)
        val safePosition = position.coerceIn(1, safeTotal)
        val safeStep = step.trim().replace(Regex("\\s+"), " ")
            .take(180)
            .ifBlank { "Thực hiện chậm trong biên độ thoải mái." }
        val phase = guidePhaseForStep(pose, safeStep)
        val movement = phase?.let { phaseCue(pose, it) } ?: "Tư thế minh họa cho bước này"
        val breathing = phase?.let { breathingCue(pose, it) } ?: "Thở đều, không nín thở."
        return "${WorkoutPoseGuide.label(pose)}. $movement. $breathing Bước $safePosition trên $safeTotal: $safeStep"
    }

    /**
     * Conservative breathing prompts for a spoken step. The alternate illustration
     * phase is the lowering/return phase for these resistance patterns; other movements
     * get a neutral reminder rather than an invented inhale/exhale mapping.
     */
    fun breathingCue(pose: WorkoutPoseGuide.Pose, returnPhase: Boolean): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.HIP_HINGE,
        WorkoutPoseGuide.Pose.RESISTANCE_ROW,
        WorkoutPoseGuide.Pose.RESISTANCE_HINGE,
        WorkoutPoseGuide.Pose.RESISTANCE_PRESS,
        WorkoutPoseGuide.Pose.PUSH_UP,
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP,
        WorkoutPoseGuide.Pose.WALL_PUSH_UP,
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP,
        WorkoutPoseGuide.Pose.LUNGE,
        WorkoutPoseGuide.Pose.BRIDGE,
        WorkoutPoseGuide.Pose.CALF_RAISE ->
            if (returnPhase) "Hít vào khi hạ hoặc trở về." else "Thở ra khi dùng lực."
        else -> "Thở đều, không nín thở."
    }

    /** Spoken context for the selected walkthrough step; bounded for short on-device TTS. */
    fun spokenStepCue(
        pose: WorkoutPoseGuide.Pose,
        step: String,
        position: Int,
        total: Int,
        alternatePhase: Boolean = false
    ): String {
        val safeTotal = total.coerceIn(1, 100)
        val safePosition = position.coerceIn(1, safeTotal)
        val safeStep = step.trim().replace(Regex("\\s+"), " ")
            .take(180)
            .ifBlank { "Thực hiện chậm trong biên độ thoải mái." }
        return "${WorkoutPoseGuide.label(pose)}. ${phaseCue(pose, alternatePhase)}. ${breathingCue(pose, alternatePhase)} " +
            "Bước $safePosition trên $safeTotal: $safeStep"
    }

    /** Short, explicit phase cues to accompany the two-frame drawing. */
    fun phaseCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> if (lowered) "Hạ hông" else "Đứng lên"
        WorkoutPoseGuide.Pose.HIP_HINGE -> if (lowered) "Đẩy hông ra sau" else "Đứng thẳng"
        WorkoutPoseGuide.Pose.RESISTANCE_ROW -> if (lowered) "Hạ tay có kiểm soát" else "Kéo khuỷu về sau"
        WorkoutPoseGuide.Pose.RESISTANCE_HINGE -> if (lowered) "Đẩy hông ra sau" else "Đứng thẳng, đưa hông về trước"
        WorkoutPoseGuide.Pose.RESISTANCE_PRESS -> if (lowered) "Hạ tải ngang vai" else "Đẩy tải lên"
        WorkoutPoseGuide.Pose.PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên"
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên từ điểm tựa gối"
        WorkoutPoseGuide.Pose.WALL_PUSH_UP -> if (lowered) "Gập khuỷu về tường" else "Đẩy ra khỏi tường"
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> if (lowered) "Hạ ngực về mặt tựa" else "Đẩy ra khỏi mặt tựa"
        WorkoutPoseGuide.Pose.LUNGE -> if (lowered) "Hạ gối" else "Đứng lên"
        WorkoutPoseGuide.Pose.BRIDGE -> if (lowered) "Hạ hông" else "Nâng hông"
        WorkoutPoseGuide.Pose.BIRD_DOG -> if (lowered) "Thu tay chân" else "Duỗi tay chân đối diện"
        WorkoutPoseGuide.Pose.DEAD_BUG -> if (lowered) "Co về" else "Duỗi tay chân"
        WorkoutPoseGuide.Pose.CAT_COW -> if (lowered) "Cuộn lưng nhẹ" else "Thả lưng nhẹ"
        WorkoutPoseGuide.Pose.MARCH -> if (lowered) "Đổi chân" else "Nâng gối"
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> if (lowered) "Đổi chân có điểm tựa" else "Nâng gối thấp"
        WorkoutPoseGuide.Pose.HIP_ABDUCTION -> if (lowered) "Hạ chân" else "Đưa chân sang bên"
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> if (lowered) "Thả lỏng bả vai" else "Kéo nhẹ bả vai"
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> if (lowered) "Thả cẳng tay" else "Ấn nhẹ cẳng tay"
        WorkoutPoseGuide.Pose.SIDE_STEP -> if (lowered) "Đưa chân về" else "Bước ngang"
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> if (lowered) "Hạ chân" else "Nâng gối"
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH -> "Giãn nhẹ phía sau đùi"
        WorkoutPoseGuide.Pose.CALF_RAISE -> if (lowered) "Hạ gót" else "Nhón gót"
        WorkoutPoseGuide.Pose.PLANK -> "Giữ thân thẳng"
        WorkoutPoseGuide.Pose.STRETCH -> "Giãn nhẹ"
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Giữ hông nâng"
        WorkoutPoseGuide.Pose.BALANCE -> "Giữ thăng bằng"
        WorkoutPoseGuide.Pose.REST -> "Nghỉ và thở đều"
        WorkoutPoseGuide.Pose.STANDING -> "Xem hướng dẫn"
    }
}
