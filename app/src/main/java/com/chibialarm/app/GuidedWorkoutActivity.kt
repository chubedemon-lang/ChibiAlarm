package com.chibialarm.app

import android.app.*
import android.webkit.URLUtil
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.WindowInsets
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale
import com.chibialarm.app.fitness.RemainingWorkoutDuration

/** Entirely offline guided workout; spoken instructions depend on installed Vietnamese Android TTS. */
class GuidedWorkoutActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var counter:TextView
    private lateinit var heading:TextView
    private lateinit var guide:TextView
    private lateinit var poseView:WorkoutPoseView
    private lateinit var progress:ProgressBar
    private lateinit var control:Button
    private lateinit var program:TrainingProgram
    private var moves:List<TrainingMove> = emptyList()
    private var index=0
    private var deadline=0L
    private var remainingMs=0L
    private var paused=true
    private var pausedForSafety=false
    // One spoken cue per exercise; never speak while paused or during microphone input.
    private var tenSecondCueGiven=false
    private var shorterFromIndex=Int.MAX_VALUE
    private var startedAt=0L
    // Track active time only, excluding pauses and time spent outside the app.
    private var accumulated=0L
    private fun activeElapsedMs():Long = accumulated + if(!paused && startedAt!=0L)
        (SystemClock.elapsedRealtime()-startedAt).coerceAtLeast(0L) else 0L
    private var spoken:TextToSpeech?=null
    private var ready=false
    // Guest-only checkpoint; authenticated profiles must use isolated storage.
    private val checkpoint by lazy { getSharedPreferences("chibi_workout_checkpoint_guest", MODE_PRIVATE) }
    private var completed=false
    // Do not overwrite a saved session before the user chooses Restore or Start over.
    private var checkpointDecisionPending=false
    private fun saveCheckpoint() {
        if (completed || checkpointDecisionPending || moves.isEmpty()) return
        checkpoint.edit().putString("program",program.id)
            .putInt("index",index).putLong("remaining",remainingMs.coerceIn(0L,600_000L))
            .putBoolean("safety",pausedForSafety)
            .putBoolean("tenSecondCueGiven",tenSecondCueGiven)
            .putInt("shorterFrom",shorterFromIndex)
            .putBoolean("started",activeElapsedMs()>0L)
            .putLong("elapsed",activeElapsedMs())
            .putLong("savedAt",System.currentTimeMillis()).apply()
    }
    private fun offerCheckpoint() {
        if (checkpoint.getString("program",null)!=program.id) return
        val age=System.currentTimeMillis()-checkpoint.getLong("savedAt",0L)
        if (age !in 0L..604_800_000L) {
            checkpoint.edit().clear().apply()
            return
        }
        checkpointDecisionPending=true
        AlertDialog.Builder(this).setTitle("Tiếp tục buổi tập đã lưu?")
            .setMessage("Đã lưu bước và thời gian còn lại trên máy. Khôi phục luôn ở trạng thái tạm dừng.")
            .setPositiveButton("Khôi phục") { _,_ ->
                checkpointDecisionPending=false
                index=checkpoint.getInt("index",0).coerceIn(0,moves.lastIndex)
                shorterFromIndex=checkpoint.getInt("shorterFrom",Int.MAX_VALUE).coerceIn(0, moves.size)
                displayStep()
                remainingMs=checkpoint.getLong("remaining",remainingMs).coerceIn(0L,600_000L)
                tenSecondCueGiven=checkpoint.getBoolean("tenSecondCueGiven",remainingMs<=10_000L)
                pausedForSafety=checkpoint.getBoolean("safety",false)
                val elapsed=checkpoint.getLong("elapsed",0L).coerceIn(0L,86_400_000L)
                accumulated=elapsed
                startedAt=0L
                val seconds=(remainingMs/1000).toInt()
                counter.text=String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
                control.text=if(pausedForSafety) "⚠️ Đã dừng vì đau" else "▶ Tiếp tục"
            }
            .setNegativeButton("Bắt đầu lại") { _,_ ->
                checkpointDecisionPending=false
                checkpoint.edit().clear().apply()
            }
            .setCancelable(false).show()
    }

    private val videoPrefs by lazy { getSharedPreferences("workout_videos",0) }
    private val pickVideoRequest=889
    private val voiceRequest=890
    private val tick=object:Runnable {
        override fun run() {
            if(paused)return
            val left=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs=left
            if (left in 1L..10_000L && !tenSecondCueGiven && !pausedForSafety) {
                tenSecondCueGiven=true
                val upcoming = moves.getOrNull(index + 1)?.name
                speak(if (upcoming != null) "Còn mười giây. Tiếp theo: $upcoming." else "Còn mười giây. Sắp hoàn thành buổi tập.")
            }
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
        poseView=WorkoutPoseView(this).apply {
            importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(poseView)
        root.addView(Button(this).apply {
            text="🔊 Nghe huấn luyện động tác hiện tại"
            contentDescription="Nghe một lần hướng dẫn, số hiệp, cách tập và lưu ý cho động tác hiện tại"
            setOnClickListener {
                if (pausedForSafety) {
                    Toast.makeText(this@GuidedWorkoutActivity,
                        "Đã dừng vì đau. Không tiếp tục hướng dẫn vận động.", Toast.LENGTH_LONG).show()
                } else {
                    speak(WorkoutContextCoach.script(moves[index]))
                }
            }
        })
        root.addView(Button(this).apply {
            text="🌿 Nghe cách tập nhẹ hơn"
            contentDescription="Nghe phương án giảm độ khó cho động tác hiện tại"
            setOnClickListener {
                if (pausedForSafety) {
                    Toast.makeText(this@GuidedWorkoutActivity,
                        "Đã dừng vì đau. Hãy nghỉ và cân nhắc hỗ trợ y tế nếu cần.", Toast.LENGTH_LONG).show()
                } else {
                    speak(WorkoutContextCoach.script(moves[index], preferEasier = true))
                }
            }
        })
        root.addView(TextView(this).apply {
            text="Hình minh họa tư thế đơn giản, không đánh giá kỹ thuật. Luôn đọc hướng dẫn từng bước."
            textSize=12f
        })
        counter=TextView(this).apply {
            textSize=54f;gravity=Gravity.CENTER;setTextColor(Color.rgb(100,70,140))
        }
        root.addView(counter)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=moves.size;progress=0
        }
        root.addView(progress)
        guide=TextView(this).apply { textSize=16f;setPadding(10,22,10,20);setTextColor(Color.rgb(70,61,85)) }
        root.addView(ScrollView(this).apply { addView(guide) },LinearLayout.LayoutParams(-1,(220*resources.displayMetrics.density).toInt()))
        root.addView(Button(this).apply {
            text="📖 Hướng dẫn từng bước • nghe lại"
            contentDescription="Xem từng bước của động tác hiện tại và nghe hướng dẫn từng bước"
            setOnClickListener { showStepByStepGuide() }
        })
        val online=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        online.addView(Button(this).apply { text="▶ YouTube";setOnClickListener { searchVideo("youtube") } },LinearLayout.LayoutParams(0,-2,1f))
        online.addView(Button(this).apply { text="♫ TikTok";setOnClickListener { searchVideo("tiktok") } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(online)
        root.addView(Button(this).apply { text="🧠 Chibi xem xét video & hình minh họa";setOnClickListener { startActivity(Intent(this@GuidedWorkoutActivity,ExerciseVideoReviewActivity::class.java).putExtra("move",moves[index].name).putExtra("videoKey",videoKey()).putExtra("url",videoPrefs.getString("link_"+videoKey(),""))) } })
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
        root.addView(Button(this).apply {
            text="🎤 Nói với Chibi (nhấn để nói)"
            contentDescription="Nhấn để yêu cầu Android nhận giọng nói, không ghi âm nền"
            setOnClickListener { requestVoiceCommand() }
        })
        root.addView(Button(this).apply {
            text="💬 Nhập yêu cầu tập luyện"
            setOnClickListener { requestTypedCommand() }
        })
        root.addView(Button(this).apply {
            text="⚙️ Điều chỉnh buổi tập ngay"
            contentDescription="Tăng thời gian nghỉ, rút ngắn động tác hoặc nghe hướng dẫn dễ hơn"
            setOnClickListener { adjustCurrentMove() }
        })
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text="← Trước";setOnClickListener {
            if(!pausedForSafety && index>0) { pause();index--;displayStep(); }
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
        // Keep every workout control reachable on small phones and with large accessibility fonts.
        val page=ScrollView(this).apply {
            isFillViewport=true
            addView(root)
        }
        setContentView(page)
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
        if (savedInstanceState == null) offerCheckpoint()
        // Restore the active exercise after rotation, but never auto-resume.
        if(savedInstanceState!=null &&
            savedInstanceState.getString("workout_program")==program.id) {
            index=savedInstanceState.getInt("workout_index",0).coerceIn(0,moves.lastIndex)
            displayStep()
            remainingMs=savedInstanceState.getLong("workout_remaining",remainingMs)
                .coerceIn(0L,600_000L)
            tenSecondCueGiven=savedInstanceState.getBoolean("workout_ten_second_cue",remainingMs<=10_000L)
            pausedForSafety=savedInstanceState.getBoolean("workout_safety",false)
            shorterFromIndex=savedInstanceState.getInt("workout_shorter_from",Int.MAX_VALUE).coerceIn(0,moves.size)
            val elapsed=savedInstanceState.getLong("workout_elapsed",0L).coerceAtLeast(0L)
            accumulated=elapsed.coerceAtMost(86_400_000L)
            startedAt=0L
            val seconds=(remainingMs/1000).toInt()
            counter.text=String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
            control.text=if(pausedForSafety) "⚠️ Đã dừng vì đau" else "▶ Tiếp tục"
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        // Save monotonic elapsed duration, not an absolute deadline.
        pause()
        outState.putString("workout_program",program.id)
        outState.putInt("workout_index",index)
        outState.putLong("workout_remaining",remainingMs)
        outState.putBoolean("workout_ten_second_cue",tenSecondCueGiven)
        outState.putBoolean("workout_safety",pausedForSafety)
        outState.putInt("workout_shorter_from",shorterFromIndex)
        outState.putBoolean("workout_started",activeElapsedMs()>0L)
        outState.putLong("workout_elapsed",activeElapsedMs())
        super.onSaveInstanceState(outState)
    }
    private fun requestVoiceCommand() {
        AlertDialog.Builder(this).setTitle("🎤 Điều khiển bằng giọng nói")
            .setMessage("Chỉ bắt đầu nghe sau khi bạn nhấn Tiếp tục. Android sẽ mở dịch vụ nhận dạng giọng nói của máy; tùy nhà cung cấp, âm thanh có thể được xử lý trực tuyến. Chibi không tự bật micro và không lưu bản ghi âm. Bạn cũng có thể nhập chữ thay thế.")
            .setPositiveButton("Tiếp tục"){_,_->
                // A voice command must never compete with spoken instructions or a running timer.
                // Keep the workout paused even if recognition is cancelled or unavailable.
                pause()
                spoken?.stop()
                val request=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE,"vi-VN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT,"Ví dụ: nghỉ thêm, bài tiếp theo, dễ hơn, dừng lại")
                }
                try { startActivityForResult(request,voiceRequest) }
                catch (_:Exception) { Toast.makeText(this,"Thiết bị chưa có dịch vụ nhận giọng nói. Hãy nhập yêu cầu bằng chữ.",Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Hủy",null).show()
    }
    private fun requestTypedCommand() {
        val field=EditText(this).apply { hint="Ví dụ: nghỉ thêm, dễ hơn, tiếp tục";setSingleLine(false) }
        AlertDialog.Builder(this).setTitle("💬 Chibi Coach • lệnh offline")
            .setMessage("Điều khiển buổi tập bằng chữ, không gửi dữ liệu lên mạng.")
            .setView(field).setPositiveButton("Gửi"){_,_->handleCoachCommand(field.text.toString())}
            .setNegativeButton("Hủy",null).show()
    }
    private fun handleCoachCommand(utterance:String) {
        when(WorkoutVoiceCommands.parse(utterance)) {
            WorkoutVoiceCommands.Command.PAIN -> {
                pause()
                pausedForSafety=true
                control.text="⚠️ Đã dừng vì đau"
                // Persist the safety lock after setting it, including when recognition
                // arrives while the workout is already paused.
                saveCheckpoint()
                AlertDialog.Builder(this).setTitle("⚠️ Đã dừng vì bạn báo đau")
                    .setMessage("Không cố tập khi đau. Hãy kết thúc buổi tập nếu triệu chứng tiếp diễn; tìm trợ giúp y tế phù hợp nếu đau nhiều hoặc có dấu hiệu bất thường. Chibi sẽ không tự tiếp tục bằng giọng nói.")
                    .setPositiveButton("Đã hiểu",null).show()
                speak("Đã dừng tập vì bạn báo đau. Hãy ưu tiên an toàn.")
            }
            WorkoutVoiceCommands.Command.PAUSE -> { pause();speak("Đã tạm dừng.") }
            WorkoutVoiceCommands.Command.RESUME -> resume()
            WorkoutVoiceCommands.Command.NEXT -> nextMove()
            WorkoutVoiceCommands.Command.PREVIOUS -> { if(!pausedForSafety) { pause();if(index>0)index--;displayStep();speak("Bài "+moves[index].name) } }
            WorkoutVoiceCommands.Command.REPEAT -> speak(moves[index].steps)
            WorkoutVoiceCommands.Command.EASIER_AND_REST -> {
                if (pausedForSafety) {
                    Toast.makeText(this,"Buổi tập đã dừng vì báo đau.",Toast.LENGTH_LONG).show()
                } else {
                    // Never silently mutate the session on a potentially misheard compound command.
                    // Pause first, then require an explicit user decision; cancel keeps the timer intact.
                    pause()
                    val addedSeconds=WorkoutRestRequest.seconds(utterance)
                    val exerciseName=moves[index].name
                    val easierCue=moves[index].easier
                    AlertDialog.Builder(this).setTitle("🐰 Xác nhận điều chỉnh buổi tập")
                        .setMessage("Bạn muốn nghỉ thêm $addedSeconds giây và xem cách tập nhẹ hơn cho $exerciseName?"+
                            "\n\n$easierCue"+
                            "\n\nChibi chỉ tăng thời gian nghỉ và gợi ý cách tập; chưa tự đổi số hiệp hoặc kỹ thuật.")
                        .setPositiveButton("Đồng ý điều chỉnh") { _, _ ->
                            if (pausedForSafety || completed) return@setPositiveButton
                            remainingMs=(remainingMs+addedSeconds*1000L).coerceAtMost(600_000L)
                            val seconds=(remainingMs/1000).toInt()
                            counter.text=String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
                            control.text="▶ Tiếp tục khi sẵn sàng"
                            saveCheckpoint()
                            speak("Đã thêm $addedSeconds giây nghỉ. $easierCue")
                        }
                        .setNegativeButton("Giữ nguyên",null)
                        .show()
                }
            }
            WorkoutVoiceCommands.Command.EASIER -> {
                pause()
                AlertDialog.Builder(this).setTitle("🌿 Cách tập dễ hơn")
                    .setMessage(moves[index].easier+"\\n\\nChỉ tiếp tục khi bạn thấy thoải mái.")
                    .setPositiveButton("Đã hiểu",null).show()
                speak(moves[index].easier)
            }
            WorkoutVoiceCommands.Command.SHORTER_REMAINING -> shortenRemainingWorkout()
            WorkoutVoiceCommands.Command.SHORTER -> confirmShortenCurrentMove()
            WorkoutVoiceCommands.Command.REST -> {
                if (pausedForSafety) {
                    Toast.makeText(this,"Buổi tập đang dừng vì báo đau; không thay đổi thời gian.",Toast.LENGTH_LONG).show()
                } else {
                    confirmExtraRest(WorkoutRestRequest.seconds(utterance))
                }
            }
            WorkoutVoiceCommands.Command.UNKNOWN -> {
                AlertDialog.Builder(this).setTitle("Chibi chưa hiểu yêu cầu")
                    .setMessage("Bạn có thể nói: tạm dừng, tiếp tục, bài tiếp theo, bài trước, nhắc lại, dễ hơn, rút ngắn hoặc nghỉ thêm. Đây là bộ lệnh offline, chưa phải hội thoại AI tự do.")
                    .setPositiveButton("Đóng",null).show()
            }
        }
    }
    /**
     * Spoken rest requests are proposals, not immediate timer mutations.
     * Guard against stale dialogs after moving to another exercise or changing the timer.
     */
    private fun confirmExtraRest(addedSeconds: Int) {
        if (pausedForSafety || completed || index !in moves.indices) return
        pause()
        val originalMs = remainingMs
        val originalIndex = index
        val proposedMs = (originalMs + addedSeconds.coerceIn(0, 600) * 1000L)
            .coerceAtMost(600_000L)
        if (proposedMs == originalMs) {
            Toast.makeText(this, "Đã đạt giới hạn thời gian 10 phút.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Xác nhận thêm thời gian nghỉ")
            .setMessage("Động tác: ${moves[originalIndex].name}\\n" +
                "Thời gian hiện tại: ${originalMs / 1000} giây → ${proposedMs / 1000} giây.\\n" +
                "Chỉ thay đổi sau khi bạn xác nhận.")
            .setPositiveButton("Thêm thời gian") { _, _ ->
                if (pausedForSafety || completed || index != originalIndex ||
                    remainingMs != originalMs) return@setPositiveButton
                remainingMs = proposedMs
                val seconds = (remainingMs / 1000).toInt()
                counter.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                control.text = "▶ Tiếp tục khi sẵn sàng"
                saveCheckpoint()
                speak("Đã thêm thời gian nghỉ. Hãy tiếp tục khi sẵn sàng.")
            }
            .setNegativeButton("Giữ nguyên", null).show()
    }

    /** Custom rest duration is user-selected, bounded and confirmed before changing the timer. */
    private fun requestCustomRest() {
        if (pausedForSafety || completed) return
        pause()
        val field = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Số giây (1–600)"
            setText("60")
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("⏱ Chọn thời gian nghỉ thêm")
            .setMessage("Nhập từ 1 đến 600 giây. Chibi sẽ cho xem trước trước khi áp dụng.")
            .setView(field)
            .setPositiveButton("Xem trước", null)
            .setNegativeButton("Hủy", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seconds = field.text.toString().trim().toIntOrNull()
                if (seconds == null || seconds !in 1..600) {
                    field.error = "Hãy nhập số giây từ 1 đến 600"
                } else {
                    dialog.dismiss()
                    confirmExtraRest(seconds)
                }
            }
        }
        dialog.show()
    }

    /** Show the exact impact before changing the active exercise; cancel leaves its timer untouched. */
    private fun confirmShortenCurrentMove() {
        if (pausedForSafety || completed) return
        pause()
        val originalMs = remainingMs
        val previewIndex = index
        val proposedMs = RemainingWorkoutDuration.shortenActiveMs(originalMs)
        val moveName = moves.getOrNull(index)?.name ?: return
        AlertDialog.Builder(this).setTitle("Xác nhận rút ngắn động tác")
            .setMessage("Bài: $moveName\nThời gian còn lại: ${originalMs / 1000} giây → ${proposedMs / 1000} giây.\n\nCác động tác sau không thay đổi. Bạn có thể nghỉ thêm nếu cần.")
            .setPositiveButton("Áp dụng") { _, _ ->
                // A dialog can outlive a step change (e.g. after a lifecycle event).
                // Never apply an old preview to a different exercise or timer.
                if (pausedForSafety || completed || index != previewIndex ||
                    remainingMs != originalMs || index !in moves.indices) return@setPositiveButton
                remainingMs = proposedMs
                val seconds = (remainingMs / 1000).toInt()
                counter.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                control.text = "▶ Tiếp tục bài ngắn hơn"
                saveCheckpoint()
                speak("Đã rút ngắn động tác còn ${seconds} giây. Hãy tiếp tục khi sẵn sàng.")
            }
            .setNegativeButton("Giữ nguyên", null)
            .show()
    }
    /** Explicit local adjustments; never changes other moves or silently claims AI advice. */
    private fun adjustCurrentMove() {
        if (pausedForSafety) {
            AlertDialog.Builder(this).setTitle("⚠️ Buổi tập đã dừng")
                .setMessage("Bạn đã báo đau. Chibi sẽ không điều chỉnh hoặc tiếp tục bài tập cho tới khi bạn xác nhận tình trạng an toàn bằng nút Tiếp tục.")
                .setPositiveButton("Đã hiểu",null).show()
            return
        }
        val options=arrayOf(
            "🌿 Giảm thời gian động tác còn một nửa",
            "⏱ Thêm 30 giây nghỉ",
            "⏱ Tự chọn số giây nghỉ",
            "📖 Xem cách tập dễ hơn",
            "⏸ Tạm dừng buổi tập",
            "⏱ Rút ngắn tất cả động tác còn lại"
        )
        AlertDialog.Builder(this).setTitle("Chibi điều chỉnh theo sức của bạn")
            .setItems(options) { _, choice ->
                when(choice) {
                    0 -> confirmShortenCurrentMove()
                    1 -> confirmExtraRest(30)
                    2 -> requestCustomRest()
                    3 -> {
                        pause()
                        AlertDialog.Builder(this).setTitle("Cách tập dễ hơn")
                            .setMessage(moves[index].easier+"\\n\\nDừng tập nếu thấy đau.")
                            .setPositiveButton("Đã hiểu",null).show()
                        speak(moves[index].easier)
                    }
                    4 -> { pause();speak("Đã tạm dừng buổi tập.") }
                    5 -> shortenRemainingWorkout()
                }
            }.setNegativeButton("Đóng",null).show()
    }
    private fun shortenRemainingWorkout() {
        if (pausedForSafety || completed) return
        pause()
        val before = remainingMs + moves.indices.drop(index + 1).sumOf { position ->
            RemainingWorkoutDuration.seconds(moves[position].seconds, position, shorterFromIndex) * 1000L
        }
        // Bind the proposal to the exact paused workout snapshot. A stale dialog
        // must not shorten a different step or overwrite a newer timer adjustment.
        val previewIndex = index
        val previewRemainingMs = remainingMs
        val previewShorterFrom = shorterFromIndex
        val proposedFrom = minOf(shorterFromIndex, index)
        val afterCurrent = RemainingWorkoutDuration.shortenActiveMs(remainingMs)
        val after = afterCurrent + moves.indices.drop(index + 1).sumOf { position ->
            RemainingWorkoutDuration.seconds(moves[position].seconds, position, proposedFrom) * 1000L
        }
        fun format(ms: Long): String = "${(ms / 60000)} phút ${(ms / 1000) % 60} giây"
        val stepPreview = RemainingWorkoutDuration.preview(
            moves.map { it.name }, moves.map { it.seconds }, index,
            (remainingMs / 1000L).toInt(), shorterFromIndex
        )
        val details = stepPreview.take(4).joinToString("\\n") { step ->
            "• ${step.name}: ${step.beforeSeconds}s → ${step.afterSeconds}s"
        } + if (stepPreview.size > 4) "\\n… và ${stepPreview.size - 4} bước tiếp theo" else ""

        AlertDialog.Builder(this).setTitle("Xem trước giáo án rút ngắn")
            .setMessage("Thời gian còn lại dự kiến:\nTrước: ${format(before)}\nSau: ${format(after)}\n\n" +
                "Giảm thời lượng động tác hiện tại và các động tác phía sau. " +
                "Những bước đã hoàn thành được giữ nguyên. Đây là ước tính, không bao gồm nghỉ thêm.\\n\\n" + details)
            .setPositiveButton("Xác nhận thay đổi") { _, _ ->
                if (pausedForSafety || completed || index != previewIndex ||
                    index !in moves.indices || remainingMs != previewRemainingMs ||
                    shorterFromIndex != previewShorterFrom) {
                    Toast.makeText(this, "Giáo án đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                shorterFromIndex = proposedFrom
                remainingMs = afterCurrent
                val seconds = (remainingMs / 1000).toInt()
                counter.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                control.text = "▶ Tiếp tục giáo án ngắn hơn"
                saveCheckpoint()
                speak("Đã rút ngắn phần tập còn lại. Các bước đã hoàn thành không thay đổi.")
            }
            .setNegativeButton("Giữ giáo án cũ", null).show()
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
        if(requestCode==voiceRequest && resultCode==RESULT_OK) {
            val phrase=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!phrase.isNullOrBlank()) {
                // Speech recognition is fallible: preview before changing workout state.
                // A reported safety symptom is the exception: stop immediately.
                val command=WorkoutVoiceCommands.parse(phrase)
                if (command==WorkoutVoiceCommands.Command.PAIN) {
                    handleCoachCommand(phrase)
                } else {
                    AlertDialog.Builder(this).setTitle("🐰 Chibi nghe được")
                        .setMessage("“"+phrase.take(160)+"”\\n\\n"+WorkoutVoiceCommands.describe(command)+
                            "\\n\\nKiểm tra câu nhận dạng trước khi điều khiển buổi tập.")
                        .setPositiveButton("Áp dụng") { _,_ -> handleCoachCommand(phrase) }
                        .setNeutralButton("Sửa câu nhận dạng") { _, _ ->
                            val corrected=EditText(this).apply {
                                setText(phrase.take(160))
                                setSelection(text.length)
                                setSingleLine(false)
                            }
                            AlertDialog.Builder(this).setTitle("Sửa yêu cầu gửi Chibi")
                                .setMessage("Chibi chỉ áp dụng câu đã sửa sau khi bạn xác nhận. Nếu đang đau hoặc khó chịu, hãy dừng tập ngay.")
                                .setView(corrected)
                                .setPositiveButton("Gửi câu đã sửa") { _, _ ->
                                    handleCoachCommand(corrected.text.toString())
                                }
                                .setNegativeButton("Hủy",null).show()
                        }
                        .setNegativeButton("Không đúng",null).show()
                }
            }
        }
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
    private fun showStepByStepGuide() {
        // A step-by-step walkthrough is opt-in and never consumes workout time.
        pause()
        spoken?.stop()
        val move=moves.getOrNull(index) ?: return
        val steps=WorkoutStepGuide.split(move.steps)
        var current=0
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(28,20,28,12)
        }
        val stepProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=steps.size
            progress=1
            contentDescription="Tiến độ hướng dẫn động tác"
        }
        val label=TextView(this).apply {
            textSize=18f
            setPadding(8,24,8,24)
            setTextColor(Color.rgb(70,61,85))
        }
        container.addView(stepProgress)
        container.addView(ScrollView(this).apply {
            addView(label)
        },LinearLayout.LayoutParams(-1,(250*resources.displayMetrics.density).toInt()))
        val dialog=AlertDialog.Builder(this)
            .setTitle("📖 ${move.name}")
            .setView(container)
            .setNegativeButton("Đóng",null)
            .setNeutralButton("🔊 Nghe bước này",null)
            .setPositiveButton("Bước tiếp →",null)
            .create()
        fun renderStep() {
            label.text="Bước ${current+1}/${steps.size}\n\n${steps[current]}\n\n🌿 Dễ hơn: ${move.easier}\n⚠️ ${move.avoid}"
            label.contentDescription="Bước ${current+1} trên ${steps.size}. ${steps[current]}"
            stepProgress.progress=current+1
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled=current<steps.lastIndex
                text=if(current==steps.lastIndex) "Đã xem hết" else "Bước tiếp →"
            }
        }
        dialog.setOnShowListener {
            renderStep()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(current<steps.lastIndex) {
                    current++
                    spoken?.stop()
                    renderStep()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                speak("Bước ${current+1}. ${steps[current]}")
            }
        }
        dialog.setOnDismissListener { spoken?.stop() }
        dialog.show()
    }

    private fun displayStep() {
        paused=true;handler.removeCallbacks(tick)
        val move=moves[index]
        tenSecondCueGiven=false
        remainingMs=RemainingWorkoutDuration.seconds(move.seconds,index,shorterFromIndex)*1000L
        heading.text="${index+1}/${moves.size} • ${move.name}"
        poseView.pose=WorkoutPoseGuide.classify(move.name)
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
        if(pausedForSafety) {
            AlertDialog.Builder(this).setTitle("Xác nhận trước khi tập lại")
                .setMessage("Bạn đã báo đau. Chỉ tiếp tục nếu triệu chứng đã hết và bạn thấy an toàn. Nếu còn đau, hãy dừng buổi tập.")
                .setPositiveButton("Tôi đã ổn, tiếp tục"){_,_->pausedForSafety=false;resume()}
                .setNegativeButton("Tiếp tục nghỉ",null).show()
            return
        }
        if(!paused)return
        paused=false
        startedAt=SystemClock.elapsedRealtime()
        deadline=SystemClock.elapsedRealtime()+remainingMs
        control.text="⏸ Tạm dừng"
        speak("Bài ${index+1}: ${moves[index].name}. ${moves[index].dose}. ${moves[index].steps}")
        handler.post(tick)
    }
    private fun pause() {
        if(paused)return
        val now=SystemClock.elapsedRealtime()
        remainingMs=(deadline-now).coerceAtLeast(0L)
        if(startedAt!=0L) accumulated+=(now-startedAt).coerceAtLeast(0L)
        startedAt=0L
        paused=true;handler.removeCallbacks(tick)
        spoken?.stop()
        control.text="▶ Tiếp tục"
    }
    private fun nextMove() {
        if(pausedForSafety) {
            Toast.makeText(this,"Đang dừng vì báo đau. Hãy xác nhận an toàn trước khi tiếp tục.",Toast.LENGTH_LONG).show()
            return
        }
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
        // A skipped or immediately abandoned session is not a completed workout.
        // Never manufacture one minute of exercise (and downstream RPG progress).
        val elapsedMs=activeElapsedMs()
        if (elapsedMs < 60_000L) {
            AlertDialog.Builder(this)
                .setTitle("Chưa đủ thời gian vận động")
                .setMessage("Buổi tập có dưới 1 phút hoạt động thực tế. Chibi chưa ghi nhật ký hoặc trao tiến độ nhiệm vụ. Bạn có thể tập tiếp hoặc thoát mà không lưu.")
                .setPositiveButton("Tập tiếp", null)
                .setNegativeButton("Thoát không lưu") { _, _ ->
                    completed=true
                    checkpoint.edit().clear().apply()
                    finish()
                }.show()
            return
        }
        completed=true
        checkpoint.edit().clear().apply()
        val mins=(elapsedMs/60000L).toInt()
        WorkoutData.save(this,WorkoutData.history(this)+Workout(
            LifeData.today(),program.name,mins,"Tập theo Chibi Coach, ${index+1}/${moves.size} bước; thời lượng ước tính"))
        Toast.makeText(this,"Đã ghi $mins phút vào nhật ký",Toast.LENGTH_LONG).show()
        finish()
    }
    override fun onPause() {
        pause()
        saveCheckpoint()
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        spoken?.stop();spoken?.shutdown()
        super.onDestroy()
    }
}
