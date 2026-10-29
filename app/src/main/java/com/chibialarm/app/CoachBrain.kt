package com.chibialarm.app
import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Offline coaching rules using consented local goal/check-in data. Not a generative LLM. */
object CoachBrain {
    fun reply(context:Context,question:String):String {
        val q=question.lowercase(Locale.ROOT)
        val p=context.getSharedPreferences("transformation_v1",0)
        val goal=p.getString("goal","").orEmpty().take(140)
        val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
        val check=try{JSONObject(p.getString("daily_"+day,"{}") ?: "{}")}catch(_:Exception){JSONObject()}
        val tired=check.optInt("energy",2)<=1 || listOf("mệt","kiệt sức","uể oải").any{it in q}
        val memory=if(goal.isBlank()) "Bạn có thể đặt mục tiêu trong Hành trình thay đổi." else "Mục tiêu bạn đã lưu: "+goal+"."
        return when {
            listOf("đau ngực","ngất","khó thở dữ dội").any{it in q} ->
                "Hãy dừng tập và tìm trợ giúp y tế khẩn cấp nếu có triệu chứng nghiêm trọng. Mình không thể chẩn đoán qua trò chuyện."
            listOf("tập","thể dục","gym","chống đẩy","squat","vận động").any{it in q} ->
                if(tired) "Hôm nay bạn có vẻ mệt. Ta chọn đi bộ hoặc giãn cơ nhẹ 5–10 phút nếu thấy thoải mái, hoặc nghỉ. Bạn muốn phục hồi hay vận động nhẹ? "+memory
                else "Thử 10–20 phút toàn thân không dụng cụ: khởi động, đứng lên ngồi xuống, chống đẩy tường, nâng hông, thả lỏng. Mở Chibi Fitness để tập từng bước. Bạn mới tập hay đã quen? "+memory
            listOf("ăn","bữa","dinh dưỡng","calo","protein","uống nước","thực đơn").any{it in q} ->
                "Mình giúp bạn xây bữa ăn đều đặn gồm rau quả, đạm và tinh bột phù hợp. Bạn muốn tăng sức mạnh, duy trì sức khỏe hay thay đổi cân nặng? Có món nào bạn dị ứng hoặc không ăn?"
            listOf("ngủ","thức khuya","dậy sớm").any{it in q} ->
                "Ta thử giữ giờ thức dậy ổn định, giảm màn hình trước giờ ngủ và chuẩn bị việc sáng mai từ tối. Bạn thường ngủ và thức dậy lúc mấy giờ?"
            listOf("buồn","chán","áp lực","stress","căng thẳng","nản","bỏ cuộc").any{it in q} ->
                "Mình đang lắng nghe. Không cần hoàn hảo mỗi ngày. Hôm nay bạn có thể chọn một việc nhỏ trong 5 phút, hoặc nghỉ. Điều gì đang khiến bạn khó chịu nhất? "+memory
            listOf("mục tiêu","thay đổi","90 ngày","kỷ luật","thói quen").any{it in q} ->
                memory+" Mỗi ngày ta có thể chọn 10 phút vận động, một phiên tập trung 25 phút và check-in tối. Bạn muốn ưu tiên sức khỏe, học tập hay giấc ngủ?"
            listOf("học","tập trung","trì hoãn","làm việc").any{it in q} ->
                "Chọn một việc quan trọng, bắt đầu 10 phút trước, rồi làm thêm một phiên 25 phút nếu còn sức. Việc nào bạn muốn hoàn thành hôm nay?"
            listOf("chào","hello","bạn là ai").any{it in q} ->
                "Chào bạn! Mình là Chibi Coach, trợ lý ngoại tuyến hỗ trợ thói quen và luyện tập. "+memory+" Hôm nay bạn muốn cải thiện điều gì?"
            else ->
                "Mình nghe bạn nói: "+question.take(100)+". "+memory+" Mình hỗ trợ luyện tập, dinh dưỡng, giấc ngủ và kỷ luật. Bạn muốn bắt đầu bằng một hành động nhỏ nào hôm nay? (Chế độ này dùng quy tắc ngoại tuyến, chưa phải AI sinh ngôn ngữ.)"
        }
    }
}
