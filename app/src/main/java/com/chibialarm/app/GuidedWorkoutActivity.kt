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
import java.util.UUID
import com.chibialarm.app.fitness.RemainingWorkoutDuration
import com.chibialarm.app.fitness.WorkoutSessionTimeEstimate
import com.chibialarm.app.fitness.WorkoutMidSessionPlanner
import com.chibialarm.app.fitness.WorkoutMidSessionRequest
import com.chibialarm.app.rpg.RpgDatabase
import com.chibialarm.app.rpg.RpgEngine
import com.chibialarm.app.rpg.WorkoutRewardEvidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Entirely offline guided workout; spoken instructions depend on installed Vietnamese Android TTS. */
class GuidedWorkoutActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    // Reward work is asynchronous; the durable journal remains the retry source if this Activity is recreated.
    private val rewardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var counter:TextView
    private lateinit var heading:TextView
    private lateinit var guide:TextView
    private lateinit var poseView:WorkoutPoseView
    private lateinit var progress:ProgressBar
    private lateinit var control:Button
    private lateinit var program:TrainingProgram
    // Stable across rotation/process restore so one guided session can earn XP only once.
    private var sessionId:String = UUID.randomUUID().toString()
    private fun validSessionId(value:String?):String? = value?.takeIf {
        runCatching { UUID.fromString(it).toString()==it }.getOrDefault(false)
    }
    private fun currentPlanFingerprint(): String = WorkoutConstraintFilter.fingerprint(originalMoves, workoutConstraints)
    private var moves:List<TrainingMove> = emptyList()
    private var originalMoves:List<TrainingMove> = emptyList()
    private val substitutions=mutableMapOf<Int,TrainingMove>()
    private var index=0
    private var deadline=0L
    private var remainingMs=0L
    private var paused=true
    private var pausedForSafety=false
    private var workoutConstraints = WorkoutConstraintProfile()
    private var workoutLevel = 0
    // Names only: these are optional catalog extensions, not user transcripts.
    private val optionalExtensionNames = mutableSetOf<String>()
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
    // Index-scoped rep guidance: completed steps are never retroactively edited.
    private val repTargets=mutableMapOf<Int,Int>()
    // Timed moves use the timer; set-based moves require explicit user confirmation.
    private val completedMoveIndices=sortedSetOf<Int>()
    private val completedSetsByMove=mutableMapOf<Int,Int>()
    private lateinit var setProgress:TextView
    private lateinit var completeSetButton:Button
    private lateinit var restSetButton:Button
    private lateinit var customSetRestButton:Button
    private var resting=false
    private var restDeadline=0L
    private var restRemainingMs=0L
    private var setTimerExpired=false
    private fun targetDose(position:Int):String = repTargets[position]?.let { "$it lần (mục tiêu đã chỉnh)" } ?: moves[position].dose
    private var completed=false
    // Do not overwrite a saved session before the user chooses Restore or Start over.
    private var checkpointDecisionPending=false
    private fun saveCheckpoint() {
        if (completed || checkpointDecisionPending || moves.isEmpty()) return
        checkpoint.edit().putString("program",program.id)
            .putString("planNames",WorkoutMidSessionPlanner.encodePlan(originalMoves))
            .putString("planFingerprint",currentPlanFingerprint())
            .putBoolean("avoidWristLoad",workoutConstraints.avoidWristLoad)
            .putBoolean("avoidFloorPositions",workoutConstraints.avoidFloorPositions)
            .putString("extensionMoves",optionalExtensionNames.sorted().joinToString("\n"))
            .putString("sessionId",sessionId)
            .putInt("index",index).putLong("remaining",remainingMs.coerceIn(0L,600_000L))
            .putBoolean("safety",pausedForSafety)
            .putBoolean("tenSecondCueGiven",tenSecondCueGiven)
            .putInt("shorterFrom",shorterFromIndex)
            .putString("repTargets",repTargets.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("substitutions",WorkoutMoveSubstitution.encode(substitutions,originalMoves,workoutConstraints))
            .putString("completedMoves",WorkoutCompletionTracker.encode(completedMoveIndices,moves.size))
            .putString("completedSets",WorkoutSetTracker.encode(completedSetsByMove,moves))
            .putBoolean("resting",resting)
            .putLong("restRemaining",restRemainingMs.coerceIn(0L,600_000L))
            .putBoolean("setTimerExpired",setTimerExpired)
            .putBoolean("started",activeElapsedMs()>0L)
            .putLong("elapsed",activeElapsedMs())
            .putLong("savedAt",System.currentTimeMillis()).apply()
    }
    private fun offerCheckpoint() {
        if (checkpoint.getString("program",null)!=program.id) return
        val savedPlanNames = checkpoint.getString("planNames", null)
        if (!savedPlanNames.isNullOrBlank()) {
            val restoredPlan = WorkoutMidSessionPlanner.decodePlan(savedPlanNames)
            if (restoredPlan == null) {
                checkpoint.edit().clear().apply()
                Toast.makeText(this,"Không thể xác minh giáo án đã lưu; tiến độ cũ không được áp dụng nhầm.",Toast.LENGTH_LONG).show()
                return
            }
            originalMoves = restoredPlan
            moves = restoredPlan
            program = program.copy(moves = restoredPlan)
        }
        workoutConstraints = WorkoutConstraintProfile(
            avoidWristLoad = workoutConstraints.avoidWristLoad ||
                checkpoint.getBoolean("avoidWristLoad", false),
            avoidFloorPositions = workoutConstraints.avoidFloorPositions ||
                checkpoint.getBoolean("avoidFloorPositions", false)
        )
        optionalExtensionNames.clear()
        optionalExtensionNames.addAll(
            checkpoint.getString("extensionMoves", "").orEmpty().split("\n")
                .filter { name -> name.isNotBlank() && moves.any { it.name == name } }
                .take(20)
        )
        val savedFingerprint = checkpoint.getString("planFingerprint", null)
        if ((savedFingerprint != null && savedFingerprint != currentPlanFingerprint()) ||
            (savedFingerprint == null && (workoutConstraints.avoidWristLoad || workoutConstraints.avoidFloorPositions))) {
            checkpoint.edit().clear().apply()
            Toast.makeText(this,
                "Giáo án đã thay đổi theo lựa chọn hiện tại. Chibi không khôi phục hiệp cũ để tránh gán nhầm tiến độ; hãy bắt đầu buổi mới.",
                Toast.LENGTH_LONG).show()
            return
        }
        val savedSessionId=validSessionId(checkpoint.getString("sessionId",null))
        if (savedSessionId!=null && WorkoutData.history(this).any { it.sessionId==savedSessionId }) {
            // A crash after the durable journal append must not reopen a completed session.
            checkpoint.edit().clear().apply()
            return
        }
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
                sessionId=validSessionId(checkpoint.getString("sessionId",null)) ?: UUID.randomUUID().toString()
                index=checkpoint.getInt("index",0).coerceIn(0,moves.lastIndex)
                val savedSubstitutions=checkpoint.getString("substitutions",null)
                substitutions.clear()
                substitutions.putAll(WorkoutMoveSubstitution.decode(savedSubstitutions,originalMoves,workoutConstraints))
                moves=WorkoutMoveSubstitution.apply(originalMoves,savedSubstitutions,workoutConstraints)
                shorterFromIndex=checkpoint.getInt("shorterFrom",Int.MAX_VALUE).coerceIn(0, moves.size)
                repTargets.clear()
                repTargets.putAll(WorkoutRepTargets.decode(checkpoint.getString("repTargets",null),moves.size,index))
                completedMoveIndices.clear()
                completedMoveIndices.addAll(WorkoutCompletionTracker.decode(checkpoint.getString("completedMoves",null),moves.size))
                completedSetsByMove.clear()
                completedSetsByMove.putAll(WorkoutSetTracker.decode(checkpoint.getString("completedSets",null),moves))
                completedMoveIndices.removeAll { position ->
                    val plan=setPlan(position)
                    plan!=null && !WorkoutSetTracker.isComplete(completedSetsByMove[position] ?: 0,plan)
                }
                displayStep()
                remainingMs=checkpoint.getLong("remaining",remainingMs).coerceIn(0L,600_000L)
                tenSecondCueGiven=checkpoint.getBoolean("tenSecondCueGiven",remainingMs<=10_000L)
                pausedForSafety=checkpoint.getBoolean("safety",false)
                resting=checkpoint.getBoolean("resting",false)
                restRemainingMs=checkpoint.getLong("restRemaining",0L).coerceIn(0L,600_000L)
                restDeadline=0L
                setTimerExpired=checkpoint.getBoolean("setTimerExpired",remainingMs<=0L)
                renderSetControls()
                val elapsed=checkpoint.getLong("elapsed",0L).coerceIn(0L,86_400_000L)
                accumulated=elapsed
                startedAt=0L
                val seconds=(remainingMs/1000).toInt()
                counter.text=if(resting) restCounterText(restRemainingMs)
                    else String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
                control.text=when {
                    pausedForSafety -> "⚠️ Đã dừng vì đau"
                    resting -> "▶ Tiếp tục nghỉ"
                    else -> "▶ Tiếp tục"
                }
            }
            .setNegativeButton("Bắt đầu lại") { _,_ ->
                // Starting over must not silently erase a previously reported pain lock.
                val keepSafetyLock=checkpoint.getBoolean("safety",false)
                checkpointDecisionPending=false
                sessionId=UUID.randomUUID().toString()
                completedMoveIndices.clear()
                completedSetsByMove.clear()
                repTargets.clear()
                substitutions.clear()
                moves=originalMoves
                index=0
                shorterFromIndex=Int.MAX_VALUE
                accumulated=0L
                startedAt=0L
                remainingMs=0L
                resting=false
                restRemainingMs=0L
                restDeadline=0L
                setTimerExpired=false
                pausedForSafety=keepSafetyLock
                checkpoint.edit().clear().apply()
                displayStep()
                if(pausedForSafety) {
                    control.text="⚠️ Đã dừng vì đau"
                    saveCheckpoint()
                }
            }
            .setCancelable(false).show()
    }

    private val videoPrefs by lazy { getSharedPreferences("workout_videos",0) }
    private val pickVideoRequest=889
    private val voiceRequest=890
    private val tick=object:Runnable {
        override fun run() {
            if(paused)return
            if(resting) {
                val left=(restDeadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                restRemainingMs=left
                counter.text=restCounterText(left)
                if(left==0L) {
                    resting=false
                    paused=true
                    restRemainingMs=0L
                    renderSetControls()
                    control.text="▶ Tiếp tục hiệp"
                    counter.text=clockText(remainingMs)
                    saveCheckpoint()
                    speak("Đã hết giờ nghỉ. Tiếp tục khi bạn sẵn sàng.")
                } else handler.postDelayed(this,300)
                return
            }
            val left=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs=left
            if (left in 1L..10_000L && !tenSecondCueGiven && !pausedForSafety) {
                tenSecondCueGiven=true
                val upcoming = moves.getOrNull(index + 1)?.name
                speak(if (upcoming != null) "Còn mười giây. Tiếp theo: $upcoming." else "Còn mười giây. Sắp hoàn thành buổi tập.")
            }
            val seconds=(left/1000).toInt()
            counter.text=String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
            if(left==0L && currentSetPlan()!=null) {
                // A timebox is only a reminder for rep-based exercises; it cannot prove sets were done.
                if(!setTimerExpired) {
                    setTimerExpired=true
                    renderSetControls()
                    Toast.makeText(this@GuidedWorkoutActivity,
                        "Hết thời gian gợi ý. Hãy xác nhận từng hiệp sau khi tập, hoặc bỏ qua mà không nhận tiến độ.",
                        Toast.LENGTH_LONG).show()
                    speak("Đã hết thời gian gợi ý. Hãy hoàn thành các hiệp trong khả năng của bạn.")
                    saveCheckpoint()
                }
                handler.postDelayed(this,1000)
            } else if(left==0L) nextMove(completedByTimer=true)
            else handler.postDelayed(this,300)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val baseProgram = TrainingCatalog.byId(intent.getStringExtra("program") ?: "starter")
        workoutConstraints = WorkoutConstraintProfile(
            avoidWristLoad = intent.getBooleanExtra("avoid_wrist_load", false) ||
                savedInstanceState?.getBoolean("workout_avoid_wrist_load", false) == true,
            avoidFloorPositions = intent.getBooleanExtra("avoid_floor_positions", false) ||
                savedInstanceState?.getBoolean("workout_avoid_floor_positions", false) == true
        )
        val constraintResult = WorkoutConstraintFilter.apply(baseProgram, workoutConstraints)
        if (!constraintResult.canStart) {
            Toast.makeText(this, "Chưa có động tác chính phù hợp với bộ lọc đã chọn.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        program = constraintResult.program
        val level=intent.getIntExtra("level",0).coerceIn(0,2)
        workoutLevel = level
        val minutes=WorkoutDurationChoices.guidedMinutes(intent.getIntExtra("minutes",15))
        val extensionPreferences = WorkoutExtensionPreferences(
            goal = intent.getIntExtra("extension_goal", 0).coerceIn(0, 5),
            energy = intent.getIntExtra("extension_energy", 1).coerceIn(0, 2),
            equipment = intent.getIntExtra("extension_equipment", 0).coerceIn(0, 2),
            impact = intent.getIntExtra("extension_impact", 0).coerceIn(0, 2),
            constraints = workoutConstraints
        )
        val planned=WorkoutDurationPlanner.build(program,level,minutes,extensionPreferences)
        moves=planned.moves
        originalMoves=moves.toList()
        optionalExtensionNames.clear()
        optionalExtensionNames.addAll(planned.extensionMoves.map { it.name })
        val restoredPlanNames = savedInstanceState?.getString("workout_plan_names")
        if (!restoredPlanNames.isNullOrBlank()) {
            WorkoutMidSessionPlanner.decodePlan(restoredPlanNames)?.let { restoredPlan ->
                originalMoves = restoredPlan
                moves = restoredPlan
                program = program.copy(moves = restoredPlan)
            }
        }
        savedInstanceState?.getStringArrayList("workout_extension_names")?.let { names ->
            optionalExtensionNames.clear()
            optionalExtensionNames.addAll(names.filter { name -> moves.any { it.name == name } }.take(20))
        }
        val root=LinearLayout(this).apply {
            orientation=1;setPadding(24,27,24,24);setBackgroundColor(Color.rgb(247,240,252))
        }
        root.addView(TextView(this).apply {
            text="🐰 Chibi Coach • Tập có hướng dẫn";textSize=22f
            setTextColor(Color.rgb(95,55,113))
        })
        root.addView(TextView(this).apply { text=program.name;textSize=16f })
        root.addView(TextView(this).apply {
            text = "Mục tiêu tối đa: ${planned.targetMinutes} phút • ước tính theo bộ đếm: ${planned.estimatedMinutes} phút.\n" +
                "Ước tính đã cộng thời gian nghỉ giữa hiệp theo giáo án; thời gian thực tế còn tùy tốc độ thực hiện và thời gian nghỉ thêm."
            textSize = 12f
            setPadding(4, 4, 4, 10)
            if (planned.exceedsTarget) {
                append("\nLưu ý: chương trình tối thiểu dài hơn thời lượng đã chọn.")
            }
        })
        if (constraintResult.notice.isNotBlank()) root.addView(TextView(this).apply {
            text = constraintResult.notice
            textSize = 13f
            setPadding(4, 6, 4, 10)
        })
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
        setProgress=TextView(this).apply {
            textSize=15f
            gravity=Gravity.CENTER
            setPadding(8,8,8,8)
            setTextColor(Color.rgb(75,55,95))
        }
        root.addView(setProgress)
        completeSetButton=Button(this).apply {
            text="✓ Xác nhận hoàn thành hiệp"
            contentDescription="Tự xác nhận đã thực hiện xong hiệp hiện tại; không phải cảm biến đếm lần tập"
            setOnClickListener { completeCurrentSet() }
        }
        root.addView(completeSetButton)
        restSetButton=Button(this).apply {
            text="⏱ Nghỉ theo giáo án"
            setOnClickListener { currentSetPlan()?.let { startSetRest(it.defaultRestSeconds) } }
        }
        root.addView(restSetButton)
        customSetRestButton=Button(this).apply {
            text="Tùy chỉnh thời gian nghỉ"
            setOnClickListener { requestSetRest() }
        }
        root.addView(customSetRestButton)
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
            text="🔢 Chỉnh số lần tập động tác hiện tại"
            contentDescription="Đặt mục tiêu số lần cho bước hiện tại, xem trước và xác nhận"
            setOnClickListener { requestRepTarget() }
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
        val savedPlanFingerprint = savedInstanceState?.getString("workout_plan_fingerprint")
        val savedPlanCompatible = savedPlanFingerprint == currentPlanFingerprint() ||
            (savedPlanFingerprint == null && !workoutConstraints.avoidWristLoad && !workoutConstraints.avoidFloorPositions)
        if(savedInstanceState!=null &&
            savedInstanceState.getString("workout_program")==program.id &&
            savedPlanCompatible) {
            sessionId=validSessionId(savedInstanceState.getString("workout_session_id")) ?: sessionId
            val savedSubstitutions=savedInstanceState.getString("workout_substitutions")
            substitutions.clear()
            substitutions.putAll(WorkoutMoveSubstitution.decode(savedSubstitutions,originalMoves,workoutConstraints))
            moves=WorkoutMoveSubstitution.apply(originalMoves,savedSubstitutions,workoutConstraints)
            index=savedInstanceState.getInt("workout_index",0).coerceIn(0,moves.lastIndex)
            repTargets.clear()
            repTargets.putAll(WorkoutRepTargets.decode(savedInstanceState.getString("workout_rep_targets"),moves.size,index))
            completedMoveIndices.clear()
            completedMoveIndices.addAll(WorkoutCompletionTracker.decode(savedInstanceState.getString("workout_completed_moves"),moves.size))
            completedSetsByMove.clear()
            completedSetsByMove.putAll(WorkoutSetTracker.decode(savedInstanceState.getString("workout_completed_sets"),moves))
            completedMoveIndices.removeAll { position ->
                val plan=setPlan(position)
                plan!=null && !WorkoutSetTracker.isComplete(completedSetsByMove[position] ?: 0,plan)
            }
            displayStep()
            remainingMs=savedInstanceState.getLong("workout_remaining",remainingMs)
                .coerceIn(0L,600_000L)
            tenSecondCueGiven=savedInstanceState.getBoolean("workout_ten_second_cue",remainingMs<=10_000L)
            pausedForSafety=savedInstanceState.getBoolean("workout_safety",false)
            resting=savedInstanceState.getBoolean("workout_resting",false)
            restRemainingMs=savedInstanceState.getLong("workout_rest_remaining",0L).coerceIn(0L,600_000L)
            restDeadline=0L
            setTimerExpired=savedInstanceState.getBoolean("workout_set_timer_expired",remainingMs<=0L)
            shorterFromIndex=savedInstanceState.getInt("workout_shorter_from",Int.MAX_VALUE).coerceIn(0,moves.size)
            renderSetControls()
            val elapsed=savedInstanceState.getLong("workout_elapsed",0L).coerceAtLeast(0L)
            accumulated=elapsed.coerceAtMost(86_400_000L)
            startedAt=0L
            val seconds=(remainingMs/1000).toInt()
            counter.text=if(resting) restCounterText(restRemainingMs)
                else String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
            control.text=when {
                pausedForSafety -> "⚠️ Đã dừng vì đau"
                resting -> "▶ Tiếp tục nghỉ"
                else -> "▶ Tiếp tục"
            }
        } else if (savedInstanceState != null &&
            savedInstanceState.getString("workout_program") == program.id) {
            Toast.makeText(this, "Giáo án đã đổi; không khôi phục tiến độ của kế hoạch khác.", Toast.LENGTH_LONG).show()
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        // Save monotonic elapsed duration, not an absolute deadline.
        pause()
        outState.putString("workout_program",program.id)
        outState.putString("workout_plan_names",WorkoutMidSessionPlanner.encodePlan(originalMoves))
        outState.putBoolean("workout_avoid_wrist_load",workoutConstraints.avoidWristLoad)
        outState.putBoolean("workout_avoid_floor_positions",workoutConstraints.avoidFloorPositions)
        outState.putStringArrayList("workout_extension_names",ArrayList(optionalExtensionNames))
        outState.putString("workout_plan_fingerprint",currentPlanFingerprint())
        outState.putString("workout_session_id",sessionId)
        outState.putString("workout_rep_targets",repTargets.entries.joinToString(",") { "${it.key}:${it.value}" })
        outState.putString("workout_substitutions",WorkoutMoveSubstitution.encode(substitutions,originalMoves,workoutConstraints))
        outState.putString("workout_completed_moves",WorkoutCompletionTracker.encode(completedMoveIndices,moves.size))
        outState.putString("workout_completed_sets",WorkoutSetTracker.encode(completedSetsByMove,moves))
        outState.putBoolean("workout_resting",resting)
        outState.putLong("workout_rest_remaining",restRemainingMs.coerceIn(0L,600_000L))
        outState.putBoolean("workout_set_timer_expired",setTimerExpired)
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
                    putExtra(RecognizerIntent.EXTRA_PROMPT,"Ví dụ: hướng dẫn từng bước, nghỉ thêm, đổi bài, không dùng tạ/dây, dễ hơn, dừng lại")
                }
                try { startActivityForResult(request,voiceRequest) }
                catch (_:Exception) { Toast.makeText(this,"Thiết bị chưa có dịch vụ nhận giọng nói. Hãy nhập yêu cầu bằng chữ.",Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Hủy",null).show()
    }
    private fun requestTypedCommand() {
        val field=EditText(this).apply { hint="Ví dụ: hướng dẫn từng bước, đọc tiến độ, còn 10 phút, đổi bài, nghỉ thêm";setSingleLine(false) }
        AlertDialog.Builder(this).setTitle("💬 Chibi Coach • lệnh offline")
            .setMessage("Điều khiển buổi tập bằng chữ, không gửi dữ liệu lên mạng.")
            .setView(field).setPositiveButton("Gửi"){_,_->handleCoachCommand(field.text.toString())}
            .setNegativeButton("Hủy",null).show()
    }
    /** Spoken repetition changes use the same preview/confirmation as manual edits. */
    private fun confirmSpokenRepTarget(reps: Int) {
        if (pausedForSafety || completed || index !in moves.indices) return
        if ((completedSetsByMove[index] ?: 0) > 0) {
            Toast.makeText(this, "Đã ghi nhận hiệp; không sửa lại mục tiêu của động tác này.", Toast.LENGTH_LONG).show()
            return
        }
        pause()
        val selected = index
        val before = repTargets[selected]
        val previewRemainingMs = remainingMs
        val oldDose = targetDose(selected)
        AlertDialog.Builder(this).setTitle("🎯 Xác nhận số lần tập")
            .setMessage("${moves[selected].name}: $oldDose → $reps lần.\nChỉ thay đổi hướng dẫn, không tự đếm lần tập.")
            .setPositiveButton("Áp dụng") { _, _ ->
                if (pausedForSafety || completed || index != selected ||
                    repTargets[selected] != before || remainingMs != previewRemainingMs) {
                    Toast.makeText(this, "Buổi tập đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                } else {
                    repTargets[selected] = reps
                    renderDose()
                    renderSetControls()
                    saveCheckpoint()
                    speak("Đã đổi mục tiêu thành $reps lần. Tập trong khả năng của bạn.")
                }
            }.setNegativeButton("Giữ nguyên", null).show()
    }

    /** Voice recognition is fallible: confirm before recording a self-reported set. */
    private fun confirmSpokenSetCompletion() {
        if (pausedForSafety || completed || resting || index !in moves.indices) {
            Toast.makeText(this, "Buổi tập đang dừng; chưa thể ghi nhận hiệp.", Toast.LENGTH_LONG).show()
            return
        }
        val plan = currentSetPlan()
        if (plan == null) {
            Toast.makeText(this, "Động tác này không dùng bộ đếm hiệp.", Toast.LENGTH_LONG).show()
            return
        }
        if (activeElapsedMs() <= 0L) {
            Toast.makeText(this, "Hãy bắt đầu buổi tập trước khi ghi nhận hiệp.", Toast.LENGTH_LONG).show()
            return
        }
        val selectedIndex = index
        val selectedSessionId = sessionId
        val before = completedSetsByMove[selectedIndex] ?: 0
        if (before >= plan.maximumSets) {
            Toast.makeText(this, "Các hiệp của động tác này đã được ghi nhận đủ.", Toast.LENGTH_LONG).show()
            return
        }
        pause()
        val moveName = moves[selectedIndex].name
        AlertDialog.Builder(this)
            .setTitle("✓ Xác nhận hoàn thành hiệp")
            .setMessage(
                "Bạn xác nhận đã thực hiện xong hiệp ${before + 1}/${plan.maximumSets} của $moveName?\n\n" +
                    "Chibi chỉ ghi nhận lời xác nhận của bạn; không tự đếm lần tập hoặc xác minh bằng camera."
            )
            .setPositiveButton("Đúng, ghi nhận") { _, _ ->
                if (pausedForSafety || completed || resting || index != selectedIndex ||
                    sessionId != selectedSessionId ||
                    (completedSetsByMove[selectedIndex] ?: 0) != before
                ) {
                    Toast.makeText(this, "Buổi tập đã thay đổi. Chibi chưa ghi nhận hiệp.", Toast.LENGTH_LONG).show()
                } else {
                    completeCurrentSet()
                }
            }
            .setNegativeButton("Chưa, giữ nguyên", null)
            .show()
    }

    private fun stopForSafety() {
        pause()
        pausedForSafety=true
        renderSetControls()
        control.text="⚠️ Đã dừng vì đau"
        saveCheckpoint()
        AlertDialog.Builder(this).setTitle("⚠️ Đã dừng vì bạn báo đau")
            .setMessage("Không cố tập khi đau. Hãy kết thúc buổi tập nếu triệu chứng tiếp diễn; tìm trợ giúp y tế phù hợp nếu đau nhiều hoặc có dấu hiệu bất thường. Chibi sẽ không tự tiếp tục bằng giọng nói.")
            .setPositiveButton("Đã hiểu",null).show()
        speak("Đã dừng tập vì bạn báo đau. Hãy ưu tiên an toàn.")
    }

    private fun applyMidSessionRequest(request: WorkoutMidSessionRequest.Request) {
        if (pausedForSafety || completed || resting || index !in moves.indices) {
            Toast.makeText(this,"Buổi tập đang dừng hoặc chưa thể điều chỉnh lúc này.",Toast.LENGTH_LONG).show()
            return
        }
        if (completedMoveIndices.any { it > index } || completedSetsByMove.keys.any { it > index }) {
            Toast.makeText(this,"Đã có hiệp ở bước phía sau được ghi nhận. Hãy quay tới bước cuối trước khi đổi phần còn lại.",Toast.LENGTH_LONG).show()
            return
        }

        pause()
        val beforeMoves = moves.toList()
        val beforeFingerprint = currentPlanFingerprint()
        val beforeIndex = index
        val beforeRemaining = remainingMs
        val beforeCompletedSets = completedSetsByMove[index] ?: 0
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = beforeMoves,
            currentIndex = beforeIndex,
            completedCurrentSets = beforeCompletedSets,
            currentStepLocked = beforeIndex in completedMoveIndices,
            constraints = workoutConstraints,
            request = request,
            remainingCurrentSeconds = ((beforeRemaining + 999L) / 1000L).coerceIn(0L, 3_600L).toInt(),
            extensionMoveNames = optionalExtensionNames,
            level = workoutLevel
        )

        when (result) {
            is WorkoutMidSessionPlanner.Result.Blocked -> {
                AlertDialog.Builder(this).setTitle("Chibi chưa thể đổi giáo án an toàn")
                    .setMessage(result.reason)
                    .setPositiveButton("Đã hiểu",null).show()
            }
            WorkoutMidSessionPlanner.Result.Unsupported -> {
                Toast.makeText(this,"Chibi chưa tìm được thay đổi phù hợp. Giáo án vẫn được giữ nguyên.",Toast.LENGTH_LONG).show()
            }
            is WorkoutMidSessionPlanner.Result.Proposed -> {
                val preview = result.preview
                fun durationLabel(seconds: Int): String =
                    "${seconds / 60} phút ${seconds % 60} giây"
                val summary = buildString {
                    if (preview.replacements.isNotEmpty()) {
                        append("Thay động tác:\n")
                        preview.replacements.forEach { append("• ").append(it).append("\n") }
                    }
                    if (preview.removedMoves.isNotEmpty()) {
                        append("\nBỏ các bước chưa bắt đầu:\n")
                        preview.removedMoves.forEach { append("• ").append(it).append("\n") }
                    }
                    if (request.avoidWristLoad) {
                        append("\nÁp dụng tránh chống tay/tải qua cổ tay cho phần chưa hoàn thành.")
                    }
                    if (request.allowWristLoad) {
                        append("\nBỏ hạn chế chống tay/tải qua cổ tay cho phần chưa hoàn thành; các hiệp đã ghi nhận vẫn giữ nguyên.")
                    }
                    if (request.avoidFloorPositions) {
                        append("\nBỏ các động tác phải nằm hoặc quỳ sàn chưa bắt đầu; không tự tạo bài thay thế chưa được tuyển chọn.")
                    }
                    if (request.allowFloorPositions) {
                        append("\nBỏ hạn chế nằm/quỳ sàn cho phần chưa hoàn thành; các hiệp đã ghi nhận vẫn giữ nguyên.")
                    }
                    if (request.avoidExternalLoad) {
                        append("\nLoại bỏ tạ và dây kháng lực khỏi phần chưa hoàn thành; chỉ dùng biến thể đã có trong thư viện.")
                    }
                    if (request.lowEnergy) {
                        append("\nƯu tiên biến thể nhẹ hơn và nghỉ riêng.")
                    }
                    if (preview.requestedMinutes != null) {
                        append("\nMục tiêu thời gian còn lại: ").append(preview.requestedMinutes).append(" phút.")
                        append("\nƯớc tính sau điều chỉnh: ").append(durationLabel(preview.estimatedRemainingSeconds)).append(".")
                    }
                    if (preview.targetExceeded) {
                        append("\nPhần bài chính và nghỉ bắt buộc vẫn dài hơn mục tiêu. Chibi không tự cắt hiệp bắt buộc hoặc hứa chắc thời gian hoàn thành.")
                    }
                    if (preview.restSeconds > 0) {
                        append("\nSau khi áp dụng sẽ nghỉ riêng ").append(preview.restSeconds).append(" giây; thời gian nghỉ không tính là vận động.")
                    }
                    if (!preview.changed && preview.restSeconds == 0) {
                        append("\nKhông có bài tùy chọn nào cần bỏ; các hiệp bắt buộc được giữ nguyên.")
                    }
                    append("\n\nCác hiệp đã xác nhận không bị sửa. Đây là gợi ý offline từ thư viện, không phải chẩn đoán hay đánh giá tư thế.")
                }
                AlertDialog.Builder(this).setTitle("🐰 Xem trước điều chỉnh giữa buổi")
                    .setMessage(summary)
                    .setPositiveButton("Áp dụng") { _, _ ->
                        if (pausedForSafety || completed || resting || index != beforeIndex ||
                            moves != beforeMoves || currentPlanFingerprint() != beforeFingerprint ||
                            remainingMs != beforeRemaining ||
                            (completedSetsByMove[index] ?: 0) != beforeCompletedSets
                        ) {
                            Toast.makeText(this,"Buổi tập đã thay đổi. Hãy xem trước lại.",Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }

                        moves = preview.after
                        originalMoves = moves.toList()
                        program = program.copy(moves = moves)
                        substitutions.clear()
                        optionalExtensionNames.clear()
                        optionalExtensionNames.addAll(preview.extensionMoveNames)
                        completedMoveIndices.removeAll {
                            it > index || (it == index && preview.currentMoveChanged)
                        }
                        completedSetsByMove.keys.removeAll {
                            it > index || (it == index && preview.currentMoveChanged)
                        }
                        repTargets.keys.removeAll {
                            it > index || (it == index && preview.currentMoveChanged)
                        }
                        workoutConstraints = WorkoutMidSessionPlanner.effectiveConstraints(
                            workoutConstraints,
                            request
                        )
                        if (preview.currentMoveChanged) {
                            remainingMs = minOf(
                                remainingMs,
                                moves[index].seconds.coerceAtLeast(0) * 1_000L
                            )
                        }
                        heading.text = "${index + 1}/${moves.size} • ${moves[index].name}"
                        poseView.pose = WorkoutPoseGuide.classify(moves[index].name)
                        renderDose()
                        renderSetControls()
                        progress.max = moves.size
                        progress.progress = index
                        counter.text = clockText(remainingMs)
                        control.text = "▶ Tiếp tục"
                        saveCheckpoint()

                        val cue = if (preview.currentMoveChanged) {
                            "Đã đổi sang ${moves[index].name}. Hãy kiểm tra hướng dẫn trước khi tập tiếp."
                        } else {
                            "Đã cập nhật phần còn lại của giáo án. Các hiệp đã xác nhận được giữ nguyên."
                        }
                        if (preview.restSeconds > 0) startSetRest(preview.restSeconds, cue)
                        else speak(cue)
                    }
                    .setNegativeButton("Giữ nguyên",null)
                    .show()
            }
        }
    }

    private fun handleCoachCommand(utterance:String) {
        when (val parsed = WorkoutMidSessionRequest.parse(utterance)) {
            WorkoutMidSessionRequest.Result.SafetyStop -> {
                stopForSafety()
                return
            }
            is WorkoutMidSessionRequest.Result.Proposed -> {
                applyMidSessionRequest(parsed.request)
                return
            }
            WorkoutMidSessionRequest.Result.Unsupported -> Unit
        }
        // Safety words always take precedence over any numeric exercise request.
        val spokenReps = com.chibialarm.app.fitness.WorkoutTargetCommand.spokenReps(utterance)
        if (spokenReps != null && WorkoutVoiceCommands.mayEditReps(utterance)) {
            confirmSpokenRepTarget(spokenReps)
            return
        }

        when(WorkoutVoiceCommands.parse(utterance)) {
            WorkoutVoiceCommands.Command.PAIN -> stopForSafety()
            WorkoutVoiceCommands.Command.PAUSE -> { pause();speak("Đã tạm dừng.") }
            WorkoutVoiceCommands.Command.STATUS -> {
                val plan = currentSetPlan()
                val secondsLeft = { millis: Long ->
                    ((millis.coerceAtLeast(0L) + 999L) / 1000L).coerceIn(0L, 600L).toInt()
                }
                val setProgress = plan?.let {
                    WorkoutContextCoach.SetProgress(
                        completed = (completedSetsByMove[index] ?: 0).coerceIn(0, it.maximumSets),
                        required = it.requiredSets,
                        maximum = it.maximumSets,
                        targetMin = it.targetMin,
                        targetMax = it.targetMax,
                        unit = it.unit
                    )
                }
                speak(WorkoutContextCoach.progressScript(
                    WorkoutContextCoach.Progress(
                        completedMoves = completedMoveIndices.count { it in moves.indices },
                        totalMoves = moves.size,
                        currentMoveIndex = index,
                        currentMoveName = moves[index].name,
                        sets = setProgress,
                        resting = resting,
                        restSecondsRemaining = if (resting) secondsLeft(restRemainingMs) else 0,
                        exerciseSecondsRemaining = if (resting) 0 else secondsLeft(remainingMs),
                        pausedForSafety = pausedForSafety,
                        nextMoveName = moves.getOrNull(index + 1)?.name,
                        estimatedSessionSecondsRemaining = if (pausedForSafety) null else
                            WorkoutSessionTimeEstimate.seconds(
                                moves = moves,
                                currentIndex = index,
                                completedMoves = completedMoveIndices,
                                completedSets = completedSetsByMove,
                                currentExerciseRemainingSeconds = secondsLeft(remainingMs),
                                resting = resting,
                                restSecondsRemaining = if (resting) secondsLeft(restRemainingMs) else 0,
                                level = workoutLevel
                            )
                    )
                ))
            }
            WorkoutVoiceCommands.Command.STEP_GUIDE -> showStepByStepGuide()
            WorkoutVoiceCommands.Command.COMPLETE_SET -> confirmSpokenSetCompletion()
            WorkoutVoiceCommands.Command.RESUME -> resume()
            WorkoutVoiceCommands.Command.NEXT -> nextMove()
            WorkoutVoiceCommands.Command.PREVIOUS -> { if(!pausedForSafety) { pause();if(index>0)index--;displayStep();speak("Bài "+moves[index].name) } }
            WorkoutVoiceCommands.Command.REPEAT -> speak(moves[index].steps)
            WorkoutVoiceCommands.Command.EASIER_AND_REST -> {
                if (pausedForSafety || completed || resting || index !in moves.indices) {
                    Toast.makeText(this,"Buổi tập đang dừng hoặc không thể điều chỉnh.",Toast.LENGTH_LONG).show()
                } else {
                    // Confirm the combined action before starting a separate rest countdown.
                    pause()
                    val addedSeconds=WorkoutRestRequest.seconds(utterance)
                    val previewIndex=index
                    val originalMs=remainingMs
                    val exerciseName=moves[index].name
                    val easierCue=moves[index].easier
                    AlertDialog.Builder(this).setTitle("🐰 Xác nhận điều chỉnh buổi tập")
                        .setMessage("Bạn muốn nghỉ riêng $addedSeconds giây và xem cách tập nhẹ hơn cho $exerciseName? " +
                            "Bộ đếm động tác còn lại (${originalMs / 1000} giây) sẽ được giữ nguyên; thời gian nghỉ không tính vào vận động." +
                            "\n\n$easierCue" +
                            "\n\nChibi chỉ gợi ý cách tập, không tự đổi số hiệp hoặc xác minh kỹ thuật.")
                        .setPositiveButton("Đồng ý điều chỉnh") { _, _ ->
                            if (pausedForSafety || completed || resting || index != previewIndex ||
                                remainingMs != originalMs || index !in moves.indices) {
                                Toast.makeText(this, "Buổi tập đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                                return@setPositiveButton
                            }
                            startSetRest(addedSeconds, "Cách tập nhẹ hơn: $easierCue")
                        }
                        .setNegativeButton("Giữ nguyên",null)
                        .show()
                }
            }
            WorkoutVoiceCommands.Command.SWAP_EASIER -> chooseEasierAlternative()
            WorkoutVoiceCommands.Command.EASIER -> {
                pause()
                AlertDialog.Builder(this).setTitle("🌿 Cách tập dễ hơn")
                    .setMessage(moves[index].easier+"\n\nChỉ tiếp tục khi bạn thấy thoải mái.")
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
                    .setMessage("Bạn có thể nói: hướng dẫn từng bước, đọc tiến độ, tạm dừng, tiếp tục, bài tiếp theo, bài trước, đổi động tác, nhắc lại, dễ hơn, rút ngắn hoặc nghỉ thêm. Đây là bộ lệnh offline, chưa phải hội thoại AI tự do.")
                    .setPositiveButton("Đóng",null).show()
            }
        }
    }
    /**
     * Spoken rest requests are proposals, not immediate timer mutations.
     * Guard against stale dialogs after moving to another exercise or changing the timer.
     */
    /**
     * Spoken/manual rest is a separate countdown. It pauses the exercise timer
     * without adding rest seconds to the active-exercise duration.
     */
    /**
     * Spoken/manual rest is a separate countdown. It pauses the exercise timer
     * without adding rest seconds to the active-exercise duration.
     */
    private fun confirmExtraRest(addedSeconds: Int) {
        if (pausedForSafety || completed || resting || index !in moves.indices) return
        val seconds = addedSeconds.coerceIn(1, 600)
        pause()
        val originalMs = remainingMs
        val originalIndex = index
        AlertDialog.Builder(this).setTitle("Xác nhận nghỉ riêng")
            .setMessage("Động tác: ${moves[originalIndex].name}\n" +
                "Thời gian động tác còn lại: ${originalMs / 1000} giây.\n" +
                "Chibi sẽ chạy bộ đếm nghỉ riêng $seconds giây. Thời gian động tác được giữ nguyên; thời gian nghỉ không tính vào phút vận động.")
            .setPositiveButton("Bắt đầu nghỉ") { _, _ ->
                if (pausedForSafety || completed || resting || index != originalIndex ||
                    remainingMs != originalMs || index !in moves.indices) return@setPositiveButton
                startSetRest(seconds)
            }
            .setNegativeButton("Giữ nguyên", null)
            .show()
    }

    /** Repetition guidance for the current step, without pretending to count actual reps. */
    private fun requestRepTarget() {
        if (pausedForSafety || completed || index !in moves.indices) return
        if ((completedSetsByMove[index] ?: 0) > 0) {
            Toast.makeText(this, "Đã ghi nhận hiệp; không sửa lại mục tiêu của động tác này.", Toast.LENGTH_LONG).show()
            return
        }
        pause()
        val selected=index
        val before=repTargets[selected]
        val field=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            hint="Số lần từ 1 đến 100"
            setText(before?.toString() ?: "8")
            selectAll()
        }
        val dialog=AlertDialog.Builder(this).setTitle("🔢 Chọn số lần tập")
            .setMessage("Chỉ thay đổi mục tiêu hướng dẫn của bước hiện tại; Chibi chưa tự đếm số lần tập thực tế.")
            .setView(field).setPositiveButton("Xem trước",null)
            .setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reps=field.text.toString().trim().toIntOrNull()
                if (reps==null || reps !in 1..100) field.error="Nhập từ 1 đến 100 lần"
                else {
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Xác nhận số lần tập")
                        .setMessage("${moves[selected].name}\n${targetDose(selected)} → $reps lần.\nKhông sửa các bước đã hoàn thành hoặc thời gian đếm ngược.")
                        .setPositiveButton("Áp dụng") { _, _ ->
                            if (completed || pausedForSafety || index!=selected || repTargets[selected]!=before) {
                                Toast.makeText(this,"Buổi tập đã thay đổi. Hãy xem trước lại.",Toast.LENGTH_LONG).show()
                            } else {
                                repTargets[selected]=reps
                                renderDose()
                                renderSetControls()
                                saveCheckpoint()
                                speak("Mục tiêu động tác hiện tại là $reps lần. Tập trong khả năng của bạn.")
                            }
                        }.setNegativeButton("Giữ nguyên",null).show()
                }
            }
        }
        dialog.show()
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
            "⏱ Rút ngắn tất cả động tác còn lại",
            "🔄 Đổi sang động tác nhẹ hơn"
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
                            .setMessage(moves[index].easier+"\n\nDừng tập nếu thấy đau.")
                            .setPositiveButton("Đã hiểu",null).show()
                        speak(moves[index].easier)
                    }
                    4 -> { pause();speak("Đã tạm dừng buổi tập.") }
                    5 -> shortenRemainingWorkout()
                    6 -> chooseEasierAlternative()
                }
            }.setNegativeButton("Đóng",null).show()
    }
    /** Replace only the current, not-yet-completed step with a curated easier catalog move. */
    private fun chooseEasierAlternative() {
        if (pausedForSafety) {
            Toast.makeText(this, "Đã dừng vì đau. Không tự đổi bài để tiếp tục tập.", Toast.LENGTH_LONG).show()
            return
        }
        if (completed || resting || index !in moves.indices) return
        if ((completedSetsByMove[index] ?: 0) > 0 || index in completedMoveIndices) {
            Toast.makeText(this, "Động tác đã ghi nhận hiệp hoặc hoàn thành; không thể thay thế hồi tố.", Toast.LENGTH_LONG).show()
            return
        }
        if (index in substitutions) {
            Toast.makeText(this, "Động tác này đã được thay thế trong phiên. Giữ thay đổi hiện tại để bảo toàn tiến độ.", Toast.LENGTH_LONG).show()
            return
        }
        val options = WorkoutMoveSubstitution.alternativesFor(moves[index],workoutConstraints)
        if (options.isEmpty()) {
            Toast.makeText(this, "Chưa có phương án thay thế được kiểm duyệt cho động tác này.", Toast.LENGTH_LONG).show()
            return
        }
        pause()
        val selectedIndex = index
        val original = moves[selectedIndex]
        AlertDialog.Builder(this).setTitle("🔄 Đổi động tác hiện tại")
            .setItems(options.map { "${it.name} • ${it.dose}" }.toTypedArray()) { _, choice ->
                val replacement = options.getOrNull(choice) ?: return@setItems
                val restGuidance = WorkoutSetTracker.restGuidance(replacement.dose, replacement.restSeconds)
                    ?.let { "$it\n" }.orEmpty()
                AlertDialog.Builder(this).setTitle("Xác nhận thay động tác")
                    .setMessage("${original.name} → ${replacement.name}\n\nMục tiêu: ${replacement.dose}\n${restGuidance}Cách tập: ${replacement.steps}\nDễ hơn nữa: ${replacement.easier}\nLưu ý: ${replacement.avoid}\n\nChỉ thay bước hiện tại chưa được ghi nhận hoàn thành. Các bước khác và thời gian còn lại được giữ nguyên. Chỉ chọn nếu phù hợp với cơ thể bạn; đây là gợi ý offline, không phải tư vấn điều trị.")
                    .setPositiveButton("Đổi động tác") { _, _ ->
                        if (pausedForSafety || completed || resting || index != selectedIndex ||
                            index !in moves.indices || (completedSetsByMove[index] ?: 0) > 0 ||
                            index in completedMoveIndices || index in substitutions
                        ) {
                            Toast.makeText(this, "Buổi tập đã thay đổi. Hãy mở lại phần điều chỉnh.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        val updated = WorkoutMoveSubstitution.replace(moves, selectedIndex, replacement, workoutConstraints)
                        if (updated == null) {
                            Toast.makeText(this, "Phương án không còn hợp lệ. Giữ nguyên động tác.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        moves = updated
                        substitutions[selectedIndex] = replacement
                        repTargets.remove(selectedIndex)
                        heading.text = "${selectedIndex + 1}/${moves.size} • ${replacement.name}"
                        poseView.pose = WorkoutPoseGuide.classify(replacement.name)
                        renderDose()
                        renderSetControls()
                        saveCheckpoint()
                        speak("Đã đổi sang ${replacement.name}. Thời gian còn lại được giữ nguyên. Hãy kiểm tra hướng dẫn trước khi tiếp tục.")
                    }
                    .setNegativeButton("Giữ bài cũ", null)
                    .show()
            }
            .setNegativeButton("Đóng", null)
            .show()
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
        val details = stepPreview.take(4).joinToString("\n") { step ->
            "• ${step.name}: ${step.beforeSeconds}s → ${step.afterSeconds}s"
        } + if (stepPreview.size > 4) "\n… và ${stepPreview.size - 4} bước tiếp theo" else ""

        AlertDialog.Builder(this).setTitle("Xem trước giáo án rút ngắn")
            .setMessage("Thời gian còn lại dự kiến:\nTrước: ${format(before)}\nSau: ${format(after)}\n\n" +
                "Giảm thời lượng động tác hiện tại và các động tác phía sau. " +
                "Những bước đã hoàn thành được giữ nguyên. Đây là ước tính, không bao gồm nghỉ thêm.\n\n" + details)
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
                        .setMessage("“"+phrase.take(160)+"”\n\n"+WorkoutVoiceCommands.describe(command, phrase)+
                            "\n\nKiểm tra câu nhận dạng trước khi điều khiển buổi tập.")
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
        if(local==null && remote.isNullOrBlank()) {
            Toast.makeText(this,"Chưa có video. Hãy lưu link hoặc chọn video từ máy.",Toast.LENGTH_LONG).show()
            return
        }
        try {
            val intent = when {
                local != null -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(local),"video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                remote != null && WorkoutVideoUrl.extractVideoId(remote) != null ->
                    Intent(this, WorkoutVideoActivity::class.java).putExtra("url", remote)
                else -> Intent(Intent.ACTION_VIEW, Uri.parse(remote))
            }
            startActivity(intent)
        } catch(_:Exception) {
            Toast.makeText(this,"Không mở được video, hãy chọn lại tệp hoặc kiểm tra mạng.",Toast.LENGTH_LONG).show()
        }
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
        if (pausedForSafety) {
            Toast.makeText(this, "Buổi tập đang dừng vì đau; hãy nghỉ, không mở hướng dẫn vận động.", Toast.LENGTH_LONG).show()
            return
        }
        if (completed || index !in moves.indices) return
        // A step-by-step walkthrough is opt-in and never consumes workout time.
        pause()
        spoken?.stop()
        val move = moves.getOrNull(index) ?: return
        var walkthrough = WorkoutStepGuide.Walkthrough.start(move.steps)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 12)
        }
        val stepProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = walkthrough.steps.size
            progress = walkthrough.position
            contentDescription = "Tiến độ hướng dẫn động tác"
        }
        val label = TextView(this).apply {
            textSize = 18f
            setPadding(8, 24, 8, 24)
            setTextColor(Color.rgb(70, 61, 85))
        }
        val previousButton = Button(this).apply {
            text = "← Bước trước"
            contentDescription = "Quay lại bước hướng dẫn trước"
            isEnabled = false
        }
        container.addView(stepProgress)
        container.addView(previousButton)
        container.addView(ScrollView(this).apply {
            addView(label)
        }, LinearLayout.LayoutParams(-1, (250 * resources.displayMetrics.density).toInt()))
        val dialog = AlertDialog.Builder(this)
            .setTitle("📖 ${move.name}")
            .setView(container)
            .setNegativeButton("Đóng", null)
            .setNeutralButton("🔊 Nghe bước này", null)
            .setPositiveButton("Bước tiếp →", null)
            .create()
        fun renderStep() {
            label.text = "Bước ${walkthrough.position}/${walkthrough.steps.size}\n\n" +
                "${walkthrough.currentStep}\n\n🌿 Dễ hơn: ${move.easier}\n⚠️ ${move.avoid}"
            label.contentDescription =
                "Bước ${walkthrough.position} trên ${walkthrough.steps.size}. ${walkthrough.currentStep}"
            stepProgress.progress = walkthrough.position
            previousButton.isEnabled = walkthrough.canGoPrevious
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = walkthrough.canGoNext
                text = if (walkthrough.canGoNext) "Bước tiếp →" else "Đã xem hết"
            }
        }
        dialog.setOnShowListener {
            renderStep()
            previousButton.setOnClickListener {
                walkthrough = walkthrough.previous()
                spoken?.stop()
                renderStep()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (walkthrough.canGoNext) {
                    walkthrough = walkthrough.next()
                    spoken?.stop()
                    renderStep()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                speak("Bước ${walkthrough.position}. ${walkthrough.currentStep}")
            }
        }
        dialog.setOnDismissListener { spoken?.stop() }
        dialog.show()
    }

    private fun renderDose() {
        val move=moves[index]
        val restGuidance = currentSetPlan()?.let {
            "\n\n⏱ Nghỉ giữa hiệp đề xuất: ${it.defaultRestSeconds} giây. Có thể tùy chỉnh nếu cần."
        }.orEmpty()
        guide.text="🎯 ${targetDose(index)}${restGuidance}\n\n📖 Cách thực hiện:\n${move.steps}\n\n🌿 Giảm độ khó:\n${move.easier}\n\n⚠️ Lưu ý:\n${move.avoid}"
    }
    private fun displayStep() {
        paused=true;handler.removeCallbacks(tick)
        resting=false
        restDeadline=0L
        restRemainingMs=0L
        setTimerExpired=false
        val move=moves[index]
        tenSecondCueGiven=false
        remainingMs=RemainingWorkoutDuration.seconds(move.seconds,index,shorterFromIndex)*1000L
        heading.text="${index+1}/${moves.size} • ${move.name}"
        poseView.pose=WorkoutPoseGuide.classify(move.name)
        renderDose()
        renderSetControls()
        progress.progress=index
        val sec=(remainingMs/1000).toInt()
        counter.text=String.format(Locale.US,"%02d:%02d",sec/60,sec%60)
        control.text="▶ Tập động tác này"
        spoken?.stop()
    }
    private fun setPlan(position:Int):WorkoutSetTracker.Plan? {
        val move=moves.getOrNull(position) ?: return null
        val base=WorkoutSetTracker.parse(move.dose, move.restSeconds) ?: return null
        val edited=repTargets[position] ?: return base
        return base.copy(targetMin=edited,targetMax=edited)
    }

    private fun currentSetPlan():WorkoutSetTracker.Plan? = setPlan(index)

    private fun clockText(milliseconds:Long):String {
        val seconds=(milliseconds.coerceAtLeast(0L)/1000L).toInt()
        return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
    }

    private fun restCounterText(milliseconds:Long):String = "Nghỉ " + clockText(milliseconds)

    private fun renderSetControls() {
        val plan=currentSetPlan()
        val visibility=if(plan==null) android.view.View.GONE else android.view.View.VISIBLE
        setProgress.visibility=visibility
        completeSetButton.visibility=visibility
        if(plan==null) {
            restSetButton.visibility=android.view.View.GONE
            customSetRestButton.visibility=android.view.View.GONE
            return
        }
        val done=(completedSetsByMove[index] ?: 0).coerceIn(0,plan.maximumSets)
        val target=if(plan.targetMin==plan.targetMax) "${plan.targetMin} ${plan.unit}"
            else "${plan.targetMin}–${plan.targetMax} ${plan.unit}"
        val setDescription=if(plan.maximumSets>plan.requiredSets)
            "Tối thiểu ${plan.requiredSets} hiệp, có thể thêm đến ${plan.maximumSets}"
            else "${plan.requiredSets} hiệp"
        setProgress.text="Hiệp tự xác nhận: $done/${plan.maximumSets} • $setDescription • $target"
        if(setTimerExpired) setProgress.append("\nHết thời gian gợi ý; chỉ xác nhận hiệp sau khi bạn thực hiện.")
        setProgress.contentDescription=setProgress.text
        completeSetButton.isEnabled=done<plan.maximumSets && !pausedForSafety && !resting
        completeSetButton.text=when {
            done<plan.requiredSets -> "✓ Xác nhận hoàn thành hiệp ${done+1}/${plan.requiredSets}"
            done<plan.maximumSets -> "＋ Ghi nhận hiệp tùy chọn ${done+1}/${plan.maximumSets}"
            else -> "✓ Đã ghi nhận đủ hiệp"
        }
        val canRest=done>0 && done<plan.maximumSets && !pausedForSafety && !resting
        restSetButton.visibility=if(canRest) android.view.View.VISIBLE else android.view.View.GONE
        customSetRestButton.visibility=if(canRest) android.view.View.VISIBLE else android.view.View.GONE
        restSetButton.isEnabled=canRest
        customSetRestButton.isEnabled=canRest
        restSetButton.text="⏱ Nghỉ theo giáo án (${plan.defaultRestSeconds} giây)"
    }

    /** The user confirms a set after doing it; no camera or automatic rep counting is implied. */
    private fun completeCurrentSet() {
        if(pausedForSafety || completed || resting || index !in moves.indices) return
        val plan=currentSetPlan() ?: return
        if(activeElapsedMs()<=0L) {
            Toast.makeText(this,"Hãy bắt đầu buổi tập trước khi ghi nhận hiệp.",Toast.LENGTH_LONG).show()
            return
        }
        val before=completedSetsByMove[index] ?: 0
        if(before>=plan.maximumSets) return
        pause()
        val after=WorkoutSetTracker.complete(before,plan)
        completedSetsByMove[index]=after
        if(WorkoutSetTracker.isComplete(after,plan)) completedMoveIndices.add(index)
        renderSetControls()
        saveCheckpoint()
        if(after<plan.maximumSets) {
            val message=if(after>=plan.requiredSets)
                "Đã đủ hiệp tối thiểu. Bạn có thể nghỉ rồi thêm hiệp tùy chọn nếu còn sức."
                else "Đã tự xác nhận hiệp $after/${plan.requiredSets}. Nghỉ trước hiệp tiếp theo."
            Toast.makeText(this,message,Toast.LENGTH_LONG).show()
            speak(message)
            startSetRest(plan.defaultRestSeconds)
        } else {
            val message="Đã tự xác nhận đủ $after hiệp cho ${moves[index].name}."
            Toast.makeText(this,message,Toast.LENGTH_LONG).show()
            speak(message)
        }
    }

    /** Rest is a separate countdown and is excluded from active-exercise time. */
    private fun startSetRest(seconds:Int, spokenCue:String?=null) {
        if(pausedForSafety || completed || resting || seconds !in 1..600) return
        pause()
        val restState = WorkoutRestSession.start(remainingMs, seconds)
        remainingMs = restState.exerciseRemainingMs
        resting=true
        restRemainingMs=restState.restRemainingMs
        restDeadline=SystemClock.elapsedRealtime()+restRemainingMs
        startedAt=0L
        paused=false
        handler.removeCallbacks(tick)
        renderSetControls()
        control.text="⏸ Dừng nghỉ"
        counter.text=restCounterText(restRemainingMs)
        handler.post(tick)
        saveCheckpoint()
        val announcement = spokenCue?.take(240)?.let { "Bắt đầu nghỉ $seconds giây. $it" }
            ?: "Bắt đầu nghỉ $seconds giây."
        speak(announcement)
    }

    private fun requestSetRest() {
        if(pausedForSafety || completed || resting) return
        val plan=currentSetPlan() ?: return
        val done=completedSetsByMove[index] ?: 0
        if(done<=0 || done>=plan.maximumSets) return
        pause()
        val field=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            setText(plan.defaultRestSeconds.toString())
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("⏱ Tùy chỉnh nghỉ giữa hiệp")
            .setMessage("Nhập thời gian nghỉ từ 1 đến 600 giây. Thời gian nghỉ không tính vào thời gian vận động.")
            .setView(field)
            .setPositiveButton("Bắt đầu nghỉ") { _, _ ->
                val seconds=field.text.toString().trim().toIntOrNull()
                if(seconds==null || seconds !in 1..600) {
                    Toast.makeText(this,"Nhập thời gian từ 1 đến 600 giây.",Toast.LENGTH_LONG).show()
                } else startSetRest(seconds)
            }
            .setNegativeButton("Đóng",null).show()
    }

    private fun speak(message:String) {
        if(ready)spoken?.speak(message.take(350),TextToSpeech.QUEUE_FLUSH,null,"fitness-${index}")
    }
    private fun resume() {
        if(pausedForSafety) {
            AlertDialog.Builder(this).setTitle("Xác nhận trước khi tập lại")
                .setMessage("Bạn đã báo đau. Chỉ tiếp tục nếu triệu chứng đã hết và bạn thấy an toàn. Nếu còn đau, hãy dừng buổi tập.")
                .setPositiveButton("Tôi đã ổn, tiếp tục"){_,_->pausedForSafety=false;renderSetControls();resume()}
                .setNegativeButton("Tiếp tục nghỉ",null).show()
            return
        }
        if(!paused)return
        if(resting) {
            paused=false
            restDeadline=SystemClock.elapsedRealtime()+restRemainingMs
            startedAt=0L
            control.text="⏸ Dừng nghỉ"
            counter.text=restCounterText(restRemainingMs)
            handler.post(tick)
            return
        }
        paused=false
        startedAt=SystemClock.elapsedRealtime()
        deadline=SystemClock.elapsedRealtime()+remainingMs
        control.text="⏸ Tạm dừng"
        speak("Bài ${index+1}: ${moves[index].name}. ${targetDose(index)}. ${moves[index].steps}")
        handler.post(tick)
    }
    private fun pause() {
        if(paused)return
        val now=SystemClock.elapsedRealtime()
        if(resting) {
            restRemainingMs=(restDeadline-now).coerceAtLeast(0L)
            paused=true
            handler.removeCallbacks(tick)
            spoken?.stop()
            control.text="▶ Tiếp tục nghỉ"
            counter.text=restCounterText(restRemainingMs)
            saveCheckpoint()
            return
        }
        remainingMs=(deadline-now).coerceAtLeast(0L)
        if(startedAt!=0L) accumulated+=(now-startedAt).coerceAtLeast(0L)
        startedAt=0L
        paused=true;handler.removeCallbacks(tick)
        spoken?.stop()
        control.text="▶ Tiếp tục"
        saveCheckpoint()
    }
    private fun nextMove(completedByTimer:Boolean=false) {
        if(pausedForSafety) {
            Toast.makeText(this,"Đang dừng vì báo đau. Hãy xác nhận an toàn trước khi tiếp tục.",Toast.LENGTH_LONG).show()
            return
        }
        if(!completedByTimer) {
            val plan=currentSetPlan()
            if(plan!=null && !WorkoutSetTracker.isComplete(completedSetsByMove[index] ?: 0,plan)) {
                Toast.makeText(this,"Bỏ qua động tác này sẽ không được tính hoàn thành. Hãy xác nhận từng hiệp sau khi tập.",Toast.LENGTH_LONG).show()
            }
        }
        pause()
        if(completedByTimer && currentSetPlan()==null) {
            completedMoveIndices.addAll(WorkoutCompletionTracker.complete(completedMoveIndices,index,moves.size))
        }
        if(index==moves.lastIndex) {
            AlertDialog.Builder(this).setTitle("🎉 Đã đi hết chương trình!")
                .setMessage("Bạn hoàn thành các bước hướng dẫn. Lưu buổi tập vào nhật ký nhé.")
                .setPositiveButton("💾 Lưu buổi tập"){_,_->finishWorkout()}
                .setNegativeButton("Xem lại",null).show()
            return
        }
        index++;displayStep()
        saveCheckpoint()
        resume()
    }
    private fun finishWorkout() {
        pause()
        // Timed moves require timer expiry; rep-based moves require self-confirmed sets.
        // This is transparent offline self-report, not camera-verified performance.
        val elapsedMs=activeElapsedMs()
        val completedCount=completedMoveIndices.count { it in moves.indices }
        val setCompletedMoves=completedMoveIndices.count { position ->
            val plan=setPlan(position)
            plan!=null && WorkoutSetTracker.isComplete(completedSetsByMove[position] ?: 0,plan)
        }
        val timerCompletedMoves=completedMoveIndices.count { position ->
            setPlan(position)==null
        }
        val selfReportedSets=completedSetsByMove.values.sum()
        if (!WorkoutCompletionTracker.canLog(completedMoveIndices,moves.size,elapsedMs)) {
            val reason=if(completedCount < WorkoutCompletionTracker.MIN_COMPLETED_MOVES_TO_LOG)
                "Hãy để bộ đếm hoàn tất động tác theo thời gian hoặc tự xác nhận đủ hiệp cho ít nhất 2 động tác khác nhau. Bỏ qua không được tính."
            else "Cần ít nhất 1 phút hoạt động thực tế, không tính thời gian tạm dừng."
            AlertDialog.Builder(this)
                .setTitle("Chưa đủ hoạt động để ghi nhật ký")
                .setMessage("$reason Chibi chưa ghi buổi tập hoặc mở tiến độ nhiệm vụ. Bạn có thể tập tiếp hoặc thoát mà không lưu.")
                .setPositiveButton("Tập tiếp", null)
                .setNegativeButton("Thoát không lưu") { _, _ ->
                    completed=true
                    checkpoint.edit().clear().apply()
                    finish()
                }.show()
            return
        }
        val mins=(elapsedMs/60000L).toInt().coerceIn(1,600)
        val substitutionSummary=substitutions.entries.sortedBy { it.key }.joinToString("; ") { (position,replacement) ->
            "${originalMoves.getOrNull(position)?.name ?: "Động tác"} → ${replacement.name}"
        }
        val workoutNote=buildString {
            append("Chibi Coach: $completedCount/${moves.size} động tác được ghi nhận; $setCompletedMoves động tác tự xác nhận hiệp ($selfReportedSets hiệp); $timerCompletedMoves động tác theo bộ đếm; $mins phút hoạt động. Số hiệp là tự khai báo, không được cảm biến xác minh.")
            if(substitutionSummary.isNotBlank()) append(" Thay động tác: $substitutionSummary.")
        }
        val saved=WorkoutData.appendSession(this,Workout(
            LifeData.today(),program.name,mins,workoutNote,sessionId,completedCount,selfReportedSets))
        if(!saved) {
            Toast.makeText(this,"Chưa lưu được buổi tập. Tiến độ vẫn được giữ để thử lại.",Toast.LENGTH_LONG).show()
            return
        }
        // The guest-local journal is durable before XP is attempted. If the app is
        // interrupted here, RpgActivity's idempotent journal reconciliation retries it.
        completed=true
        checkpoint.edit().clear().apply()
        control.isEnabled=false
        val evidence = WorkoutRewardEvidence(
            sessionId = sessionId,
            activeMinutes = mins,
            completedMoves = completedCount,
            selfReportedSets = selfReportedSets
        )
        rewardScope.launch {
            try {
                val display = withContext(Dispatchers.IO) {
                    // GuidedWorkoutActivity still uses the guest-only checkpoint and journal.
                    // Do not route it to an account database until Fitness has verified identity-scoped storage.
                    val rpgDatabase = RpgDatabase.get(applicationContext)
                    val rpgEngine = RpgEngine(rpgDatabase)
                    rpgEngine.initialize(LifeData.points(applicationContext))
                    val awardedXp = rpgEngine.awardWorkoutSessions(listOf(evidence))
                    val totalXp = rpgDatabase.dao().character()?.totalXp ?: 0L
                    WorkoutRewardDisplayFactory.create(awardedXp, totalXp)
                }
                if (!isFinishing && !isDestroyed) showWorkoutReward(display)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isFinishing && !isDestroyed) showWorkoutRewardPending()
            }
        }
    }

    private fun showWorkoutReward(display: WorkoutRewardDisplay) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 14, 24, 8)
        }
        panel.addView(TextView(this).apply {
            text = if (display.awardedXp > 0) "⭐ +${display.awardedXp} EXP"
                else "✓ Phần thưởng đã được cấp trước đó"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 72, 145))
            setPadding(8, 10, 8, 14)
        })
        panel.addView(TextView(this).apply {
            text = "🐰 Chibi cấp ${display.level} • Tổng ${display.totalXp} EXP"
            textSize = 17f
            setTextColor(Color.rgb(75, 55, 95))
            gravity = Gravity.CENTER
        })
        if (!display.maxLevelReached) {
            val max = display.xpRequired.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            val current = display.xpInLevel.coerceAtMost(max.toLong()).toInt()
            panel.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.max = max
                progress = current
                contentDescription = "Tiến độ cấp tiếp theo: ${current} trên ${max} EXP"
            })
            panel.addView(TextView(this).apply {
                text = "${display.xpInLevel}/${display.xpRequired} EXP tới cấp tiếp theo"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(95, 80, 110))
                setPadding(4, 8, 4, 8)
            })
        } else {
            panel.addView(TextView(this).apply {
                text = "Đã đạt cấp tối đa."
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
            })
        }
        panel.addView(TextView(this).apply {
            text = "EXP là phần thưởng trò chơi dựa trên thời gian và hiệp tự xác nhận; không phải xác minh bằng camera hoặc thiết bị y tế."
            textSize = 12f
            setTextColor(Color.rgb(100, 92, 110))
            setPadding(4, 12, 4, 4)
        })
        AlertDialog.Builder(this)
            .setTitle("🎉 Buổi tập đã được lưu")
            .setView(panel)
            .setPositiveButton("Mở Chibi RPG") { _, _ ->
                startActivity(Intent(this, RpgActivity::class.java))
                finish()
            }
            .setNegativeButton("Đóng") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showWorkoutRewardPending() {
        AlertDialog.Builder(this)
            .setTitle("Đã lưu buổi tập")
            .setMessage(
                "Nhật ký đã được lưu an toàn trên máy. Chưa thể đồng bộ EXP ngay lúc này; " +
                    "Chibi RPG sẽ thử lại từ cùng mã buổi tập khi bạn mở màn hình RPG. " +
                    "Không cần tập lại và không mất nhật ký."
            )
            .setPositiveButton("Mở Chibi RPG") { _, _ ->
                startActivity(Intent(this, RpgActivity::class.java))
                finish()
            }
            .setNegativeButton("Đóng") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onPause() {
        pause()
        saveCheckpoint()
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        rewardScope.cancel()
        spoken?.stop();spoken?.shutdown()
        super.onDestroy()
    }
}
