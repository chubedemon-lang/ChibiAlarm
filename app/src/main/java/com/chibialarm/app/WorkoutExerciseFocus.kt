package com.chibialarm.app

/**
 * Curated, deliberately broad exercise-focus notes for the offline coach.
 *
 * These describe common movement emphasis, not isolated-muscle guarantees,
 * rehabilitation advice, or an assessment of the user's technique.
 */
object WorkoutExerciseFocus {
    private val descriptions = mapOf(
        "Squat không tạ" to "Nhóm cơ chính: đùi trước và cơ mông; cơ trung tâm hỗ trợ giữ thân ổn định.",
        "Squat chậm 3 giây" to "Nhóm cơ chính: đùi trước và cơ mông; cơ trung tâm hỗ trợ giữ thân ổn định.",
        "Đứng lên – ngồi xuống" to "Nhóm cơ chính: đùi trước và cơ mông; ghế là điểm tựa giúp kiểm soát chuyển động.",
        "Bước lùi – lunge" to "Nhóm cơ chính: đùi và cơ mông; cơ quanh hông hỗ trợ giữ thăng bằng.",
        "Nâng hông trên sàn" to "Nhóm cơ chính: cơ mông và mặt sau đùi; cơ trung tâm hỗ trợ ổn định thân.",
        "Chống đẩy tường" to "Nhóm cơ thường tham gia: ngực, vai trước và cơ tay sau.",
        "Chống đẩy gối" to "Nhóm cơ thường tham gia: ngực, vai trước, cơ tay sau và cơ trung tâm.",
        "Chống đẩy trên bề mặt cao" to "Nhóm cơ thường tham gia: ngực, vai trước, cơ tay sau và cơ trung tâm.",
        "Chống đẩy sàn" to "Nhóm cơ thường tham gia: ngực, vai trước, cơ tay sau và cơ trung tâm.",
        "Ép ngực tường bằng cẳng tay" to "Trọng tâm: cơ ngực và vùng vai tham gia giữ lực tĩnh; không ép nếu khớp khó chịu.",
        "Bird-dog" to "Trọng tâm: cơ trung tâm, cơ lưng và cơ mông phối hợp giữ thân ổn định.",
        "Plank khuỷu hoặc plank gối" to "Trọng tâm: cơ trung tâm và cơ bụng; vai tham gia giữ tư thế.",
        "Side plank gối" to "Trọng tâm: cơ bụng bên và các cơ trung tâm giúp giữ hông ổn định.",
        "Dead bug cơ bụng" to "Trọng tâm: cơ trung tâm phối hợp kiểm soát thân và chuyển động tay chân.",
        "Nhón gót" to "Nhóm cơ chính: cơ bắp chân.",
        "Kéo tạ đơn hoặc dây kháng lực" to "Nhóm cơ thường tham gia: lưng trên, cơ lưng rộng và cơ tay trước.",
        "Gập hông với tạ đơn hoặc dây" to "Nhóm cơ thường tham gia: cơ mông và mặt sau đùi; cơ thân giữ lưng ổn định.",
        "Gập hông không dụng cụ" to "Nhóm cơ thường tham gia: cơ mông và mặt sau đùi; ưu tiên học chuyển động không tải.",
        "Đẩy vai với tạ đơn hoặc dây" to "Nhóm cơ thường tham gia: cơ vai và cơ tay sau; chỉ dùng tải phù hợp.",
        "Siết bả vai đứng" to "Trọng tâm: các cơ vùng lưng trên hỗ trợ kiểm soát bả vai.",
        "Dang chân sang bên có điểm tựa" to "Nhóm cơ thường tham gia: cơ mông bên và các cơ ổn định vùng hông.",
        "Nâng gối luân phiên có điểm tựa" to "Trọng tâm: cơ gấp hông và cơ chân; đây cũng là vận động tim mạch nhẹ.",
        "Nâng gối ngồi ghế" to "Trọng tâm: cơ gấp hông và cơ đùi trước trong biên độ thoải mái.",
        "Đi bộ nhanh tại chỗ" to "Trọng tâm: cơ chân tham gia bước đi và hệ tim mạch; giữ nhịp đủ để vẫn nói chuyện được.",
        "Bước ngang nhẹ" to "Trọng tâm: cơ đùi và cơ mông bên hỗ trợ bước ngang và giữ thăng bằng.",
        "Đứng thăng bằng có hỗ trợ" to "Trọng tâm: khả năng phối hợp và các cơ ổn định quanh cổ chân, hông; luôn dùng điểm tựa vững.",
        "Giãn gân kheo ngồi ghế" to "Trọng tâm: vận động linh hoạt mặt sau đùi; cảm giác chỉ nên căng nhẹ, không đau.",
        "Mở vai và ngực" to "Trọng tâm: vận động linh hoạt vùng vai và ngực; không phải bài cô lập để tăng sức mạnh.",
        "Cat-cow nhẹ" to "Trọng tâm: vận động nhẹ cột sống kết hợp nhịp thở; không phải bài cô lập một nhóm cơ."
    )

    /** Returns only a catalogued note; unknown moves deliberately return null. */
    fun descriptionFor(moveName: String): String? =
        descriptions[moveName.trim().take(90)]
}
