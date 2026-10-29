package com.chibialarm.app

import android.app.*
import android.webkit.URLUtil
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.view.WindowInsets
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

/** Entirely offline guided workout; spoken instructions depend on installed Vietnamese Android TTS. */
class GuidedWorkoutActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var counter:TextView
    private lateinit var heading:TextView
    private lateinit var guide:TextView
    private lateinit var progress:ProgressBar
    private lateinit var control:Button
    private lateinit var program:TrainingProgram
    private var moves:List<TrainingMove> = emptyList()
    private var index=0
    private var deadline=0L
    private var remainingMs=0L
    private var paused=true
    private var startedAt=0L
    private var accumulated=0L
    private var spoken:TextToSpeech?=null
    private var ready=false
    private val videoPrefs by lazy { getSharedPreferences("workout_videos",0) }
    private val pickVideoRequest=889
    private val tick=object:Runnable {
        override fun run() {
            if(paused)return
            val left=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs=left
            val seconds=(left/1000).toInt()
            counter.text=String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
            if(left==0L) nextMove() else handler.postDelayed(this,300)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        program=TrainingCatalog.byId(intent.getStringExtra("program") ?: "starter")
        val level=intent.getIntExtra("level",0).coerceIn(0,2)
        val minutes=intent.getIntExtra("minutes",15).coerceIn(5,45)
        val base=TrainingCatalog.adjusted(program,level,minutes)
        val middle=base.drop(1).dropLast(1)
        val estimate=base.sumOf { it.seconds }
        val rounds=if(middle.isEmpty()) 1 else
            ((minutes*60-estimate).coerceAtLeast(0)/middle.sumOf { it.seconds }+1).coerceIn(1,4)
        moves=if(middle.isEmpty())base else listOf(base.first())+
            (0 until rounds).flatMap { middle }+listOf(base.last())
        val root=LinearLayout(this).apply {
            orientation=1;setPadding(24,27,24,24);setBackgroundColor(Color.rgb(247,240,252))
        }
        root.addView(TextView(this).apply {
            text="🐰 Chibi Coach • Tập có hướng dẫn";textSize=22f
            setTextColor(Color.rgb(95,55,113))
        })
        root.addView(TextView(this).apply { text=program.name;textSize=16f })
        heading=TextView(this).apply {
            textSize=23f;gravity=Gravity.CENTER;setPadding(5,20,5,18);setTextColor(Color.rgb(75,55,95))
        }
        root.addView(heading)
        counter=TextView(this).apply {
            textSize=54f;gravity=Gravity.CENTER;setTextColor(Color.rgb(100,70,140))
        }
        root.addView(counter)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=moves.size;progress=0
        }
        root.addView(progress)
        guide=TextView(this).apply { textSize=16f;setPadding(10,22,10,20);setTextColor(Color.rgb(70,61,85)) }
        root.addView(ScrollView(this).apply { addView(guide) },LinearLayout.LayoutParams(-1,0,1f))
        val online=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        online.addView(Button(this).apply { text="▶ YouTube";setOnClickListener { searchVideo("youtube") } },LinearLayout.LayoutParams(0,-2,1f))
        online.addView(Button(this).apply { text="♫ TikTok";setOnClickListener { searchVideo("tiktok") } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(online)
        val library=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        library.addView(Button(this).apply { text="⭐ Lưu link";setOnClickListener { saveVideoLink() } },LinearLayout.LayoutParams(0,-2,1f))
        library.addView(Button(this).apply { text="📥 Lưu video";setOnClickListener { downloadVideo() } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(library)
        root.addView(Button(this).apply {
            text="🧠 Kiểm tra độ phù hợp của video"
            setOnClickListener { assessVideo() }
        })
        val offline=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        offline.addView(Button(this).apply { text="📂 Chọn từ máy";setOnClickListener { chooseLocalVideo() } },LinearLayout.LayoutParams(0,-2,1f))
        offline.addView(Button(this).apply { text="▶ Video đã lưu";setOnClickListener { openSavedVideo() } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(offline)
        root.addView(TextView(this).apply {
            text="🌐 Video được tìm theo động tác đang chọn. Mở nguồn chính thức, cần kết nối mạng.";textSize=12f
        })
        control=Button(this).apply { text="▶ Bắt đầu";setOnClickListener { if(paused)resume() else pause() } }
        root.addView(control)
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text="← Trước";setOnClickListener {
            if(index>0) { pause();index--;displayStep(); }
        } },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply { text="Bước tiếp →";setOnClickListener { nextMove() } },
            LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        root.addView(Button(this).apply { text="⏹ Kết thúc buổi tập";setOnClickListener {
            AlertDialog.Builder(this@GuidedWorkoutActivity).setTitle("Kết thúc buổi tập?")
                .setMessage("Bạn có thể ghi buổi tập vào nhật ký nếu đã hoàn thành phù hợp với sức mình.")
                .setPositiveButton("Lưu & kết thúc"){_,_->finishWorkout()}
                .setNegativeButton("Tiếp tục",null).show()
        } })
        root.addView(TextView(this).apply {
            text="Giọng đọc dùng engine TTS cài trên Android. Thời gian từng động tác chỉ là nhắc nhở; nghỉ thêm khi cần. Dừng ngay nếu thấy đau hoặc khó chịu."
            textSize=12f
        })
        setContentView(root)
        if(Build.VERSION.SDK_INT>=35) {
            root.setOnApplyWindowInsetsListener { view,insets ->
                val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(24+bars.left,27+bars.top,24+bars.right,24+bars.bottom)
                insets
            }
            root.requestApplyInsets()
        }
        spoken=TextToSpeech(this){ state ->
            ready=state==TextToSpeech.SUCCESS
            if(ready) {
                val s=getSharedPreferences("settings",0)
                spoken?.setLanguage(Locale("vi","VN"))
                spoken?.setSpeechRate(s.getFloat("rate",1f))
                spoken?.setPitch(s.getFloat("pitch",1f))
                spoken?.voices?.firstOrNull { it.name==s.getString("voice","") }?.let { spoken?.voice=it }
            }
        }
        displayStep()
    }
    private fun videoKey():String=program.id+"_"+moves[index].name
    private fun saveVideoLink() {
        val field=EditText(this).apply { hint="Dán link YouTube, TikTok hoặc video HTTPS";inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI }
        AlertDialog.Builder(this).setTitle("Lưu liên kết cho: "+moves[index].name)
            .setMessage("Liên kết YouTube/TikTok được lưu để mở lại trực tuyến, không tải tệp.")
            .setView(field).setPositiveButton("Lưu"){_,_->
                val link=field.text.toString().trim()
                val uri=Uri.parse(link)
                if(uri.scheme=="https" && !uri.host.isNullOrBlank()) {
                    videoPrefs.edit().putString("link_"+videoKey(),link).apply()
                    Toast.makeText(this,"Đã lưu liên kết",Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this,"Chỉ chấp nhận liên kết HTTPS hợp lệ",Toast.LENGTH_LONG).show()
            }.setNegativeButton("Hủy",null).show()
    }
    private fun assessVideo() {
        val move=moves[index]
        val name=EditText(this).apply { hint="Tên hoặc mô tả video";setSingleLine(false);minLines=2 }
        val choices=arrayOf(
            "Hướng dẫn đúng động tác và tư thế",
            "Có khởi động / hướng dẫn giảm độ khó",
            "Nhắc thở đều và không cố tập khi đau",
            "Phù hợp trình độ của tôi",
            "Không hứa giảm mỡ một vùng / kết quả phi thực tế"
        )
        val checked=BooleanArray(choices.size)
        val holder=LinearLayout(this).apply { orientation=1;setPadding(20,10,20,10) }
        holder.addView(TextView(this).apply {
            text="Đánh giá sơ bộ theo thông tin bạn cung cấp, KHÔNG phải AI đã xem và phân tích nội dung video."
            textSize=14f
        })
        holder.addView(name)
        val boxes=choices.mapIndexed { i,label ->
            CheckBox(this).apply { text=label;setOnCheckedChangeListener { _,yes->checked[i]=yes } ;holder.addView(this) }
        }
        AlertDialog.Builder(this).setTitle("🧠 Đánh giá video: "+move.name)
            .setView(ScrollView(this).apply { addView(holder) })
            .setPositiveButton("Xem nhận xét"){_,_->
                val score=checked.count { it }
                val comment=when {
                    score>=4->"Có nhiều dấu hiệu phù hợp theo mô tả. Vẫn cần tự kiểm tra kỹ thuật và độ tin cậy của người hướng dẫn."
                    score>=2->"Cần xem kỹ hơn kỹ thuật, mức độ và hướng dẫn an toàn trước khi tập."
                    else->"Chưa đủ dấu hiệu để khuyến nghị. Ưu tiên video có hướng dẫn rõ ràng, giảm độ khó và cảnh báo an toàn."
                }
                val text="Video: "+name.text.toString().ifBlank { "(chưa nhập tên)" }+
                    "\nĐộng tác: "+move.name+"\nTiêu chí đạt: "+score+"/"+choices.size+
                    "\n\n"+comment+"\n\nĐây là bảng kiểm dựa trên lựa chọn của bạn; chưa tự xem video hoặc xác thực chuyên môn."
                AlertDialog.Builder(this).setTitle("Kết quả kiểm tra sơ bộ")
                    .setMessage(text).setPositiveButton("Đã hiểu",null).show()
            }.setNegativeButton("Hủy",null).show()
    }
    private fun downloadVideo() {
        val field=EditText(this).apply {
            hint="https://example.org/exercise.mp4"
            setText(videoPrefs.getString("link_"+videoKey(),""))
        }
        AlertDialog.Builder(this).setTitle("Tải video được phép sử dụng")
            .setMessage("Chỉ nhập URL HTTPS trực tiếp tới tệp MP4 mà bạn có quyền tải. Không hỗ trợ tải luồng YouTube/TikTok.")
            .setView(field).setPositiveButton("Tải về"){_,_->
                val link=field.text.toString().trim()
                val uri=Uri.parse(link)
                if(uri.scheme!="https" || uri.host.isNullOrBlank() ||
                    !uri.path.orEmpty().lowercase(Locale.ROOT).endsWith(".mp4")) {
                    Toast.makeText(this,"Cần liên kết HTTPS trực tiếp kết thúc bằng .mp4",Toast.LENGTH_LONG).show()
                } else try {
                    val name="Chibi_"+System.currentTimeMillis()+".mp4"
                    val request=DownloadManager.Request(uri)
                        .setTitle(moves[index].name).setDescription("Video hướng dẫn Chibi Life")
                        .setMimeType("video/mp4")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
                    val manager=getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                    Toast.makeText(this,"Đang tải vào Downloads. Sau khi tải, chọn video từ máy để gắn vào động tác.",Toast.LENGTH_LONG).show()
                } catch(e:Exception) {
                    Toast.makeText(this,"Không thể tải: "+e.message,Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("Hủy",null).show()
    }
    private fun chooseLocalVideo() {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type="video/*"
        }
        startActivityForResult(i,pickVideoRequest)
    }
    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==pickVideoRequest && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                videoPrefs.edit().putString("local_"+videoKey(),uri.toString()).apply()
                Toast.makeText(this,"Đã gắn video ngoại tuyến cho động tác",Toast.LENGTH_SHORT).show()
            } catch(e:Exception) { Toast.makeText(this,"Không thể lưu quyền đọc video",Toast.LENGTH_LONG).show() }
        }
    }
    private fun openSavedVideo() {
        val local=videoPrefs.getString("local_"+videoKey(),null)
        val remote=videoPrefs.getString("link_"+videoKey(),null)
        val url=local ?: remote
        if(url==null) {
            Toast.makeText(this,"Chưa có video. Hãy lưu link hoặc chọn video từ máy.",Toast.LENGTH_LONG).show()
            return
        }
        try {
            val i=Intent(Intent.ACTION_VIEW,Uri.parse(url))
            if(local!=null) { i.setDataAndType(Uri.parse(local),"video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(i)
        } catch(_:Exception) { Toast.makeText(this,"Không mở được video, hãy chọn lại tệp hoặc kiểm tra mạng.",Toast.LENGTH_LONG).show() }
    }
    private fun searchVideo(site:String) {
        val move=moves.getOrNull(index) ?: return
        val query=move.name.replace(Regex("[^\\p{L}\\p{N} ]")," ").trim()+" hướng dẫn tập đúng kỹ thuật"
        val url=if(site=="youtube") "https://www.youtube.com/results?search_query="+Uri.encode(query)
            else "https://www.tiktok.com/search?q="+Uri.encode(query)
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
        catch(_:Exception) { Toast.makeText(this,"Không tìm thấy ứng dụng mở liên kết",Toast.LENGTH_LONG).show() }
    }
    private fun displayStep() {
        paused=true;handler.removeCallbacks(tick)
        val move=moves[index]
        remainingMs=move.seconds*1000L
        heading.text="${index+1}/${moves.size} • ${move.name}"
        guide.text="🎯 ${move.dose}\n\n📖 Cách thực hiện:\n${move.steps}\n\n🌿 Giảm độ khó:\n${move.easier}\n\n⚠️ Lưu ý:\n${move.avoid}"
        progress.progress=index
        val sec=(remainingMs/1000).toInt()
        counter.text=String.format(Locale.US,"%02d:%02d",sec/60,sec%60)
        control.text="▶ Tập động tác này"
        spoken?.stop()
    }
    private fun speak(message:String) {
        if(ready)spoken?.speak(message.take(350),TextToSpeech.QUEUE_FLUSH,null,"fitness-${index}")
    }
    private fun resume() {
        if(!paused)return
        paused=false
        if(startedAt==0L) startedAt=SystemClock.elapsedRealtime()
        deadline=SystemClock.elapsedRealtime()+remainingMs
        control.text="⏸ Tạm dừng"
        speak("Bài ${index+1}: ${moves[index].name}. ${moves[index].dose}. ${moves[index].steps}")
        handler.post(tick)
    }
    private fun pause() {
        if(paused)return
        remainingMs=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        paused=true;handler.removeCallbacks(tick)
        spoken?.stop()
        control.text="▶ Tiếp tục"
    }
    private fun nextMove() {
        pause()
        if(index==moves.lastIndex) {
            AlertDialog.Builder(this).setTitle("🎉 Đã đi hết chương trình!")
                .setMessage("Bạn hoàn thành các bước hướng dẫn. Lưu buổi tập vào nhật ký nhé.")
                .setPositiveButton("💾 Lưu buổi tập"){_,_->finishWorkout()}
                .setNegativeButton("Xem lại",null).show()
            return
        }
        index++;displayStep()
        resume()
    }
    private fun finishWorkout() {
        pause()
        val elapsed=if(startedAt==0L)0 else ((SystemClock.elapsedRealtime()-startedAt)/60000L).toInt()
        val mins=elapsed.coerceAtLeast(1)
        WorkoutData.save(this,WorkoutData.history(this)+Workout(
            LifeData.today(),program.name,mins,"Tập theo Chibi Coach, ${index+1}/${moves.size} bước; thời lượng ước tính"))
        Toast.makeText(this,"Đã ghi $mins phút vào nhật ký",Toast.LENGTH_LONG).show()
        finish()
    }
    override fun onPause() {
        pause();super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        spoken?.stop();spoken?.shutdown()
        super.onDestroy()
    }
}
