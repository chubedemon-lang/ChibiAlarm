package com.chibialarm.app

import java.util.Locale

/** Free on-device rule-based assistant. Does not pretend to be a generative model. */
object FreeCoach {
    fun wake(topic:String):String {
        val s=topic.lowercase(Locale.ROOT)
        return when {
            listOf("hài","vui","cười","báo").any { it in s } -> "Dậy đi bạn ơi! Mặt trời đi làm từ lâu rồi, còn bạn vẫn đang đàm phán với chiếc gối đấy. Chibi đếm đến ba nhé!"
            listOf("học","thi","bài","sách").any { it in s } -> "Chào buổi sáng! Hôm nay mình tiến thêm một bước nhỏ tới mục tiêu học tập nhé. Dậy, uống nước và bắt đầu thôi!"
            listOf("tập","gym","chạy","thể dục").any { it in s } -> "Dậy nào! Vươn vai, uống chút nước và khởi động nhẹ nhàng. Mỗi ngày một chút, bạn sẽ khỏe hơn!"
            listOf("nhẹ","dịu","êm","ngủ").any { it in s } -> "Chào buổi sáng, bạn đáng yêu. Hít thở sâu một chút, mở mắt và bắt đầu ngày mới thật bình yên nhé."
            listOf("động lực","cố gắng","thành công").any { it in s } -> "Mỗi ngày là một cơ hội để bạn tốt hơn hôm qua. Đừng chờ cảm hứng, hãy bắt đầu bằng một việc nhỏ ngay bây giờ!"
            else -> "Dậy thôi bạn ơi! Chibi đã chuẩn bị một ngày mới đầy năng lượng. Mình cùng thức dậy, uống nước và làm điều tuyệt vời đầu tiên nhé!"
        }
    }
    fun chat(question:String):String {
        val s=question.lowercase(Locale.ROOT)
        return when {
            listOf("ngủ","dậy","buổi sáng").any { it in s } -> "🐰 Mẹo nhỏ: Hãy đặt giờ thức dậy cố định, để điện thoại xa giường và chuẩn bị việc đầu tiên từ tối hôm trước. Bạn có thể vào Lịch để thêm giờ ngủ và thức dậy."
            listOf("học","tập trung","ôn thi").any { it in s } -> "📚 Chibi gợi ý: Chọn một việc quan trọng, tập trung 25 phút trong Chibi Focus rồi nghỉ 5 phút. Sau 3–4 phiên, nghỉ dài hơn. Bắt đầu với mục tiêu vừa sức nhé!"
            listOf("thể dục","chạy","gym","vận động").any { it in s } -> "🏃 Bắt đầu với 10–20 phút vận động vừa sức. Khởi động nhẹ, uống đủ nước và ghi lại buổi tập trong Chibi Workout. Nếu có vấn đề sức khỏe, hãy hỏi chuyên gia."
            listOf("mục tiêu","30 ngày","90 ngày","thói quen").any { it in s } -> "🎯 Hãy chọn một mục tiêu cụ thể, chia thành việc nhỏ mỗi ngày, đặt nhắc việc và check-in. Vào Thử thách 30/90 ngày để bắt đầu. Duy trì đều đặn quan trọng hơn làm quá nhiều."
            listOf("stress","buồn","lo","mệt").any { it in s } -> "🌷 Bạn có thể thử nghỉ vài phút, uống nước, hít thở chậm và giảm bớt một nhiệm vụ hôm nay. Nếu cảm giác khó chịu kéo dài, hãy tìm người đáng tin cậy hoặc chuyên gia để trao đổi."
            else -> "🐰 Chibi Offline đây! Mình có thể gợi ý cách học tập, luyện tập, ngủ nghỉ và tạo thói quen miễn phí. Hãy thử hỏi: 'Làm sao tập trung học?', 'Lập thói quen 30 ngày' hoặc 'Cách ngủ đúng giờ'."
        }
    }
    fun plan(detail:String,span:Int):Pair<String,List<Routine>> {
        val s=detail.lowercase(Locale.ROOT)
        val items=mutableListOf<Routine>()
        var id=LifeData.nextId()
        fun add(title:String,cat:String,h:Int,m:Int,d:Int){items.add(Routine(id++,title,cat,h,m,d))}
        add("Uống nước và chuẩn bị ngày mới","💧 Sinh hoạt",7,0,10)
        if(listOf("chạy","gym","tập","thể dục","giảm cân").any { it in s }) add("Vận động vừa sức","🏃 Sức khỏe",7,20,25)
        if(listOf("học","tiếng anh","thi","đọc","kỹ năng").any { it in s }) add("Học kỹ năng ưu tiên","📚 Học tập",19,0,35)
        else add("Tập trung cho mục tiêu chính","📚 Học tập",19,0,30)
        add("Đọc sách hoặc thư giãn","🧘 Tinh thần",21,0,20)
        add("Chuẩn bị đi ngủ","💤 Nghỉ ngơi",22,15,15)
        return "Kế hoạch $span ngày (mẫu ngoại tuyến)" to items
    }
}
