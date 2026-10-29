package com.chibialarm.app

/**
 * Lightweight, fully offline wellness exercise library.
 * Informational guidance for generally healthy adults, not medical rehabilitation.
 * Exercise patterns informed by WHO/CDC/NHS; exact prescriptions are illustrative.
 */
data class TrainingMove(
    val name:String,
    val dose:String,
    val steps:String,
    val easier:String,
    val avoid:String,
    val seconds:Int=60
)
data class TrainingProgram(
    val id:String,
    val name:String,
    val subtitle:String,
    val category:String,
    val moves:List<TrainingMove>,
    val caution:String="Thở đều, tập trong phạm vi dễ chịu. Dừng khi đau, chóng mặt, đau ngực hoặc khó thở bất thường."
)
object TrainingCatalog {
    private fun exercise(n:String,d:String,s:String,e:String,a:String="Nếu khó chịu hoặc mất kiểm soát kỹ thuật, dừng hoặc giảm mức độ.",secs:Int=60)=
        TrainingMove(n,d,s,e,a,secs)
    private val warm=exercise("Khởi động tại chỗ","3–5 phút",
        "Đi bộ tại chỗ, xoay vai và cổ chân chậm, nâng gối nhẹ. Tăng tốc từng chút.",
        "Bước chậm, có thể vịn ghế chắc chắn.","Không xoay giật khớp.",120)
    private val cool=exercise("Thả lỏng","3–5 phút",
        "Đi bộ chậm rồi kéo giãn nhẹ nhóm cơ vừa tập; hít thở bình thường.",
        "Ngồi nghỉ và hít thở chậm nếu mệt.","Không ép cơ đến mức đau.",120)
    private val chairSquat=exercise("Đứng lên – ngồi xuống","2 hiệp × 6–10 lần",
        "Dùng ghế vững chắc không bánh xe. Ngồi gần mép ghế, bàn chân rộng bằng hông, đứng lên chậm rồi ngồi xuống có kiểm soát.",
        "Dùng tay vịn hỗ trợ hoặc hạ ít lần.","Đảm bảo ghế không trượt.",90)
    private val wallPush=exercise("Chống đẩy tường","2 hiệp × 5–12 lần",
        "Đặt hai tay ngang ngực lên tường, giữ thân thẳng, gập khuỷu đưa ngực gần tường rồi đẩy ra.",
        "Đứng gần tường hơn để dễ hơn.","Không võng lưng, không ép cổ tay.",90)
    private val squat=exercise("Squat không tạ","2–3 hiệp × 8–12 lần",
        "Chân rộng bằng vai, đẩy hông nhẹ về sau, hạ xuống theo khả năng rồi đứng lên, gối cùng hướng bàn chân.",
        "Squat nông hoặc squat ghế.","Không nhún mạnh ở cuối động tác.",90)
    private val bridge=exercise("Nâng hông trên sàn","2 hiệp × 8–12 lần",
        "Nằm ngửa, co gối, đặt bàn chân trên sàn. Siết mông nâng hông, giữ ngắn rồi hạ chậm.",
        "Nâng hông với biên độ thấp.","Không ưỡn thắt lưng quá mức.",90)
    private val bird=exercise("Bird-dog","2 hiệp × 6 lần mỗi bên",
        "Chống tay và gối, duỗi tay đối diện chân, giữ lưng trung lập rồi đổi bên.",
        "Chỉ duỗi một tay hoặc một chân.","Giữ thân ổn định.",90)
    private val plank=exercise("Plank khuỷu hoặc plank gối","2 hiệp × 15–30 giây",
        "Giữ thân thành đường thẳng, siết bụng nhẹ, tiếp tục hít thở.",
        "Chống hai gối xuống sàn, rút ngắn thời gian.","Dừng nếu đau lưng hoặc vai.",60)
    private val march=exercise("Đi bộ nhanh tại chỗ","3–6 phút",
        "Bước đều, đánh tay tự nhiên, tăng nhịp vừa sức; vẫn có thể trò chuyện.",
        "Đi bộ chậm hoặc bước tại chỗ.","Không cần chạy nhảy nếu không thoải mái.",150)
    private val sideStep=exercise("Bước ngang nhẹ","2 phút",
        "Bước sang phải, đưa chân trái về cạnh chân phải; đổi hướng, giữ gối hơi mềm.",
        "Bước ngắn, bám tay vịn nếu cần.","Giữ sàn khô, không trơn.",90)
    private val reverseLunge=exercise("Bước lùi – lunge","2 hiệp × 6–10 lần mỗi chân",
        "Bước một chân ra sau, hạ thân nhẹ rồi trở về; chân trước vững vàng.",
        "Vịn tường hoặc chỉ bước lùi không hạ sâu.","Không tập khi cảm thấy khớp gối đau.",90)
    private val calf=exercise("Nhón gót","2 hiệp × 10–15 lần",
        "Đứng thẳng, vịn tường nếu cần, nâng gót chậm rồi hạ xuống.",
        "Dùng hai tay vịn ghế để giữ thăng bằng.","Tránh nhún bật mạnh.",70)
    private val kneePush=exercise("Chống đẩy gối","2 hiệp × 5–10 lần",
        "Tay dưới vai, gối chạm sàn, giữ thân từ vai tới gối thẳng, hạ ngực rồi đẩy lên.",
        "Thay bằng chống đẩy tường.","Dừng nếu đau cổ tay hoặc vai.",100)
    private val deadBug=exercise("Dead bug cơ bụng","2 hiệp × 6–8 lần mỗi bên",
        "Nằm ngửa, tay hướng lên, gối gập trên hông. Hạ chậm tay và chân đối diện trong phạm vi giữ lưng thoải mái.",
        "Chỉ hạ một gót xuống sàn.","Không cố ép lưng nếu đau.",90)
    private val catCow=exercise("Cat-cow nhẹ","6–8 nhịp",
        "Chống tay và gối, hít vào chuyển lưng sang tư thế thoải mái, thở ra cuộn lưng nhẹ, không ép biên độ.",
        "Thực hiện với chuyển động nhỏ.","Nếu đau cổ tay, thử đặt cẳng tay lên bề mặt chắc chắn.",70)
    private val shoulder=exercise("Mở vai và ngực","6–10 nhịp",
        "Đứng thẳng, xoay vai nhẹ từ trước ra sau, khép bả vai, thả lỏng cổ.",
        "Chỉ xoay vai nhỏ.","Không ngửa cổ quá mức.",60)
    private val stretch=exercise("Giãn cơ và thở chậm","4–6 phút",
        "Giãn vai, hông, bắp chân từng bên nhẹ nhàng, không nín thở.",
        "Ngồi ghế để giảm tải.","Không giật nảy, không chịu đau.",180)
    private val inclinePush=exercise("Chống đẩy trên bề mặt cao","2 hiệp × 8–12 lần",
        "Chống tay lên bề mặt cố định vững chắc, thân thẳng, hạ ngực rồi đẩy lên.",
        "Chuyển sang chống đẩy tường.","Không dùng bàn ghế dễ lật hoặc trượt.",90)
    private val mountain=exercise("Mountain climber chậm","2 × 20 giây",
        "Ở tư thế chống tay, đưa từng gối về phía ngực theo nhịp chậm, giữ hông ổn định.",
        "Đưa gối chậm, hoặc đổi sang nâng gối tại chỗ.","Dừng nếu đau cổ tay, vai.",55)
    private val sidePlank=exercise("Side plank gối","2 × 15–25 giây mỗi bên",
        "Nằm nghiêng, chống khuỷu, co gối, nâng hông trong phạm vi có kiểm soát.",
        "Giảm thời gian và giữ hông thấp hơn.","Không ép vai chịu đau.",80)
    private val heelRaise=exercise("Nâng gối ngồi ghế","2 × 8 lần mỗi chân",
        "Ngồi thẳng trên ghế vững, nâng một gối vừa sức rồi hạ và đổi chân.",
        "Nâng bàn chân thấp hơn.","Không rướn cổ.",70)
    private val balance=exercise("Đứng thăng bằng có hỗ trợ","3 × 15 giây mỗi bên",
        "Đứng cạnh tường hoặc ghế vững, nhấc nhẹ một gót, giữ thăng bằng có hỗ trợ.",
        "Giữ cả hai chân trên sàn và chuyển trọng tâm.","Không thử nếu chóng mặt; luôn có điểm tựa.",75)
    private val tempoSquat=exercise("Squat chậm 3 giây","3 hiệp × 8–12 lần",
        "Đếm ba giây khi hạ xuống, giữ kỹ thuật rồi đứng lên bình thường.",
        "Giảm độ sâu hoặc dùng ghế.","Dừng khi gối đau, không quá sức.",95)
    private val push=exercise("Chống đẩy sàn","3 hiệp × 6–12 lần",
        "Tay dưới vai, thân thẳng, hạ có kiểm soát, đẩy lên đều.",
        "Chống đẩy gối hoặc tường.","Dừng nếu đau cổ tay hoặc vai.",95)
    val programs=listOf(
        TrainingProgram("starter","🌱 Bắt đầu nhẹ nhàng","Mới tập hoặc quay lại sau thời gian nghỉ","Toàn thân",listOf(warm,chairSquat,wallPush,bridge,stretch)),
        TrainingProgram("full","💪 Toàn thân không dụng cụ","Chân, thân trên và cơ trung tâm","Sức mạnh",listOf(warm,squat,wallPush,bridge,bird,plank,cool)),
        TrainingProgram("lowcardio","🚶 Cardio không bật nhảy","Tăng vận động, hạn chế tác động khớp","Cardio",listOf(warm,march,sideStep,calf,march,cool)),
        TrainingProgram("legs","🦵 Chân và mông","Hông, đùi, bắp chân","Sức mạnh",listOf(warm,squat,reverseLunge,bridge,calf,cool)),
        TrainingProgram("upper","🏋️ Thân trên","Vai, ngực, tay và lưng trên","Sức mạnh",listOf(warm,wallPush,inclinePush,shoulder,bird,cool)),
        TrainingProgram("core","🔥 Core ổn định","Cơ bụng và kiểm soát thân người, không hứa giảm mỡ vùng bụng","Sức mạnh",listOf(warm,bird,deadBug,plank,sidePlank,cool)),
        TrainingProgram("mobility","🧘 Linh hoạt & giãn cơ","Chuyển động nhẹ sau ngày dài","Linh hoạt",listOf(warm,catCow,shoulder,stretch,cool)),
        TrainingProgram("desk","💻 Nghỉ vận động tại bàn","Buổi vận động ngắn giữa giờ học/làm","Vận động nhẹ",listOf(shoulder,march,calf,stretch)),
        TrainingProgram("balance","🌿 Thăng bằng cơ bản","Tập nhẹ có điểm tựa, không phải phục hồi chức năng","Vận động nhẹ",listOf(warm,heelRaise,balance,chairSquat,cool)),
        TrainingProgram("quick","⚡ 10 phút bận rộn","Buổi ngắn để bắt đầu thói quen","Vận động nhẹ",listOf(warm,march,wallPush,stretch)),
        TrainingProgram("advanced","🚀 Thử thách nâng cao","Chỉ chọn khi đã quen kỹ thuật và hồi phục tốt","Thử thách",listOf(warm,tempoSquat,push,reverseLunge,mountain,sidePlank,cool)),
        TrainingProgram("recovery","🌙 Ngày phục hồi tích cực","Nhẹ nhàng, không thay thế điều trị chấn thương","Phục hồi nhẹ",listOf(march,shoulder,catCow,stretch))
    )
    fun byId(id:String)=programs.firstOrNull { it.id==id } ?: programs.first()
    fun adjusted(program:TrainingProgram,level:Int,duration:Int):List<TrainingMove> {
        // Keep warm up/cool down; add repeats conservatively rather than intensify blindly.
        val base=program.moves.toMutableList()
        if(duration>=35 && level>=1 && program.category in listOf("Sức mạnh","Cardio","Thử thách")) {
            base.add(base.size-1,base.getOrElse(2){base.last()})
        }
        return base
    }
}
