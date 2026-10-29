package com.chibialarm.app

import android.app.*
import android.content.*
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
