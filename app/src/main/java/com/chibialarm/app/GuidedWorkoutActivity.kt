package com.chibialarm.app

import android.app.*
import android.webkit.URLUtil
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.Manifest
import android.content.pm.PackageManager
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
import com.chibialarm.app.fitness.WorkoutMidSessionPreviewFormatter
import com.chibialarm.app.fitness.WorkoutEffortPlanChange
import com.chibialarm.app.fitness.WorkoutRemainingRepTarget
import com.chibialarm.app.fitness.WorkoutCoachQuickPrompts
import com.chibialarm.app.TrainingEquipment
import com.chibialarm.app.WorkoutExtensionPreferences
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
    // Short-lived conversational reference memory; never checkpointed or sent to a server.
    private val coachConversation = WorkoutCoachConversationContext()
    // Reward work is asynchronous; the durable journal remains the retry source if this Activity is recreated.
    private val rewardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var counter:TextView
    private lateinit var restPreview:TextView
    private lateinit var restPosePreview:WorkoutPoseView
    private lateinit var restStepPreview:TextView
    private lateinit var restStepSpeakButton:Button
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

    /**
     * Checkpoints persist canonical catalog exercise names, not runtime-adjusted doses.
     * Fingerprint the same canonical reconstruction so valid restores are not rejected
     * merely because the duration planner tuned an extension's dose before saving.
     */
    private fun checkpointPlanFingerprint(): String {
        val canonicalPlan = WorkoutMidSessionPlanner.decodePlan(
            WorkoutMidSessionPlanner.encodePlan(originalMoves)
        ) ?: originalMoves
        return WorkoutConstraintFilter.fingerprint(canonicalPlan, workoutConstraints)
    }
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
    // Guest-local equipment context; only enum names are checkpointed, never raw speech.
    private var extensionPreferences = WorkoutExtensionPreferences()
    private fun availableWorkoutEquipment(): Set<TrainingEquipment> =
        TrainingEquipmentAvailability.fromPreference(extensionPreferences.equipment) +
            extensionPreferences.additionalEquipment
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
    // Workout-only speech rate; the user's global voice preference is never overwritten.
    private var preferredWorkoutSpeechRate=WorkoutSpeechRate.DEFAULT_RATE
    private var workoutSpeechRate=WorkoutSpeechRate.DEFAULT_RATE
    // A restored in-session rate must win over the asynchronous TTS initialization callback.
    private var workoutSpeechRateRestored=false
    // The selected rest-step speech is transient and derived from the local exercise catalog.
    private var restStepSpeechText:String?=null
    private var speechStatus:TextView?=null
    // Last selected step cue is kept in memory only so voice can repeat that exact step.
    private var lastGuideStepCue:WorkoutStepGuide.SelectedCue?=null
    // Guest-only checkpoint; authenticated profiles must use isolated storage.
    private val checkpoint by lazy { getSharedPreferences("chibi_workout_checkpoint_guest", MODE_PRIVATE) }
    // Index-scoped rep guidance: completed steps are never retroactively edited.
    private val repTargets=mutableMapOf<Int,Int>()
    private var confirmedProgression: Triple<String,Int,String>? = null
    // Timed moves use the timer; set-based moves require explicit user confirmation.
    private val completedMoveIndices=sortedSetOf<Int>()
    private val completedSetsByMove=mutableMapOf<Int,Int>()
    // Values are self-reported; never inferred from the timer, audio or camera.
    private val actualSetValuesByMove=mutableMapOf<Int,List<Int>>()
    // Optional subjective effort answers, keyed to the exact confirmed set; guest-local only.
    private val effortLevelsByMove=mutableMapOf<Int,List<WorkoutEffortFeedback.Level?>>()
    // One active check-in surface; a voice result remains pending until explicitly confirmed.
    private var activeEffortDialog: AlertDialog? = null
    private var pendingEffortLevel: WorkoutEffortFeedback.Level? = null
    private lateinit var setProgress:TextView
    private lateinit var setProgressBar:ProgressBar
    private lateinit var completeSetButton:Button
    private lateinit var restSetButton:Button
    private lateinit var customSetRestButton:Button
    private lateinit var skipRestButton:Button
    private var resting=false
    private var restDeadline=0L
    private var restRemainingMs=0L
    private var restTenSecondCueGiven=false
    // Automatic ten-second rest narration is explicitly controllable and guest-session local.
    private var restVoiceCueEnabled = true
    private var updatingRestVoiceCueToggle = false
    private lateinit var restVoiceCueToggle: CheckBox
    // A rest-completion cue survives a busy/unavailable TTS engine and process recreation.
    private val restCompletionSpeechQueue = WorkoutRestCompletionSpeechQueue()
    private var setTimerExpired=false
    private fun targetDose(position:Int):String = repTargets[position]?.let {
        val unit = WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds)?.unit ?: "lần"
        "$it $unit (mục tiêu đã chỉnh)"
    } ?: moves[position].dose

    /** Apply only a validated, user-confirmed next-session target; never infer or add sets. */
    private fun applyInitialProgression(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val checkpointAge = System.currentTimeMillis() - checkpoint.getLong("savedAt", 0L)
        if (!checkpoint.getString("program", null).isNullOrBlank() &&
            checkpointAge in 0L..604_800_000L
        ) return
        val exerciseName = intent.getStringExtra("progression_exercise")?.trim()?.take(90).orEmpty()
        val target = intent.getIntExtra("progression_reps", -1)
        // Older intents remain valid as repetition-only previews.
        val unit = intent.getStringExtra("progression_unit") ?: "lần"
        if (exerciseName.isBlank() || target !in 1..100 || unit !in setOf("lần", "giây")) return
        val matches = moves.indices.filter { moves[it].name == exerciseName }
        if (matches.size != 1) return
        val position = matches.single()
        val plan = WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds) ?: return
        val increment = if (unit == "giây") 5 else 1
        if (plan.unit != unit || target !in (plan.targetMin + increment)..plan.targetMax) return
        repTargets[position] = target
        confirmedProgression = Triple(exerciseName, target, unit)
    }
    private var completed=false
    // Do not overwrite a saved session before the user chooses Restore or Start over.
    private var checkpointDecisionPending=false
    private fun saveCheckpoint(): Boolean {
        if (completed || checkpointDecisionPending || moves.isEmpty()) return true
        return checkpoint.edit().putString("program",program.id)
            .putString("planNames",WorkoutMidSessionPlanner.encodePlan(originalMoves))
            .putString("planFingerprint",checkpointPlanFingerprint())
            .putBoolean("avoidWristLoad",workoutConstraints.avoidWristLoad)
            .putBoolean("avoidFloorPositions",workoutConstraints.avoidFloorPositions)
            .putBoolean("avoidOverheadReach",workoutConstraints.avoidOverheadReach)
            .putBoolean("avoidKneeling",workoutConstraints.avoidKneeling)
            .putBoolean("avoidJumping",workoutConstraints.avoidJumping)
            .putInt("extensionImpact",extensionPreferences.impact)
            .putString("extensionMoves",optionalExtensionNames.sorted().joinToString("\n"))
            .putInt("extensionEquipment",extensionPreferences.equipment)
            .putString("additionalEquipment",extensionPreferences.additionalEquipment.sortedBy { it.ordinal }.joinToString(",") { it.name })
            .putString("sessionId",sessionId)
            .putInt("index",index).putLong("remaining",remainingMs.coerceIn(0L,600_000L))
            .putBoolean("safety",pausedForSafety)
            .putBoolean("tenSecondCueGiven",tenSecondCueGiven)
            .putInt("shorterFrom",shorterFromIndex)
            .putString("repTargets",repTargets.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("substitutions",WorkoutMoveSubstitution.encode(substitutions,originalMoves,workoutConstraints,availableWorkoutEquipment()))
            .putString("completedMoves",WorkoutCompletionTracker.encode(completedMoveIndices,moves.size))
            .putString("completedSets",WorkoutSetTracker.encode(completedSetsByMove,moves))
            .putString("setValues",WorkoutSetRepetitionLog.encode(actualSetValuesByMove,moves))
            .putString("effortLog",WorkoutEffortLog.encode(effortLevelsByMove,moves,completedSetsByMove))
            .putBoolean("resting",resting)
            .putLong("restRemaining",restRemainingMs.coerceIn(0L,600_000L))
            .putBoolean("restTenSecondCueGiven",restTenSecondCueGiven)
            .putBoolean("restVoiceCueEnabled",restVoiceCueEnabled)
            .putString("restCompletionSpeech",restCompletionSpeechQueue.pendingMessage)
            .putBoolean("setTimerExpired",setTimerExpired)
            .putBoolean("started",activeElapsedMs()>0L)
            .putLong("elapsed",activeElapsedMs())
            .putFloat("speechRate",WorkoutSpeechRate.normalize(workoutSpeechRate))
            // Commit synchronously: Android may kill this process immediately after onPause.
            .putLong("savedAt",System.currentTimeMillis()).commit()
    }
    private fun offerCheckpoint() {
        if (checkpoint.getString("program",null)!=program.id) return
        // Read and validate the saved session without mutating the newly requested plan.
        // The user may choose “Bắt đầu lại”; in that case the current intent/equipment must win.
        val savedPlanNames = checkpoint.getString("planNames", null)
        val restoredPlan = if (!savedPlanNames.isNullOrBlank()) {
            WorkoutMidSessionPlanner.decodePlan(savedPlanNames) ?: run {
                checkpoint.edit().clear().apply()
                Toast.makeText(this,"Không thể xác minh giáo án đã lưu; tiến độ cũ không được áp dụng nhầm.",Toast.LENGTH_LONG).show()
                return
            }
        } else null
        val restoredConstraints = WorkoutConstraintProfile(
            avoidWristLoad = workoutConstraints.avoidWristLoad ||
                checkpoint.getBoolean("avoidWristLoad", false),
            avoidFloorPositions = workoutConstraints.avoidFloorPositions ||
                checkpoint.getBoolean("avoidFloorPositions", false),
            avoidOverheadReach = workoutConstraints.avoidOverheadReach ||
                checkpoint.getBoolean("avoidOverheadReach", false),
            avoidKneeling = workoutConstraints.avoidKneeling ||
                checkpoint.getBoolean("avoidKneeling", false),
            avoidJumping = workoutConstraints.avoidJumping ||
                checkpoint.getBoolean("avoidJumping", false)
        )
        val restoredEquipment = checkpoint.getInt("extensionEquipment", extensionPreferences.equipment).coerceIn(0, 5)
        val restoredImpact = checkpoint.getInt("extensionImpact", extensionPreferences.impact).coerceIn(0, 2)
        val restoredAdditionalEquipment = checkpoint.getString("additionalEquipment", "").orEmpty()
            .split(',')
            .mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
            .toSet()
        val restoredExtensionNames = checkpoint.getString("extensionMoves", "").orEmpty()
            .split("\n").filter { it.isNotBlank() }.take(20)
        val restoredAvailableEquipment = TrainingEquipmentAvailability.fromPreference(restoredEquipment) +
            restoredAdditionalEquipment
        val checkpointPlan = restoredPlan ?: originalMoves
        val restoredPlanCompatible = checkpointPlan.all { move ->
            WorkoutConstraintFilter.isAllowed(move, restoredConstraints) &&
                TrainingEquipmentAvailability.allows(move, restoredAvailableEquipment)
        }
        val savedFingerprint = checkpoint.getString("planFingerprint", null)
        val restoredFingerprint = WorkoutConstraintFilter.fingerprint(checkpointPlan, restoredConstraints)
        // Older checkpoints fingerprinted duration-adjusted moves even though only catalog
        // names were stored. Accept that legacy fingerprint only when the current plan matches;
        // all new checkpoints use the canonical catalog fingerprint above.
        val legacyFingerprint = WorkoutConstraintFilter.fingerprint(originalMoves, restoredConstraints)
        val fingerprintMatches = savedFingerprint == null ||
            savedFingerprint == restoredFingerprint ||
            savedFingerprint == legacyFingerprint
        if (!restoredPlanCompatible || !fingerprintMatches ||
            (savedFingerprint == null && (restoredConstraints.avoidWristLoad ||
                restoredConstraints.avoidFloorPositions || restoredConstraints.avoidOverheadReach ||
                restoredConstraints.avoidKneeling))) {
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
                val savedSpeechRate = checkpoint.getFloat("speechRate", Float.NaN)
                    .takeIf { checkpoint.contains("speechRate") }
                workoutSpeechRate = WorkoutSpeechRate.restore(preferredWorkoutSpeechRate, savedSpeechRate)
                workoutSpeechRateRestored = savedSpeechRate != null
                spoken?.setSpeechRate(workoutSpeechRate)
                // Apply the checkpoint snapshot only after the user explicitly chooses Restore.
                restoredPlan?.let { savedPlan ->
                    originalMoves = savedPlan
                    moves = savedPlan
                    program = program.copy(moves = savedPlan)
                }
                workoutConstraints = restoredConstraints
                extensionPreferences = extensionPreferences.copy(
                    equipment = restoredEquipment,
                    impact = if (restoredConstraints.avoidJumping) 1 else restoredImpact,
                    additionalEquipment = restoredAdditionalEquipment,
                    constraints = restoredConstraints
                )
                optionalExtensionNames.clear()
                optionalExtensionNames.addAll(
                    restoredExtensionNames.filter { name -> moves.any { it.name == name } }.take(20)
                )
                sessionId=validSessionId(checkpoint.getString("sessionId",null)) ?: UUID.randomUUID().toString()
                index=checkpoint.getInt("index",0).coerceIn(0,moves.lastIndex)
                val savedSubstitutions=checkpoint.getString("substitutions",null)
                substitutions.clear()
                substitutions.putAll(WorkoutMoveSubstitution.decode(savedSubstitutions,originalMoves,workoutConstraints,availableWorkoutEquipment()))
                moves=WorkoutMoveSubstitution.apply(originalMoves,savedSubstitutions,workoutConstraints,availableWorkoutEquipment())
                shorterFromIndex=checkpoint.getInt("shorterFrom",Int.MAX_VALUE).coerceIn(0, moves.size)
                repTargets.clear()
                repTargets.putAll(WorkoutRepTargets.decode(checkpoint.getString("repTargets",null),moves.size,index))
                completedMoveIndices.clear()
                completedMoveIndices.addAll(WorkoutCompletionTracker.decode(checkpoint.getString("completedMoves",null),moves.size))
                completedSetsByMove.clear()
                completedSetsByMove.putAll(WorkoutSetTracker.decode(checkpoint.getString("completedSets",null),moves))
                actualSetValuesByMove.clear()
                actualSetValuesByMove.putAll(WorkoutSetRepetitionLog.decode(checkpoint.getString("setValues",null),moves,completedSetsByMove))
                effortLevelsByMove.clear()
                effortLevelsByMove.putAll(WorkoutEffortLog.decode(
                    checkpoint.getString("effortLog",null),moves,completedSetsByMove
                ))
                completedMoveIndices.removeAll { position ->
                    val plan=setPlan(position)
                    plan!=null && !WorkoutSetTracker.isComplete(completedSetsByMove[position] ?: 0,plan)
                }
                restCompletionSpeechQueue.restore(checkpoint.getString("restCompletionSpeech",null))
                displayStep()
                remainingMs=checkpoint.getLong("remaining",remainingMs).coerceIn(0L,600_000L)
                tenSecondCueGiven=checkpoint.getBoolean("tenSecondCueGiven",remainingMs<=10_000L)
                pausedForSafety=checkpoint.getBoolean("safety",false)
                resting=checkpoint.getBoolean("resting",false)
                restRemainingMs=checkpoint.getLong("restRemaining",0L).coerceIn(0L,600_000L)
                restTenSecondCueGiven=checkpoint.getBoolean("restTenSecondCueGiven",false)
                restVoiceCueEnabled=checkpoint.getBoolean("restVoiceCueEnabled",true)
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
                preferredWorkoutSpeechRate = WorkoutSpeechRate.normalize(
                    getSharedPreferences("settings",0).getFloat("rate",1f)
                )
                workoutSpeechRate = preferredWorkoutSpeechRate
                workoutSpeechRateRestored = true
                spoken?.setSpeechRate(workoutSpeechRate)
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
                restTenSecondCueGiven=false
                restVoiceCueEnabled=true
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
    private val guideVoiceRequest=891
    private val effortVoiceRequest=893
    private val previewVoiceRequest=894
    private val microphonePermissionRequest=892
    private var pendingRecognitionRequestCode:Int?=null
    // Callbacks exist only for the active dialog; raw transcripts are never persisted.
    private var guideVoiceResultHandler:((String)->Unit)?=null
    private var effortVoiceResultHandler:((String)->Unit)?=null
    // Transient callback for the currently visible mid-session preview; raw speech is never stored.
    private var previewVoiceResultHandler:((String)->Unit)?=null
    private val tick=object:Runnable {
        override fun run() {
            if(paused)return
            if(resting) {
                val left=(restDeadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                restRemainingMs=left
                counter.text=restCounterText(left)
                if (WorkoutRestVoiceCue.shouldAnnounce(
                        remainingMs = left,
                        alreadyAnnounced = restTenSecondCueGiven,
                        pausedForSafety = pausedForSafety,
                        voiceCueEnabled = restVoiceCueEnabled,
                        microphoneActive = pendingRecognitionRequestCode != null,
                        // Do not flush an utterance that is already speaking. The next tick retries.
                        speechEngineReady = ready && spoken != null && spoken?.isSpeaking != true
                    )) {
                    val cue = WorkoutRestVoiceCue.message(left, restPreview.text.toString())
                    // Persist the one-shot only after Android accepts the utterance.
                    if (!cue.isNullOrBlank() && speak(cue)) {
                        restTenSecondCueGiven = true
                        saveCheckpoint()
                    }
                }
                if(left==0L) {
                    resting=false
                    paused=true
                    restRemainingMs=0L
                    val completedSets = completedSetsByMove[index] ?: 0
                    val plan = currentSetPlan()
                    val nextIndex = WorkoutRestPosePreview.nextMoveIndex(
                        currentIndex = index,
                        moveCount = moves.size,
                        completedSets = completedSets,
                        plan = plan
                    )
                    val nextMove = nextIndex
                        ?.takeIf { it != index }
                        ?.let { moves.getOrNull(it) }
                    // Keep the spoken completion cue until TTS accepts it. If speech is
                    // unavailable, the same message remains visible and manually replayable.
                    restCompletionSpeechQueue.enqueue(
                        WorkoutRestVoiceCue.completionMessage(
                            moveName = moves.getOrNull(index)?.name,
                            completedSets = completedSets,
                            plan = plan,
                            nextMoveName = nextMove?.name,
                            nextMoveDose = nextMove?.dose
                        )
                    )
                    renderSetControls()
                    control.text="▶ Tiếp tục hiệp"
                    counter.text=clockText(remainingMs)
                    saveCheckpoint()
                    drainPendingRestCompletionSpeech()
                } else handler.postDelayed(this,300)
                return
            }
            val left=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs=left
            if (WorkoutExerciseVoiceCue.shouldAnnounce(
                    remainingMs = left,
                    alreadyAnnounced = tenSecondCueGiven,
                    pausedForSafety = pausedForSafety,
                    microphoneActive = pendingRecognitionRequestCode != null,
                    // Keep retrying on the next timer tick if TTS is busy or not ready.
                    speechEngineReady = ready && spoken != null && spoken?.isSpeaking != true
                )) {
                val cue = WorkoutExerciseVoiceCue.message(
                    remainingMs = left,
                    upcomingMoveName = moves.getOrNull(index + 1)?.name
                )
                // Consume the one-shot only after Android accepts the utterance.
                if (!cue.isNullOrBlank() && speak(cue)) {
                    tenSecondCueGiven = true
                    saveCheckpoint()
                }
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
                savedInstanceState?.getBoolean("workout_avoid_floor_positions", false) == true,
            avoidOverheadReach = intent.getBooleanExtra("avoid_overhead_reach", false) ||
                savedInstanceState?.getBoolean("workout_avoid_overhead_reach", false) == true,
            avoidKneeling = intent.getBooleanExtra("avoid_kneeling", false) ||
                savedInstanceState?.getBoolean("workout_avoid_kneeling", false) == true,
            avoidJumping = intent.getIntExtra("extension_impact", 0) == 1 ||
                savedInstanceState?.getBoolean("workout_avoid_jumping", false) == true
        )
        val constraintResult = WorkoutConstraintFilter.apply(baseProgram, workoutConstraints)
        val equipmentSelection = intent.getIntExtra("extension_equipment", 0).coerceIn(0, 5)
        val additionalEquipment = intent.getStringArrayListExtra("extension_additional_equipment")
            ?.mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
            ?.toSet().orEmpty()
        val equipmentResult = WorkoutEquipmentFilter.apply(
            constraintResult.program, equipmentSelection, additionalEquipment
        )
        if (!equipmentResult.canStart) {
            Toast.makeText(this, "Chưa có động tác chính phù hợp với bộ lọc và dụng cụ đã chọn.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        program = equipmentResult.program
        val level=intent.getIntExtra("level",0).coerceIn(0,2)
        workoutLevel = level
        val minutes=WorkoutDurationChoices.guidedMinutes(intent.getIntExtra("minutes",15))
        extensionPreferences = WorkoutExtensionPreferences(
            goal = intent.getIntExtra("extension_goal", 0).coerceIn(0, 5),
            energy = intent.getIntExtra("extension_energy", 1).coerceIn(0, 2),
            equipment = intent.getIntExtra("extension_equipment", 0).coerceIn(0, 5),
            impact = intent.getIntExtra("extension_impact", 0).coerceIn(0, 2),
            constraints = workoutConstraints,
            additionalEquipment = additionalEquipment
        )
        savedInstanceState?.let { state ->
            val additional = state.getStringArrayList("workout_additional_equipment")
                ?.mapNotNull { raw -> TrainingEquipment.values().firstOrNull { it.name == raw } }
                ?.toSet() ?: extensionPreferences.additionalEquipment
            extensionPreferences = extensionPreferences.copy(
                equipment = state.getInt("workout_extension_equipment", extensionPreferences.equipment).coerceIn(0, 5),
                impact = if (workoutConstraints.avoidJumping) 1 else
                    state.getInt("workout_extension_impact", extensionPreferences.impact).coerceIn(0, 2),
                additionalEquipment = additional
            )
        }
        val planned=WorkoutDurationPlanner.build(program,level,minutes,extensionPreferences)
        moves=planned.moves
        originalMoves=moves.toList()
        optionalExtensionNames.clear()
        optionalExtensionNames.addAll(planned.extensionMoves.map { it.name })
        // A new-session preview may carry only catalog-approved substitutions. The original
        // plan stays intact for checkpoint validation; completed work does not exist yet.
        val initialSubstitutionRaw = intent.getStringExtra("initial_substitutions")
        if (savedInstanceState == null && !initialSubstitutionRaw.isNullOrBlank()) {
            val decoded = WorkoutMoveSubstitution.decode(
                initialSubstitutionRaw, originalMoves, workoutConstraints, availableWorkoutEquipment()
            )
            val available = availableWorkoutEquipment()
            val approved = decoded.filter { (_, replacement) ->
                TrainingEquipmentAvailability.allows(replacement, available)
            }
            substitutions.putAll(approved)
            val safeCheckpoint = WorkoutMoveSubstitution.encode(
                approved, originalMoves, workoutConstraints, availableWorkoutEquipment()
            )
            moves = WorkoutMoveSubstitution.apply(originalMoves, safeCheckpoint, workoutConstraints, availableWorkoutEquipment())
            approved.forEach { (position, replacement) ->
                if (originalMoves[position].name in optionalExtensionNames) {
                    optionalExtensionNames.remove(originalMoves[position].name)
                    optionalExtensionNames.add(replacement.name)
                }
            }
            if (approved.size != decoded.size) {
                Toast.makeText(this, "Một số thay đổi xem trước không còn phù hợp nên đã được bỏ qua.", Toast.LENGTH_LONG).show()
            }
            program = program.copy(moves = moves)
        }
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
        applyInitialProgression(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=1;setPadding(24,27,24,24);setBackgroundColor(Color.rgb(247,240,252))
        }
        root.addView(TextView(this).apply {
            text="🐰 Chibi Coach • Tập có hướng dẫn";textSize=22f
            setTextColor(Color.rgb(95,55,113))
        })
        root.addView(TextView(this).apply { text=program.name;textSize=16f })
        confirmedProgression?.let { (exerciseName, target, unit) ->
            root.addView(TextView(this).apply {
                text = "🌱 Mục tiêu tiến triển đã xác nhận: $exerciseName — $target $unit mỗi hiệp. Đây là mục tiêu thử; giảm hoặc dừng nếu thấy khó chịu."
                textSize = 13f
                setPadding(4, 6, 4, 10)
            })
        }
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
        if (equipmentResult.notice.isNotBlank()) root.addView(TextView(this).apply {
            text = equipmentResult.notice
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
        root.addView(Button(this).apply {
            tag = "workout-breathing-guide"
            text = "🌬️ Nhịp thở cho động tác"
            contentDescription = "Xem gợi ý nhịp thở chung cho động tác hiện tại; không sử dụng microphone."
            setOnClickListener { showBreathingGuide() }
        })
        counter=TextView(this).apply {
            textSize=54f;gravity=Gravity.CENTER;setTextColor(Color.rgb(100,70,140))
        }
        root.addView(counter)
        restPreview=TextView(this).apply {
            textSize=14f
            gravity=Gravity.CENTER
            setPadding(12,8,12,12)
            setTextColor(Color.rgb(75,55,95))
            visibility=android.view.View.GONE
        }
        root.addView(restPreview)
        restVoiceCueToggle = CheckBox(this).apply {
            tag = "workout-rest-auto-voice-cue"
            text = "🔊 Tự đọc nhắc khi còn 10 giây"
            contentDescription = "Bật hoặc tắt lời nhắc nghỉ bằng giọng đọc; không sử dụng microphone."
            isChecked = restVoiceCueEnabled
            visibility = android.view.View.GONE
            setOnCheckedChangeListener { _, checked ->
                if (updatingRestVoiceCueToggle) return@setOnCheckedChangeListener
                restVoiceCueEnabled = checked
                contentDescription = if (checked) {
                    "Đang bật lời nhắc nghỉ bằng giọng đọc khi còn 10 giây; không sử dụng microphone."
                } else {
                    "Đã tắt lời nhắc nghỉ tự động bằng giọng đọc; có thể bật lại bất cứ lúc nào."
                }
                saveCheckpoint()
            }
        }
        root.addView(restVoiceCueToggle)
        // A catalog-derived vector preview shows what follows the current rest.
        // It is a static cue during rest; the main pose animation resumes afterward.
        restPosePreview = WorkoutPoseView(this).apply {
            tag = "workout-rest-pose-preview"
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            visibility = android.view.View.GONE
            motionEnabled = false
        }
        root.addView(restPosePreview)
        // A concise, catalog-grounded first-action cue complements the static pose preview.
        restStepPreview = TextView(this).apply {
            tag = "workout-rest-step-preview"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(14, 4, 14, 10)
            setTextColor(Color.rgb(75, 55, 95))
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            visibility = android.view.View.GONE
        }
        root.addView(restStepPreview)
        restStepSpeakButton = Button(this).apply {
            tag = "workout-rest-step-speak"
            text = "🔊 Nghe bước tiếp theo"
            contentDescription = "Nghe hướng dẫn cho động tác tiếp theo trong lúc nghỉ"
            visibility = android.view.View.GONE
            setOnClickListener {
                when {
                    pausedForSafety -> Toast.makeText(this@GuidedWorkoutActivity,
                        "Buổi tập đang dừng vì đau; không phát hướng dẫn vận động.", Toast.LENGTH_LONG).show()
                    pendingRecognitionRequestCode != null -> Toast.makeText(this@GuidedWorkoutActivity,
                        "Đang chờ quyền hoặc phiên nhận giọng nói; hãy nghe sau khi microphone đóng.", Toast.LENGTH_LONG).show()
                    !ready -> Toast.makeText(this@GuidedWorkoutActivity,
                        "Giọng đọc chưa sẵn sàng. Thông báo vẫn được giữ bằng chữ trên màn hình.", Toast.LENGTH_LONG).show()
                    !resting && restCompletionSpeechQueue.pendingMessage != null ->
                        drainPendingRestCompletionSpeech()
                    !resting -> Toast.makeText(this@GuidedWorkoutActivity,
                        "Chỉ có hướng dẫn xem trước trong thời gian nghỉ.", Toast.LENGTH_SHORT).show()
                    else -> restStepSpeechText?.let { speak(it) }
                }
            }
        }
        root.addView(restStepSpeakButton)
        setProgress=TextView(this).apply {
            textSize=15f
            gravity=Gravity.CENTER
            setPadding(8,8,8,8)
            setTextColor(Color.rgb(75,55,95))
        }
        root.addView(setProgress)
        setProgressBar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=1
            progress=0
            visibility=android.view.View.GONE
            contentDescription="Tiến độ hiệp bắt buộc"
        }
        root.addView(
            setProgressBar,
            LinearLayout.LayoutParams(-1,(8*resources.displayMetrics.density).toInt())
        )
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
        skipRestButton=Button(this).apply {
            text="⏭ Bỏ qua nghỉ"
            contentDescription="Bỏ qua thời gian nghỉ còn lại sau khi xác nhận; buổi tập vẫn tạm dừng"
            setOnClickListener { confirmSkipRest() }
        }
        root.addView(skipRestButton)
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
        val status = TextView(this).apply {
            text="Đang kiểm tra giọng đọc tiếng Việt…"
            textSize=12f
            contentDescription=text
        }
        speechStatus=status
        root.addView(status)
        root.addView(TextView(this).apply {
            text="Thời gian từng động tác chỉ là nhắc nhở; nghỉ thêm khi cần. Dừng ngay nếu thấy đau hoặc khó chịu."
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
        preferredWorkoutSpeechRate = WorkoutSpeechRate.normalize(
            getSharedPreferences("settings",0).getFloat("rate",1f)
        )
        val savedWorkoutSpeechRate = savedInstanceState
            ?.takeIf { it.getString("workout_program") == program.id && it.containsKey("workout_speech_rate") }
            ?.getFloat("workout_speech_rate")
        if (savedWorkoutSpeechRate != null) {
            workoutSpeechRate = WorkoutSpeechRate.restore(preferredWorkoutSpeechRate, savedWorkoutSpeechRate)
            workoutSpeechRateRestored = true
        }
        spoken=TextToSpeech(this){ state ->
            val engine=spoken
            if(state!=TextToSpeech.SUCCESS || engine==null) {
                ready=false
                updateRestStepSpeakButton()
                speechStatus?.text="⚠️ Chưa khởi tạo được bộ đọc. Hướng dẫn chữ và bộ đếm vẫn dùng được."
                speechStatus?.contentDescription=speechStatus?.text
                return@TextToSpeech
            }
            val settings=getSharedPreferences("settings",0)
            var languageStatus=engine.setLanguage(Locale("vi","VN"))
            if(languageStatus<0) languageStatus=engine.setLanguage(Locale("vi"))
            val available=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
                runCatching { engine.voices.orEmpty() }.getOrDefault(emptyList())
            } else emptyList()
            val selection=CoachSpeechVoicePolicy.resolve(
                languageStatus,
                settings.getString("voice",null),
                available.map { voice -> CoachSpeechVoicePolicy.VoiceOption(voice.name,voice.locale.language) }
            )
            ready=selection.languageSupported
            updateRestStepSpeakButton()
            drainPendingRestCompletionSpeech()
            preferredWorkoutSpeechRate=WorkoutSpeechRate.normalize(settings.getFloat("rate",1f))
            if (!workoutSpeechRateRestored) workoutSpeechRate=preferredWorkoutSpeechRate
            engine.setSpeechRate(workoutSpeechRate)
            engine.setPitch(settings.getFloat("pitch",1f))
            if(ready && Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
                available.firstOrNull { it.name==selection.voiceName }?.let { engine.voice=it }
            }
            speechStatus?.text=if(ready) {
                "🔊 Giọng Việt sẵn sàng. Ngoại tuyến tùy engine và gói giọng đã cài."
            } else {
                "⚠️ Chưa có giọng Việt tương thích. Hướng dẫn chữ vẫn dùng được; hãy cài dữ liệu Tiếng Việt trong cài đặt TTS."
            }
            speechStatus?.contentDescription=speechStatus?.text
        }
        displayStep()
        if (savedInstanceState == null) offerCheckpoint()
        // Restore the active exercise after rotation, but never auto-resume.
        val savedPlanFingerprint = savedInstanceState?.getString("workout_plan_fingerprint")
        val savedPlanCompatible = savedPlanFingerprint == currentPlanFingerprint() ||
            (savedPlanFingerprint == null && !workoutConstraints.avoidWristLoad &&
                !workoutConstraints.avoidFloorPositions && !workoutConstraints.avoidKneeling)
        if(savedInstanceState!=null &&
            savedInstanceState.getString("workout_program")==program.id &&
            savedPlanCompatible) {
            sessionId=validSessionId(savedInstanceState.getString("workout_session_id")) ?: sessionId
            val savedSubstitutions=savedInstanceState.getString("workout_substitutions")
            substitutions.clear()
            substitutions.putAll(WorkoutMoveSubstitution.decode(savedSubstitutions,originalMoves,workoutConstraints,availableWorkoutEquipment()))
            moves=WorkoutMoveSubstitution.apply(originalMoves,savedSubstitutions,workoutConstraints,availableWorkoutEquipment())
            index=savedInstanceState.getInt("workout_index",0).coerceIn(0,moves.lastIndex)
            repTargets.clear()
            repTargets.putAll(WorkoutRepTargets.decode(savedInstanceState.getString("workout_rep_targets"),moves.size,index))
            completedMoveIndices.clear()
            completedMoveIndices.addAll(WorkoutCompletionTracker.decode(savedInstanceState.getString("workout_completed_moves"),moves.size))
            restCompletionSpeechQueue.restore(savedInstanceState.getString("workout_rest_completion_speech"))
            completedSetsByMove.clear()
            completedSetsByMove.putAll(WorkoutSetTracker.decode(savedInstanceState.getString("workout_completed_sets"),moves))
            // Rotation uses the Android Bundle path, not the guest checkpoint dialog.
            // Preserve self-reported values and effort labels with the exact confirmed-set count.
            actualSetValuesByMove.clear()
            actualSetValuesByMove.putAll(
                WorkoutSetRepetitionLog.decode(
                    savedInstanceState.getString("workout_set_values"), moves, completedSetsByMove
                )
            )
            effortLevelsByMove.clear()
            effortLevelsByMove.putAll(
                WorkoutEffortLog.decode(
                    savedInstanceState.getString("workout_effort_log"), moves, completedSetsByMove
                )
            )
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
            restTenSecondCueGiven=savedInstanceState.getBoolean("workout_rest_ten_second_cue",false)
            restVoiceCueEnabled=savedInstanceState.getBoolean("workout_rest_voice_cue_enabled",true)
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
        outState.putBoolean("workout_avoid_overhead_reach",workoutConstraints.avoidOverheadReach)
        outState.putBoolean("workout_avoid_kneeling",workoutConstraints.avoidKneeling)
        outState.putBoolean("workout_avoid_jumping",workoutConstraints.avoidJumping)
        outState.putStringArrayList("workout_extension_names",ArrayList(optionalExtensionNames))
        outState.putInt("workout_extension_equipment",extensionPreferences.equipment)
        outState.putInt("workout_extension_impact",extensionPreferences.impact)
        outState.putStringArrayList(
            "workout_additional_equipment",
            ArrayList(extensionPreferences.additionalEquipment.map { it.name })
        )
        outState.putString("workout_plan_fingerprint",currentPlanFingerprint())
        outState.putString("workout_session_id",sessionId)
        outState.putString("workout_rep_targets",repTargets.entries.joinToString(",") { "${it.key}:${it.value}" })
        outState.putString("workout_substitutions",WorkoutMoveSubstitution.encode(substitutions,originalMoves,workoutConstraints,availableWorkoutEquipment()))
        outState.putString("workout_completed_moves",WorkoutCompletionTracker.encode(completedMoveIndices,moves.size))
        outState.putString("workout_completed_sets",WorkoutSetTracker.encode(completedSetsByMove,moves))
        outState.putString("workout_set_values",WorkoutSetRepetitionLog.encode(actualSetValuesByMove,moves))
        outState.putString("workout_effort_log",WorkoutEffortLog.encode(effortLevelsByMove,moves,completedSetsByMove))
        outState.putBoolean("workout_resting",resting)
        outState.putLong("workout_rest_remaining",restRemainingMs.coerceIn(0L,600_000L))
        outState.putBoolean("workout_rest_ten_second_cue",restTenSecondCueGiven)
        outState.putBoolean("workout_rest_voice_cue_enabled",restVoiceCueEnabled)
        outState.putString("workout_rest_completion_speech",restCompletionSpeechQueue.pendingMessage)
        outState.putBoolean("workout_set_timer_expired",setTimerExpired)
        outState.putInt("workout_index",index)
        outState.putLong("workout_remaining",remainingMs)
        outState.putBoolean("workout_ten_second_cue",tenSecondCueGiven)
        outState.putBoolean("workout_safety",pausedForSafety)
        outState.putInt("workout_shorter_from",shorterFromIndex)
        outState.putBoolean("workout_started",activeElapsedMs()>0L)
        outState.putLong("workout_elapsed",activeElapsedMs())
        outState.putFloat("workout_speech_rate",WorkoutSpeechRate.normalize(workoutSpeechRate))
        super.onSaveInstanceState(outState)
    }
    /**
     * Request RECORD_AUDIO only after the user has read the in-app disclosure and tapped
     * the explicit listen button. Recognition is delegated to Android's speech service.
     */
    private fun requestRecognitionPermissionOrStart(requestCode:Int) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchSpeechRecognition(requestCode)
        } else {
            pendingRecognitionRequestCode = requestCode
            updateRestStepSpeakButton()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), microphonePermissionRequest)
        }
    }

    private fun launchSpeechRecognition(requestCode:Int) {
        // Track the external recognizer as an active microphone session, not only a pending permission request.
        // Rest TTS and step narration stay quiet until Android returns the recognition result.
        pendingRecognitionRequestCode = requestCode
        updateRestStepSpeakButton()
        val guideRequest = requestCode == guideVoiceRequest
        val effortRequest = requestCode == effortVoiceRequest
        val previewRequest = requestCode == previewVoiceRequest
        val prompt = when {
            guideRequest -> "Nói: bước tiếp, bước trước, hỏi bước 2 là gì, đọc lại, bật tự đọc, tắt tự đọc, tạm dừng buổi tập, tiếp tục buổi tập hoặc đóng hướng dẫn. Nếu đau hay khó thở, hãy nói rõ."
            effortRequest -> "Nói cảm nhận hiệp vừa rồi: nhẹ, vừa sức, khó cần nghỉ thêm hoặc quá sức. Nếu đau hay khó thở, hãy nói rõ."
            previewRequest -> "Nói rõ: xác nhận áp dụng, đồng ý áp dụng, hoặc giữ nguyên, hủy thay đổi. Nếu đau hoặc khó thở, hãy nói rõ."
            else -> "Ví dụ: tôi đã làm 10 lần, tôi giữ 20 giây, xong hiệp, bài này khó quá, nghỉ thêm, đổi bài, dừng lại"
        }
        val request=WorkoutSpeechRecognitionRequest.create(prompt)
        try {
            startActivityForResult(request,requestCode)
        } catch (_:Exception) {
            if (pendingRecognitionRequestCode == requestCode) pendingRecognitionRequestCode = null
            updateRestStepSpeakButton()
            handler.post { drainPendingRestCompletionSpeech() }
            if (guideRequest) guideVoiceResultHandler = null
            if (effortRequest) effortVoiceResultHandler = null
            if (previewRequest) previewVoiceResultHandler = null
            Toast.makeText(this,"Thiết bị chưa có dịch vụ nhận giọng nói. Hãy dùng các lựa chọn trên màn hình.",Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Android permission callback compatibility")
    override fun onRequestPermissionsResult(
        requestCode:Int,
        permissions:Array<out String>,
        grantResults:IntArray
    ) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode != microphonePermissionRequest) return
        val target = pendingRecognitionRequestCode
        pendingRecognitionRequestCode = null
        updateRestStepSpeakButton()
        if (target != null && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition(target)
        } else {
            if (target == guideVoiceRequest) guideVoiceResultHandler = null
            if (target == effortVoiceRequest) effortVoiceResultHandler = null
            if (target == previewVoiceRequest) previewVoiceResultHandler = null
            Toast.makeText(
                this,
                "Micro chưa được cấp quyền. Bạn vẫn có thể điều khiển buổi tập bằng chữ; có thể bật quyền trong Cài đặt ứng dụng.",
                Toast.LENGTH_LONG
            ).show()
            handler.post { drainPendingRestCompletionSpeech() }
        }
    }

    private fun requestVoiceCommand() {
        AlertDialog.Builder(this).setTitle("🎤 Điều khiển bằng giọng nói")
            .setMessage("Chỉ bắt đầu nghe sau khi bạn nhấn Tiếp tục. Android sẽ hỏi quyền micro nếu đây là lần đầu. Chibi yêu cầu ưu tiên nhận dạng ngoại tuyến và tối đa 5 phương án nếu dịch vụ hỗ trợ; đây chỉ là gợi ý; nhà cung cấp có thể bỏ qua ưu tiên này và có thể xử lý âm thanh trực tuyến. Chibi không ghi âm nền và không lưu bản ghi hoặc câu nhận dạng. Bạn cũng có thể nhập yêu cầu bằng chữ.")
            .setPositiveButton("Tiếp tục"){_,_->
                // A voice command must never compete with spoken instructions or a running timer.
                pause()
                spoken?.stop()
                requestRecognitionPermissionOrStart(voiceRequest)
            }.setNegativeButton("Hủy",null).show()
    }

    private fun requestGuideVoiceCommand(onResult:(String)->Unit) {
        guideVoiceResultHandler = onResult
        val disclosure = AlertDialog.Builder(this).setTitle("🎤 Điều khiển hướng dẫn bằng giọng nói")
            .setMessage("Chỉ mở micro sau khi bạn nhấn Bắt đầu nghe. Android sẽ hỏi quyền micro nếu cần. Chibi yêu cầu ưu tiên nhận dạng ngoại tuyến và tối đa 5 phương án nếu dịch vụ hỗ trợ; đây chỉ là gợi ý, nhà cung cấp có thể bỏ qua và xử lý âm thanh trực tuyến. Chibi chỉ dùng câu nhận dạng để chuyển bước, trả lời câu hỏi về một bước cụ thể từ thư viện offline, đọc lại, yêu cầu nghỉ, tạm dừng hoặc tiếp tục buổi tập, hoặc đóng hướng dẫn; không ghi âm nền và không lưu bản ghi hay bản chép lời. Bạn có thể hủy và tiếp tục dùng nút trên màn hình.")
            .setPositiveButton("Bắt đầu nghe"){_,_->
                pause()
                spoken?.stop()
                requestRecognitionPermissionOrStart(guideVoiceRequest)
            }
            .setNegativeButton("Hủy"){_,_->guideVoiceResultHandler=null}
            .create()
        disclosure.setOnCancelListener { guideVoiceResultHandler=null }
        disclosure.show()
    }
    private fun requestEffortVoiceCommand(onResult:(String)->Unit) {
        effortVoiceResultHandler = onResult
        val disclosure = AlertDialog.Builder(this)
            .setTitle("🎤 Báo cảm nhận hiệp bằng giọng nói")
            .setMessage(
                "Chỉ mở micro sau khi bạn nhấn Bắt đầu nghe và cấp quyền Android nếu được hỏi. " +
                    "Chibi yêu cầu ưu tiên nhận dạng ngoại tuyến và tối đa 5 phương án nếu dịch vụ hỗ trợ; đây chỉ là gợi ý, nhà cung cấp có thể bỏ qua và xử lý âm thanh trực tuyến. " +
                    "Chibi chỉ dùng câu nhận dạng để chọn mức cảm nhận sau khi bạn xác nhận; " +
                    "ví dụ: “hiệp vừa rồi tôi cảm thấy quá khó” hoặc “không còn sức để tiếp tục”. " +
                    "Ứng dụng không ghi âm nền và không lưu bản ghi hay bản chép lời. " +
                    "Bạn vẫn có thể chọn bằng nút."
            )
            .setPositiveButton("Bắt đầu nghe") { _, _ ->
                pause()
                spoken?.stop()
                requestRecognitionPermissionOrStart(effortVoiceRequest)
            }
            .setNegativeButton("Chọn bằng nút") { _, _ ->
                effortVoiceResultHandler = null
            }
            .create()
        disclosure.setOnCancelListener { effortVoiceResultHandler = null }
        disclosure.show()
    }


    private fun requestPlanPreviewVoiceCommand(onResult:(String)->Unit) {
        previewVoiceResultHandler = onResult
        val disclosure = AlertDialog.Builder(this)
            .setTitle("🎙 Xác nhận bản xem trước bằng giọng nói")
            .setMessage(
                "Chỉ mở microphone sau khi bạn nhấn Bắt đầu nghe và cấp quyền microphone của Android nếu được hỏi. " +
                    "Chibi yêu cầu ưu tiên nhận dạng ngoại tuyến và tối đa 5 phương án nếu dịch vụ hỗ trợ; " +
                    "đây chỉ là gợi ý; nhà cung cấp có thể bỏ qua ưu tiên nhận dạng ngoại tuyến và có thể xử lý âm thanh trực tuyến. " +
                    "Chibi không nghe nền, chỉ nhận văn bản và không lưu bản ghi hay bản chép lời. " +
                    "Đây là xác nhận hai bước: sau câu xác nhận đầu tiên, Chibi sẽ yêu cầu bạn chủ động bắt đầu một phiên nghe thứ hai. " +
                    "Chỉ khi cả hai lần nhận dạng đều rõ ràng là “xác nhận áp dụng” và bản xem trước vẫn khớp, Chibi mới áp dụng. " +
                    "Bạn có thể nói “giữ nguyên” ở bất kỳ bước nào để hủy."
            )
            .setPositiveButton("Bắt đầu nghe") { _, _ ->
                pause()
                spoken?.stop()
                requestRecognitionPermissionOrStart(previewVoiceRequest)
            }
            .setNegativeButton("Hủy") { _, _ ->
                previewVoiceResultHandler = null
            }
            .create()
        disclosure.setOnCancelListener { previewVoiceResultHandler = null }
        disclosure.show()
    }

    /** A second, separately initiated microphone session is the final spoken consent. */
    private fun requestFinalPlanPreviewVoiceCommand(onResult: (String) -> Unit) {
        previewVoiceResultHandler = onResult
        val disclosure = AlertDialog.Builder(this)
            .setTitle("🎙 Xác nhận cuối bằng giọng nói")
            .setMessage(
                "Đây là lần xác nhận cuối cho bản xem trước đang hiển thị. " +
                    "Chỉ mở microphone sau khi bạn nhấn Bắt đầu nghe và cấp quyền Android nếu được hỏi. " +
                    "Chibi không nghe nền, không lưu bản ghi hay bản chép lời. " +
                    "Hãy nói rõ “xác nhận áp dụng” để áp dụng phần chưa hoàn thành; nói “giữ nguyên” để hủy. " +
                    "Nếu buổi tập hoặc tiến độ đã thay đổi, lệnh cũ sẽ bị từ chối."
            )
            .setPositiveButton("Bắt đầu nghe") { _, _ ->
                pause()
                spoken?.stop()
                requestRecognitionPermissionOrStart(previewVoiceRequest)
            }
            .setNegativeButton("Giữ nguyên") { _, _ ->
                previewVoiceResultHandler = null
            }
            .create()
        disclosure.setOnCancelListener { previewVoiceResultHandler = null }
        disclosure.show()
    }

    private fun requestTypedCommand() {
        val field = EditText(this).apply {
            hint = "Ví dụ: bài này tác động nhóm cơ nào?, còn mấy hiệp nữa?, tôi đã làm mấy hiệp?, nghỉ bao lâu?, cách tập bài này?"
            minLines = 2
            maxLines = 4
            setSingleLine(false)
            contentDescription = "Câu hỏi hoặc yêu cầu điều chỉnh buổi tập cho Chibi"
        }
        val activePlan = if (!resting && index in moves.indices) currentSetPlan() else null
        val completedCurrentSets = completedSetsByMove[index] ?: 0
        val hasOptionalCurrentSets = activePlan != null &&
            activePlan.maximumSets > activePlan.requiredSets &&
            completedCurrentSets < activePlan.maximumSets
        val hasOptionalFutureExercises = moves.indices.any { position ->
            position > index && moves[position].name in optionalExtensionNames
        }
        val quickPrompts = WorkoutCoachQuickPrompts.forSession(
            resting = resting,
            hasOptionalFutureExercises = hasOptionalFutureExercises,
            hasOptionalCurrentSets = hasOptionalCurrentSets,
            safetyStopped = pausedForSafety || completed
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 10, 22, 8)
        }
        content.addView(TextView(this).apply {
            text = "Chọn gợi ý để điền nhanh hoặc tự nhập. Chibi chỉ xem trước; mọi thay đổi vẫn cần mày xác nhận."
            textSize = 13f
            setTextColor(Color.rgb(90, 76, 105))
            setPadding(0, 0, 0, 8)
        })
        if (quickPrompts.isNotEmpty()) {
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                contentDescription = "Các gợi ý nhanh cho buổi tập hiện tại"
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            quickPrompts.forEachIndexed { promptIndex, prompt ->
                val chip = Button(this).apply {
                    text = prompt.label
                    isAllCaps = false
                    contentDescription = "Điền gợi ý ${prompt.label}; chưa gửi hoặc áp dụng"
                    setOnClickListener {
                        field.setText(prompt.utterance)
                        field.setSelection(field.text.length)
                        field.requestFocus()
                    }
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (promptIndex > 0) {
                    params.marginStart = (6 * resources.displayMetrics.density).toInt()
                }
                row.addView(chip, params)
            }
            scroll.addView(row)
            content.addView(scroll)
        }
        content.addView(field)

        AlertDialog.Builder(this).setTitle("💬 Chibi Coach • hỏi & điều khiển")
            .setView(content)
            .setPositiveButton("Gửi") { _, _ -> handleCoachCommand(field.text.toString()) }
            .setNegativeButton("Hủy", null)
            .show()
    }
    /** Spoken target edits preserve confirmed sets and apply only to the unfinished sets. */
    private fun confirmSpokenTarget(value: Int, spokenUnit: String = "lần", restSeconds: Int? = null) {
        if (pausedForSafety || completed || index !in moves.indices) return
        val wasResting = resting
        val wasRestRunning = wasResting && !paused
        pause()
        val selected = index
        val plan = currentSetPlan()
        if (plan == null) {
            Toast.makeText(this, "Động tác hiện tại không có mục tiêu hiệp/lần/nhịp/giây để chỉnh.", Toast.LENGTH_LONG).show()
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            return
        }
        if (plan.unit != spokenUnit) {
            Toast.makeText(
                this,
                "Đơn vị bạn nói là $spokenUnit nhưng giáo án dùng ${plan.unit}; Chibi chưa đổi mục tiêu.",
                Toast.LENGTH_LONG
            ).show()
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            return
        }
        val completedBefore = (completedSetsByMove[selected] ?: 0).coerceIn(0, plan.maximumSets)
        val before = repTargets[selected]
        val proposal = WorkoutRemainingRepTarget.preview(
            currentTarget = before,
            completedSets = completedBefore,
            maximumSets = plan.maximumSets,
            requestedReps = value
        )
        if (proposal == null) {
            val message = if (completedBefore >= plan.maximumSets) {
                "Động tác đã đủ số hiệp; không còn hiệp nào để đổi mục tiêu."
            } else {
                "Mục tiêu này đã được chọn hoặc không hợp lệ. Các hiệp đã ghi vẫn được giữ nguyên."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            return
        }

        val previewRemainingMs = remainingMs
        val previewRestRemainingMs = if (wasResting) restRemainingMs else 0L
        val restPreview = if (wasResting && restSeconds != null) {
            WorkoutRestAdjustment.previewExtension(restRemainingMs, restSeconds)
        } else null
        if (wasResting && restSeconds != null && restPreview == null) {
            Toast.makeText(this, "Thời gian nghỉ đã đạt giới hạn 10 phút; mục tiêu chưa được đổi.", Toast.LENGTH_LONG).show()
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            return
        }
        val selectedMove = moves[selected]
        val selectedSessionId = sessionId
        val restDescription = when {
            restSeconds == null -> ""
            wasResting -> "\nĐồng thời thêm $restSeconds giây vào bộ đếm nghỉ hiện tại (tối đa 10 phút)."
            else -> "\nSau khi áp dụng, Chibi sẽ bắt đầu nghỉ riêng $restSeconds giây."
        }
        val message = buildString {
            append("${selectedMove.name}: ${targetDose(selected)} → $value ${plan.unit}.\n")
            append("Đã ghi nhận $completedBefore/${plan.maximumSets} hiệp; ")
            append("mục tiêu mới chỉ áp dụng cho ${proposal.remainingSets} hiệp còn lại. ")
            append("Giá trị các hiệp đã ghi không bị sửa và Chibi không tự đếm ${plan.unit}.")
            append(restDescription)
        }
        val dialog = AlertDialog.Builder(this).setTitle("🎯 Xem trước mục tiêu hiệp còn lại")
            .setMessage(message)
            .setPositiveButton("Áp dụng") { _, _ ->
                val currentDone = completedSetsByMove[selected] ?: 0
                val currentMaximum = currentSetPlan()?.maximumSets ?: -1
                val stale = pausedForSafety || completed || index != selected ||
                    sessionId != selectedSessionId || moves.getOrNull(selected) != selectedMove ||
                    repTargets[selected] != before || currentDone != completedBefore ||
                    currentMaximum != plan.maximumSets || remainingMs != previewRemainingMs ||
                    resting != wasResting ||
                    (wasResting && restRemainingMs != previewRestRemainingMs)
                val accepted = if (stale) null else WorkoutRemainingRepTarget.accept(
                    proposal, repTargets[selected], currentDone, currentMaximum
                )
                if (accepted == null) {
                    Toast.makeText(this, "Buổi tập đã thay đổi. Hãy xem trước lại; tiến độ cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
                    if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                    return@setPositiveButton
                }

                repTargets[selected] = accepted
                renderDose()
                renderSetControls()
                saveCheckpoint()
                val cue = "Đã đổi mục tiêu thành $value ${plan.unit} cho ${proposal.remainingSets} hiệp còn lại. Các hiệp đã ghi được giữ nguyên."
                if (restSeconds != null) {
                    if (wasResting) {
                        val extension = restPreview ?: return@setPositiveButton
                        restRemainingMs = extension.afterRemainingMs
                        if (restRemainingMs > WorkoutRestVoiceCue.WARNING_WINDOW_MS) restTenSecondCueGiven = false
                        restDeadline = 0L
                        counter.text = restCounterText(restRemainingMs)
                        control.text = "▶ Tiếp tục nghỉ"
                        saveCheckpoint()
                        speak("$cue Đã thêm ${(extension.actualAddedMs + 999L) / 1_000L} giây nghỉ.")
                        if (wasRestRunning) resume()
                        else {
                            paused = true
                            control.text = "▶ Tiếp tục nghỉ"
                            counter.text = restCounterText(restRemainingMs)
                        }
                    } else {
                        startSetRest(restSeconds, cue)
                    }
                } else {
                    speak(cue)
                    if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                }
            }
            .setNegativeButton("Giữ nguyên") { _, _ ->
                if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            }
            .create()
        dialog.setOnCancelListener {
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
        }
        dialog.show()
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

    /**
     * Records a spoken actual-set value only after the user reviews and confirms it.
     * Speech recognition is a self-report; it is never treated as sensor verification.
     */
    private fun confirmSpokenSetValue(actualValue: Int) {
        if (pausedForSafety || completed || resting || index !in moves.indices) {
            Toast.makeText(this, "Buổi tập đang dừng; chưa thể ghi nhận hiệp.", Toast.LENGTH_LONG).show()
            return
        }
        val plan = currentSetPlan()
        if (plan == null) {
            Toast.makeText(this, "Động tác này không dùng bộ đếm hiệp.", Toast.LENGTH_LONG).show()
            return
        }
        val maximumValue = if (plan.unit == "giây") 600 else 200
        if (actualValue !in 1..maximumValue) {
            Toast.makeText(this, "Giá trị tự khai báo nằm ngoài giới hạn hợp lệ.", Toast.LENGTH_LONG).show()
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
        val wasRunning = !paused
        pause()
        val dialog = AlertDialog.Builder(this)
            .setTitle("🐰 Chibi nghe được: ${actualValue} ${plan.unit}")
            .setMessage(
                "Ghi nhận hiệp ${before + 1}/${plan.maximumSets} của ${moves[selectedIndex].name} " +
                    "với ${actualValue} ${plan.unit}?\n\n" +
                    "Đây là số liệu bạn tự khai báo, không phải số lần được microphone hoặc camera xác minh."
            )
            .setPositiveButton("Đúng, ghi nhận", null)
            .setNegativeButton("Chưa, giữ nguyên") { _, _ ->
                if (wasRunning && !pausedForSafety && !completed && index == selectedIndex &&
                    sessionId == selectedSessionId
                ) resume()
            }
            .create()
        dialog.setOnCancelListener {
            if (wasRunning && !pausedForSafety && !completed && index == selectedIndex &&
                sessionId == selectedSessionId
            ) resume()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val currentPlan = if (index in moves.indices) currentSetPlan() else null
                if (pausedForSafety || completed || resting || index != selectedIndex ||
                    sessionId != selectedSessionId || currentPlan != plan ||
                    (completedSetsByMove[selectedIndex] ?: 0) != before
                ) {
                    dialog.dismiss()
                    Toast.makeText(this, "Buổi tập đã thay đổi. Chibi chưa ghi nhận hiệp.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                recordCurrentSet(actualValue, selectedIndex, before, plan)
            }
        }
        dialog.show()
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
        if (pausedForSafety || completed || index !in moves.indices) {
            Toast.makeText(this,"Buổi tập đang dừng hoặc chưa thể điều chỉnh lúc này.",Toast.LENGTH_LONG).show()
            return
        }
        if (resting) {
            if (request.isRestOnly) {
                confirmRestExtension(request.restSeconds ?: return)
            } else {
                Toast.makeText(
                    this,
                    "Đang trong thời gian nghỉ. Chibi chưa đổi giáo án kết hợp lúc này; hãy gửi lại yêu cầu sau khi nghỉ.",
                    Toast.LENGTH_LONG
                ).show()
            }
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
        val beforeSessionId = sessionId
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
            level = workoutLevel,
            extensionPreferences = extensionPreferences
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
                val summary = buildString {
                    if (preview.replacements.isNotEmpty()) {
                        append("Thay động tác:\n")
                        preview.replacements.forEach { append("• ").append(it).append("\n") }
                    }
                    if (preview.addedMoves.isNotEmpty()) {
                        append("Thêm bài tùy chọn:\n")
                        preview.addedMoves.forEach { append("• ").append(it).append("\n") }
                    }
                    if (request.availableEquipment.isNotEmpty()) {
                        val newTools = request.availableEquipment.sortedBy { it.ordinal }.joinToString(", ") {
                            when (it) {
                                TrainingEquipment.DUMBBELL -> "tạ đơn"
                                TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực"
                                TrainingEquipment.STABLE_CHAIR -> "ghế vững"
                                TrainingEquipment.MAT -> "thảm tập"
                            }
                        }
                        append("\nGhi nhận dụng cụ có sẵn: $newTools.")
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
                    if (request.avoidJumping) {
                        append("\nLoại bỏ bật nhảy khỏi phần chưa hoàn thành; thay bằng bước ngang nhẹ đã có trong thư viện.")
                    }
                    if (request.allowJumping) {
                        append("\nBỏ hạn chế bật nhảy cho phần chưa hoàn thành; các hiệp đã ghi nhận vẫn giữ nguyên.")
                    }
                    if (request.avoidExternalLoad) {
                        append("\nLoại bỏ tạ và dây kháng lực khỏi phần chưa hoàn thành; chỉ dùng biến thể đã có trong thư viện.")
                    } else if (request.unavailableEquipment.isNotEmpty()) {
                        val missingTools = request.unavailableEquipment.sortedBy { it.ordinal }.joinToString(", ") {
                            when (it) {
                                TrainingEquipment.DUMBBELL -> "tạ đơn"
                                TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực"
                                TrainingEquipment.STABLE_CHAIR -> "ghế vững"
                                TrainingEquipment.MAT -> "thảm tập"
                            }
                        }
                        append("\nKhông có sẵn: $missingTools. Bài có lựa chọn dụng cụ thay thế còn phù hợp vẫn được giữ lại.")
                    }
                    if (request.lowEnergy) {
                        append("\nƯu tiên biến thể nhẹ hơn và nghỉ riêng.")
                    }
                    if (!preview.changed && preview.restSeconds == 0 &&
                        preview.currentSetLimit == null && preview.currentRepLimit == null &&
                        preview.currentDurationLimit == null) {
                        append("\nKhông có bài tùy chọn nào cần bỏ; các hiệp bắt buộc được giữ nguyên.")
                    }
                    append("\n\n")
                    append(WorkoutMidSessionPreviewFormatter.format(preview))
                    append("\n\nCác hiệp đã xác nhận không bị sửa. Đây là gợi ý offline từ thư viện, không phải chẩn đoán hay đánh giá tư thế.")
                }
                fun previewContextStillMatches(): Boolean =
                    !pausedForSafety && !completed && !resting && index == beforeIndex &&
                        sessionId == beforeSessionId && moves == beforeMoves &&
                        currentPlanFingerprint() == beforeFingerprint &&
                        (request.isRepTargetOnly || request.isDurationTargetOnly || remainingMs == beforeRemaining) &&
                        (completedSetsByMove[index] ?: 0) == beforeCompletedSets

                val previewActions = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 8, 24, 8)
                }
                val speakPreviewButton = Button(this).apply {
                    tag = "workout-plan-preview-speak"
                    text = "🔊 Nghe phương án"
                    isAllCaps = false
                }
                val voiceConfirmButton = Button(this).apply {
                    tag = "workout-plan-preview-voice-confirm"
                    text = "🎙 Xác nhận bằng giọng nói"
                    isAllCaps = false
                    contentDescription = "Mở nhận dạng giọng nói sau khi đọc thông báo quyền riêng tư; Chibi không nghe nền"
                }
                previewActions.addView(speakPreviewButton)
                previewActions.addView(voiceConfirmButton)

                val previewDialog = AlertDialog.Builder(this)
                    .setTitle("🐰 Xem trước điều chỉnh giữa buổi")
                    .setMessage(summary)
                    .setView(previewActions)
                    .setPositiveButton("Áp dụng") { _, _ ->
                        if (!previewContextStillMatches()) {
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
                        actualSetValuesByMove.keys.removeAll {
                            it > index || (it == index && preview.currentMoveChanged)
                        }
                        repTargets.keys.removeAll {
                            it > index || (it == index && preview.currentMoveChanged)
                        }

                        // The planner validates a non-null target ceiling before returning Proposed.
                        // Keep the request value as a defensive fallback so a voice edit cannot
                        // disappear between preview construction and confirmation.
                        val acceptedTargetLimit = preview.currentRepLimit ?: preview.currentDurationLimit
                            ?: request.currentRepLimit ?: request.currentDurationLimit
                        // If the user removed only optional sets and has already confirmed
                        // the required minimum, mark this move complete without inventing
                        // a set, changing its log, or awarding extra XP.
                        val adjustedSetPlan = WorkoutSetTracker.parse(
                            moves[index].dose, moves[index].restSeconds
                        )
                        val adjustedCompletedSets = completedSetsByMove[index] ?: 0
                        if (adjustedSetPlan != null &&
                            WorkoutSetTracker.isComplete(adjustedCompletedSets, adjustedSetPlan)
                        ) {
                            completedMoveIndices += index
                        }
                        workoutConstraints = WorkoutMidSessionPlanner.effectiveConstraints(
                            workoutConstraints,
                            request
                        )
                        extensionPreferences = extensionPreferences.copy(
                            equipment = 0,
                            impact = when {
                                request.avoidJumping -> 1
                                request.allowJumping -> 0
                                else -> extensionPreferences.impact
                            },
                            additionalEquipment = preview.availableEquipment,
                            constraints = workoutConstraints
                        )
                        if (preview.currentMoveChanged) {
                            remainingMs = minOf(
                                remainingMs,
                                RemainingWorkoutDuration.seconds(
                                    WorkoutDurationPlanner.activeSeconds(moves[index]), index, shorterFromIndex
                                ) * 1_000L
                            )
                        }
                        heading.text = "${index + 1}/${moves.size} • ${moves[index].name}"
                        poseView.pose = WorkoutPoseGuide.classify(moves[index].name)
                        renderDose()
                        renderSetControls()
                        acceptedTargetLimit?.let { limit ->
                            // Bind the accepted cap to the exact move index captured by the preview,
                            // after plan replacement/removal has finished. This never rewrites catalog dose.
                            val activeIndex = preview.currentIndex
                            if (activeIndex != beforeIndex || activeIndex != index || activeIndex !in moves.indices) {
                                Toast.makeText(this,"Vị trí động tác đã thay đổi. Hãy xem trước lại.",Toast.LENGTH_LONG).show()
                                return@setPositiveButton
                            }
                            val cappedTarget = WorkoutRemainingRepTarget.capExistingTarget(
                                repTargets[activeIndex], limit
                            ) ?: limit
                            repTargets[activeIndex] = cappedTarget
                            renderDose()
                            renderSetControls()
                        }
                        progress.max = moves.size
                        progress.progress = index
                        counter.text = clockText(remainingMs)
                        control.text = "▶ Tiếp tục"
                        saveCheckpoint()

                        val cueParts = mutableListOf<String>()
                        if (preview.currentMoveChanged) {
                            cueParts += "Đã đổi sang ${moves[index].name}. Hãy kiểm tra hướng dẫn trước khi tập tiếp."
                        }
                        preview.currentSetLimit?.let { limit ->
                            val required = WorkoutSetTracker.parse(
                                moves[index].dose, moves[index].restSeconds
                            )?.requiredSets
                            if (required != null) {
                                cueParts += "Đã giới hạn ${moves[index].name} tối đa còn $limit hiệp; giữ nguyên $required hiệp bắt buộc."
                            }
                        }
                        preview.currentRepLimit?.let { limit ->
                            val plan = WorkoutSetTracker.parse(
                                moves[index].dose, moves[index].restSeconds
                            )
                            if (plan != null) {
                                cueParts += "Đã giới hạn ${moves[index].name} tối đa còn $limit ${plan.unit} mỗi hiệp cho phần chưa thực hiện; các hiệp đã xác nhận giữ nguyên."
                            }
                        }
                        preview.currentDurationLimit?.let { limit ->
                            cueParts += "Đã giới hạn thời lượng ${moves[index].name} còn tối đa $limit giây mỗi hiệp cho phần chưa thực hiện; các hiệp đã xác nhận giữ nguyên."
                        }
                        if (cueParts.isEmpty()) {
                            cueParts += "Đã cập nhật phần còn lại của giáo án. Các hiệp đã xác nhận được giữ nguyên."
                        }
                        val cue = cueParts.joinToString(" ")
                        if (preview.restSeconds > 0) startSetRest(preview.restSeconds, cue)
                        else speak(cue)
                    }
                    .setNegativeButton("Giữ nguyên",null)
                    .create()
                previewDialog.setOnDismissListener { previewVoiceResultHandler = null }
                previewDialog.show()
                speakPreviewButton.setOnClickListener {
                    if (!previewContextStillMatches()) {
                        previewDialog.dismiss()
                        Toast.makeText(this@GuidedWorkoutActivity,
                            "Buổi tập đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                    } else if (ready) {
                        speak(WorkoutMidSessionPreviewFormatter.spokenSummary(preview))
                    } else {
                        Toast.makeText(this@GuidedWorkoutActivity,
                            "Giọng đọc chưa sẵn sàng. Mày vẫn có thể đọc bản xem trước trên màn hình.",
                            Toast.LENGTH_LONG).show()
                    }
                }
                voiceConfirmButton.setOnClickListener {
                    if (!previewContextStillMatches()) {
                        previewDialog.dismiss()
                        Toast.makeText(this@GuidedWorkoutActivity,
                            "Buổi tập đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    requestPlanPreviewVoiceCommand { phrase ->
                        if (!previewDialog.isShowing || !previewContextStillMatches()) {
                            Toast.makeText(this@GuidedWorkoutActivity,
                                "Bản xem trước đã đóng hoặc buổi tập đã thay đổi. Không áp dụng lệnh giọng nói cũ.",
                                Toast.LENGTH_LONG).show()
                        } else when (WorkoutPlanPreviewVoiceCommand.parse(phrase)) {
                            WorkoutPlanPreviewVoiceCommand.Action.CONFIRM -> {
                                // First spoken confirmation is intent only. A separate disclosure
                                // and a second user-started recognition session provide final consent.
                                requestFinalPlanPreviewVoiceCommand { finalPhrase ->
                                    if (!previewDialog.isShowing || !previewContextStillMatches()) {
                                        previewDialog.dismiss()
                                        Toast.makeText(
                                            this@GuidedWorkoutActivity,
                                            "Bản xem trước đã cũ hoặc buổi tập đã thay đổi. Không áp dụng lệnh giọng nói.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else when (WorkoutPlanPreviewVoiceCommand.parse(finalPhrase)) {
                                        WorkoutPlanPreviewVoiceCommand.Action.CONFIRM -> {
                                            if (previewContextStillMatches()) {
                                                previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                                            } else {
                                                previewDialog.dismiss()
                                                Toast.makeText(
                                                    this@GuidedWorkoutActivity,
                                                    "Buổi tập đã thay đổi. Hãy xem trước lại.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                        WorkoutPlanPreviewVoiceCommand.Action.CANCEL -> {
                                            previewDialog.dismiss()
                                            Toast.makeText(
                                                this@GuidedWorkoutActivity,
                                                "Đã giữ nguyên giáo án; không áp dụng bản xem trước.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        WorkoutPlanPreviewVoiceCommand.Action.SAFETY -> {
                                            previewDialog.dismiss()
                                            stopForSafety()
                                        }
                                        WorkoutPlanPreviewVoiceCommand.Action.UNSUPPORTED -> {
                                            Toast.makeText(
                                                this@GuidedWorkoutActivity,
                                                "Chibi chưa hiểu lần xác nhận cuối. Giáo án vẫn chưa đổi.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                            WorkoutPlanPreviewVoiceCommand.Action.CANCEL -> {
                                previewDialog.dismiss()
                                Toast.makeText(this@GuidedWorkoutActivity,
                                    "Đã giữ nguyên giáo án; không áp dụng bản xem trước.", Toast.LENGTH_SHORT).show()
                            }
                            WorkoutPlanPreviewVoiceCommand.Action.SAFETY -> {
                                previewDialog.dismiss()
                                stopForSafety()
                            }
                            WorkoutPlanPreviewVoiceCommand.Action.UNSUPPORTED -> {
                                Toast.makeText(this@GuidedWorkoutActivity,
                                    "Chibi chưa hiểu. Hãy nói “xác nhận áp dụng” hoặc “giữ nguyên”. Giáo án chưa đổi.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Presents every coach answer as readable text first, with optional TTS replay.
     * Voice output is an enhancement, never the only way to receive workout status.
     */
    private fun showCoachAnswer(title: String, answer: String) {
        if (answer.isBlank()) return
        val answerDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(answer)
            .setPositiveButton("Đã hiểu", null)
            .setNeutralButton("🔊 Nghe lại", null)
            .create()
        answerDialog.show()
        answerDialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
            contentDescription = "Nghe lại toàn bộ câu trả lời của Chibi"
            setOnClickListener {
                if (!speakCoachAnswer(answer)) {
                    Toast.makeText(
                        this@GuidedWorkoutActivity,
                        "Giọng đọc chưa sẵn sàng. Mày vẫn có thể đọc câu trả lời trên màn hình.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        if (!speakCoachAnswer(answer)) {
            Toast.makeText(
                this,
                "Giọng đọc chưa sẵn sàng. Mày vẫn có thể đọc câu trả lời trên màn hình.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** One local progress snapshot shared by the main coach and the step-guide conversation. */
    private fun currentWorkoutProgressReply(): String? {
        if (moves.isEmpty() || index !in moves.indices) return null
        val secondsLeft = { millis: Long ->
            ((millis.coerceAtLeast(0L) + 999L) / 1_000L).coerceIn(0L, 600L).toInt()
        }
        val plan = currentSetPlan()
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
        val sessionSetPlans = moves.mapIndexedNotNull { moveIndex, move ->
            val movePlan = WorkoutSetTracker.parse(move.dose, move.restSeconds)
                ?: return@mapIndexedNotNull null
            val confirmed = (completedSetsByMove[moveIndex] ?: 0)
                .coerceIn(0, movePlan.maximumSets)
            movePlan to confirmed
        }
        val sessionSetProgress = if (sessionSetPlans.isEmpty()) null else {
            WorkoutContextCoach.SessionSetProgress(
                completedRequired = sessionSetPlans.sumOf { (movePlan, confirmed) ->
                    minOf(movePlan.requiredSets, confirmed)
                },
                totalRequired = sessionSetPlans.sumOf { (movePlan, _) -> movePlan.requiredSets },
                optionalRemaining = sessionSetPlans.sumOf { (movePlan, confirmed) ->
                    (movePlan.maximumSets - maxOf(movePlan.requiredSets, confirmed)).coerceAtLeast(0)
                }
            )
        }
        return WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = completedMoveIndices.count { it in moves.indices },
                totalMoves = moves.size,
                currentMoveIndex = index,
                currentMoveName = moves[index].name,
                sets = setProgress,
                sessionSets = sessionSetProgress,
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
                        level = workoutLevel,
                        shorterFromIndex = shorterFromIndex
                    )
            )
        )
    }

    private fun handleCoachCommand(utterance:String) {
        // Route safety and pause intents before the broader plan parser, which also recognizes
        // the word "nghỉ". A compound "dừng tập rồi bỏ qua nghỉ" must pause, not alter rest.
        when (WorkoutVoiceCommands.parse(utterance)) {
            WorkoutVoiceCommands.Command.PAIN -> {
                stopForSafety()
                return
            }
            WorkoutVoiceCommands.Command.PAUSE -> {
                pause()
                speak("Đã tạm dừng.")
                return
            }
            WorkoutVoiceCommands.Command.SKIP_REST -> {
                confirmSkipRest()
                return
            }
            else -> Unit
        }
        // Short follow-ups inherit the active rest context only. "Thêm 30 giây nữa"
        // must not start a rest when the workout is moving, and still requires an
        // explicit duration before the existing confirmation dialog can apply it.
        val contextualRestExtension =
            WorkoutRestRequest.contextualExtensionSeconds(utterance, resting)
        if (contextualRestExtension != null) {
            confirmRestExtension(contextualRestExtension)
            return
        }
        when (val parsed = WorkoutMidSessionRequest.parse(utterance)) {
            WorkoutMidSessionRequest.Result.SafetyStop -> {
                stopForSafety()
                return
            }
            is WorkoutMidSessionRequest.Result.Proposed -> {
                // A combined "change reps + rest" utterance is one confirmed action.
                // Do not route a self-reported completed set into the target editor.
                val voiceIntent = WorkoutVoiceCommands.parse(utterance)
                val spokenSetReport = WorkoutSetReportCommand.parse(utterance)
                val spokenTarget = if (parsed.request.isRestOnly && spokenSetReport == null &&
                    voiceIntent in setOf(WorkoutVoiceCommands.Command.REST, WorkoutVoiceCommands.Command.UNKNOWN)
                ) contextualSpokenTarget(utterance) else null
                if (spokenTarget != null) {
                    confirmSpokenTarget(spokenTarget.value, spokenTarget.unit, parsed.request.restSeconds)
                } else {
                    applyMidSessionRequest(parsed.request)
                }
                return
            }
            WorkoutMidSessionRequest.Result.Unsupported -> Unit
        }
        // An explicit report of completed work must be handled before target editing.
        // Otherwise "I did 10 reps" could be mistaken for "change the target to 10 reps".
        val spokenSetReport = WorkoutSetReportCommand.parse(utterance)
        if (spokenSetReport != null) {
            val plan = if (index in moves.indices) currentSetPlan() else null
            when {
                plan == null -> Toast.makeText(
                    this, "Động tác hiện tại không dùng bộ đếm hiệp; Chibi chưa ghi nhận số lần.", Toast.LENGTH_LONG
                ).show()
                plan.unit != spokenSetReport.unit -> Toast.makeText(
                    this, "Đơn vị bạn nói không khớp giáo án (${plan.unit}); Chibi chưa ghi nhận hiệp.", Toast.LENGTH_LONG
                ).show()
                else -> confirmSpokenSetValue(spokenSetReport.value)
            }
            return
        }

        // Safety words always take precedence over any numeric exercise request.
        val spokenTarget = contextualSpokenTarget(utterance)
        if (spokenTarget != null && WorkoutVoiceCommands.mayEditReps(utterance)) {
            confirmSpokenTarget(spokenTarget.value, spokenTarget.unit)
            return
        }

        val clarification = if (!resting && !pausedForSafety && !completed && index in moves.indices) {
            WorkoutMidSessionRequest.clarificationFor(utterance, currentSetPlan()?.unit)
        } else null
        if (clarification != null) {
            val wasRunning = !paused
            pause()
            val dialog = AlertDialog.Builder(this)
                .setTitle("🐰 Chibi cần làm rõ")
                .setMessage(clarification.prompt)
                .setItems(clarification.actions.map(clarification::labelFor).toTypedArray()) { owner, choice ->
                    // Dismiss the clarification surface before opening a second editor/preview dialog.
                    (owner as? AlertDialog)?.dismiss()
                    when (clarification.actions.getOrNull(choice)) {
                        WorkoutMidSessionRequest.ClarificationAction.REDUCE_REPS -> requestRepTarget()
                        WorkoutMidSessionRequest.ClarificationAction.REMOVE_OPTIONAL_SETS ->
                            applyMidSessionRequest(WorkoutMidSessionRequest.Request(removeOptionalSets = true))
                        WorkoutMidSessionRequest.ClarificationAction.EASIER_VARIANT ->
                            applyMidSessionRequest(
                                WorkoutMidSessionRequest.Request(lowEnergy = true, preferEasier = true)
                            )
                        WorkoutMidSessionRequest.ClarificationAction.PAUSE ->
                            speak("Đã tạm dừng. Khi sẵn sàng, mày có thể tiếp tục buổi tập.")
                        null -> Unit
                    }
                }
                .setNegativeButton("Giữ nguyên") { _, _ ->
                    if (wasRunning && !pausedForSafety && !completed && !resting) resume()
                }
                .create()
            dialog.setOnCancelListener {
                if (wasRunning && !pausedForSafety && !completed && !resting) resume()
            }
            dialog.show()
            speak(clarification.spokenText())
            return
        }

        when(WorkoutVoiceCommands.parse(utterance)) {
            WorkoutVoiceCommands.Command.PAIN -> stopForSafety()
            WorkoutVoiceCommands.Command.PAUSE -> { pause();speak("Đã tạm dừng.") }
            WorkoutVoiceCommands.Command.STATUS -> {
                // Freeze the active/rest countdown while the user reads or replays the report.
                if (!paused && !pausedForSafety) pause()
                val liveRestAnswer = if (!pausedForSafety) {
                    WorkoutRestStatusConversation.answer(
                        input = utterance,
                        restIsActive = resting,
                        remainingMs = if (resting) restRemainingMs else null,
                        restIsPaused = resting && paused
                    )
                } else null
                val answer = liveRestAnswer ?: currentWorkoutProgressReply()
                answer?.let {
                    showCoachAnswer("🐰 Chibi Coach • tiến độ buổi tập", it)
                }
            }
            WorkoutVoiceCommands.Command.STEP_GUIDE -> showStepByStepGuide()
            WorkoutVoiceCommands.Command.EXPLAIN_CURRENT -> {
                val explanation = if (pausedForSafety) {
                    "Buổi tập đang dừng vì cảnh báo an toàn. Chibi không đề xuất tiếp tục hoặc đổi bài."
                } else {
                    currentSubstitutionExplanation()
                }
                AlertDialog.Builder(this).setTitle("🐰 Vì sao Chibi gợi ý bài này?")
                    .setMessage(explanation)
                    .setPositiveButton("Đã hiểu", null)
                    .show()
                speak(explanation)
            }
            WorkoutVoiceCommands.Command.COMPLETE_SET -> confirmSpokenSetCompletion()
            WorkoutVoiceCommands.Command.RESUME -> resume()
            WorkoutVoiceCommands.Command.NEXT -> nextMove()
            WorkoutVoiceCommands.Command.PREVIOUS -> { if(!pausedForSafety) { pause();if(index>0)index--;displayStep();speak("Bài "+moves[index].name) } }
            WorkoutVoiceCommands.Command.REPEAT -> speak(moves.getOrNull(index)?.steps.orEmpty())
            WorkoutVoiceCommands.Command.REPEAT_STEP -> {
                val move = moves.getOrNull(index)
                if (move != null && !pausedForSafety) {
                    speak(WorkoutStepGuide.repeatCue(index, lastGuideStepCue, move.name, move.steps))
                }
            }
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
            WorkoutVoiceCommands.Command.SKIP_REST -> confirmSkipRest()
            WorkoutVoiceCommands.Command.NO_EQUIPMENT -> {
                when (val parsed = WorkoutMidSessionRequest.parse(utterance)) {
                    is WorkoutMidSessionRequest.Result.Proposed -> applyMidSessionRequest(parsed.request)
                    WorkoutMidSessionRequest.Result.SafetyStop -> stopForSafety()
                    WorkoutMidSessionRequest.Result.Unsupported -> Toast.makeText(
                        this, "Chưa thể xác định yêu cầu thay đổi dụng cụ.", Toast.LENGTH_LONG
                    ).show()
                }
            }
            WorkoutVoiceCommands.Command.UNKNOWN -> {
                val currentMove = moves.getOrNull(index)
                val questionAnswer = coachConversation.answer(
                    input = utterance,
                    activeMove = currentMove,
                    activeDose = currentMove?.let { targetDose(index) },
                    pausedForSafety = pausedForSafety,
                    restIsActive = resting,
                    restRemainingMs = if (resting) {
                        if (paused) restRemainingMs
                        else (restDeadline - SystemClock.elapsedRealtime()).coerceIn(0L, 600_000L)
                    } else null,
                    restIsPaused = resting && paused,
                    activeCompletedSets = currentMove?.let { completedSetsByMove[index] ?: 0 },
                    activeCompletedSetValues = currentMove?.let { actualSetValuesByMove[index]?.toList() },
                    activeEffortLevels = currentMove?.let { effortLevelsByMove[index]?.toList() },
                    nextMove = moves.getOrNull(index + 1)
                )
                if (questionAnswer != null) {
                    // Keep the exercise/rest clock from advancing while the user reads or hears
                    // a catalog-grounded answer. The user explicitly resumes when ready.
                    if (!paused && !pausedForSafety) pause()
                    showCoachAnswer("🐰 Chibi Coach • trả lời bài tập", questionAnswer)
                } else {
                    AlertDialog.Builder(this).setTitle("Chibi chưa hiểu yêu cầu")
                        .setMessage("Mày có thể hỏi cách tập bài đang mở, nhóm cơ, dụng cụ, số lần hoặc thời gian nghỉ; hoặc nói: tôi đã làm 10 lần, xong hiệp, đổi bài, nghỉ thêm, đọc tiến độ, tạm dừng hay tiếp tục. Các thay đổi giáo án vẫn cần xác nhận.")
                        .setPositiveButton("Đóng",null).show()
                }
            }
        }
    }
    /**
     * Skip only the remaining rest interval after explicit confirmation.
     * The exercise timer is preserved and the workout remains paused afterwards.
     */
    private fun confirmSkipRest() {
        if (pausedForSafety || completed || !resting || index !in moves.indices) {
            Toast.makeText(this, "Hiện không có thời gian nghỉ để bỏ qua.", Toast.LENGTH_LONG).show()
            return
        }
        val wasRunning = !paused
        if (wasRunning) pause()
        val selectedSessionId = sessionId
        val selectedIndex = index
        val exerciseBefore = remainingMs
        val restBefore = restRemainingMs
        val dialog = AlertDialog.Builder(this)
            .setTitle("⏭ Bỏ qua thời gian nghỉ?")
            .setMessage(
                "Còn khoảng ${((restBefore + 999L) / 1_000L).coerceAtMost(600L)} giây nghỉ. " +
                    "Nếu xác nhận, bộ đếm động tác (${exerciseBefore / 1_000L} giây) được giữ nguyên. " +
                    "Chỉ bỏ qua khi bạn đã hồi phục; nếu còn mệt, hãy giữ thời gian nghỉ."
            )
            .setPositiveButton("Bỏ qua nghỉ") { _, _ ->
                if (pausedForSafety || completed || !resting || sessionId != selectedSessionId ||
                    index != selectedIndex || remainingMs != exerciseBefore || restRemainingMs != restBefore
                ) {
                    Toast.makeText(this, "Buổi tập đã thay đổi. Hãy kiểm tra lại trước khi bỏ qua nghỉ.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val next = runCatching {
                    WorkoutRestSession.skip(WorkoutRestSession.State(exerciseBefore, restBefore))
                }.getOrElse {
                    Toast.makeText(this, "Không thể xác minh bộ đếm nghỉ; thời gian cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                remainingMs = next.exerciseRemainingMs
                restRemainingMs = next.restRemainingMs
                restTenSecondCueGiven = false
                resting = false
                restDeadline = 0L
                paused = true
                startedAt = 0L
                handler.removeCallbacks(tick)
                renderSetControls()
                control.text = "▶ Tiếp tục"
                counter.text = clockText(remainingMs)
                saveCheckpoint()
                speak("Đã bỏ qua thời gian nghỉ. Nhấn tiếp tục khi sẵn sàng.")
            }
            .setNegativeButton("Giữ thời gian nghỉ") { _, _ ->
                if (wasRunning && !pausedForSafety && !completed && resting &&
                    sessionId == selectedSessionId && index == selectedIndex && remainingMs == exerciseBefore
                ) resume()
            }
            .create()
        dialog.setOnCancelListener {
            if (wasRunning && !pausedForSafety && !completed && resting &&
                sessionId == selectedSessionId && index == selectedIndex && remainingMs == exerciseBefore
            ) resume()
        }
        dialog.show()
    }

    /**
     * Extend an existing rest countdown only after a preview and explicit confirmation.
     * The exercise countdown is preserved and the combined rest is capped at ten minutes.
     */
    private fun confirmRestExtension(addedSeconds: Int) {
        if (pausedForSafety || completed || !resting || index !in moves.indices) return
        val wasRunning = !paused
        pause()
        val beforeRemaining = restRemainingMs
        val preview = WorkoutRestAdjustment.previewExtension(beforeRemaining, addedSeconds)
        if (preview == null) {
            Toast.makeText(
                this,
                "Thời gian nghỉ đã đạt giới hạn 10 phút hoặc yêu cầu không hợp lệ.",
                Toast.LENGTH_LONG
            ).show()
            if (wasRunning && resting && !pausedForSafety) resume()
            return
        }

        val selectedIndex = index
        val exerciseRemaining = remainingMs
        val addedSecondsActual = ((preview.actualAddedMs + 999L) / 1_000L).toInt()
        val totalSeconds = ((preview.afterRemainingMs + 999L) / 1_000L).toInt()
        val capNotice = if (preview.wasCapped) {
            "\\nTổng thời gian nghỉ được giới hạn tối đa 10 phút."
        } else {
            ""
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("⏱ Xác nhận kéo dài thời gian nghỉ")
            .setMessage(
                "Thời gian nghỉ còn: ${(beforeRemaining + 999L) / 1_000L} giây → ${totalSeconds} giây.\\n" +
                    "Thêm thực tế: ${addedSecondsActual} giây.\\n" +
                    "Thời gian động tác còn lại được giữ nguyên; nghỉ không tính vào phút vận động." +
                    capNotice
            )
            .setPositiveButton(if (wasRunning) "Thêm & tiếp tục nghỉ" else "Thêm, giữ tạm dừng") { _, _ ->
                if (pausedForSafety || completed || !resting || !paused ||
                    index != selectedIndex || restRemainingMs != beforeRemaining ||
                    remainingMs != exerciseRemaining
                ) {
                    Toast.makeText(
                        this,
                        "Bộ đếm đã thay đổi. Hãy yêu cầu lại để xem trước thời gian mới.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                restRemainingMs = preview.afterRemainingMs
                if (restRemainingMs > WorkoutRestVoiceCue.WARNING_WINDOW_MS) restTenSecondCueGiven = false
                restDeadline = 0L
                counter.text = restCounterText(restRemainingMs)
                control.text = "▶ Tiếp tục nghỉ"
                saveCheckpoint()
                speak("Đã thêm ${addedSecondsActual} giây nghỉ. Còn ${totalSeconds} giây.")
                if (wasRunning) {
                    resume()
                } else {
                    // Extending a paused rest must not silently restart its countdown.
                    paused = true
                    control.text = "▶ Tiếp tục nghỉ"
                    counter.text = restCounterText(restRemainingMs)
                }
            }
            .setNegativeButton("Giữ nguyên") { _, _ ->
                if (wasRunning && resting && !pausedForSafety) resume()
            }
            .create()
        dialog.setOnCancelListener {
            if (wasRunning && resting && !pausedForSafety) resume()
        }
        dialog.show()
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

    /** Manual target edits apply only to unfinished sets; confirmed set values remain immutable. */
    private fun requestRepTarget() {
        if (pausedForSafety || completed || index !in moves.indices) return
        val selected = index
        val plan = currentSetPlan() ?: run {
            Toast.makeText(this, "Động tác hiện tại không có mục tiêu hiệp/lần để chỉnh.", Toast.LENGTH_LONG).show()
            return
        }
        val completedBefore = (completedSetsByMove[selected] ?: 0).coerceIn(0, plan.maximumSets)
        if (completedBefore >= plan.maximumSets) {
            Toast.makeText(this, "Động tác đã đủ số hiệp; không còn hiệp nào để đổi mục tiêu.", Toast.LENGTH_LONG).show()
            return
        }
        val wasResting = resting
        val wasRestRunning = wasResting && !paused
        pause()
        val before = repTargets[selected]
        val beforeRemainingMs = remainingMs
        val beforeRestRemainingMs = if (wasResting) restRemainingMs else 0L
        val selectedMove = moves[selected]
        val selectedSessionId = sessionId
        val initialValue = WorkoutRemainingRepTarget.initialValue(before, plan.targetMin)
        val currentDose = before?.let { "$it ${plan.unit} (đã điều chỉnh)" } ?: run {
            val prescribed = if (plan.targetMin == plan.targetMax) "${plan.targetMin}"
                else "${plan.targetMin}–${plan.targetMax}"
            "$prescribed ${plan.unit}"
        }
        val field=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            hint="Mục tiêu ${plan.unit} từ 1 đến 100"
            setText(initialValue?.toString().orEmpty())
            selectAll()
        }
        val disclosure = "Đã ghi nhận $completedBefore/${plan.maximumSets} hiệp. Mục tiêu hiện tại: $currentDose. " +
            "Mục tiêu mới chỉ áp dụng cho ${plan.maximumSets - completedBefore} hiệp còn lại; dữ liệu hiệp đã ghi không đổi."
        val dialog=AlertDialog.Builder(this).setTitle("🔢 Chọn mục tiêu mỗi hiệp")
            .setMessage("$disclosure Chibi chưa tự đếm ${plan.unit}.")
            .setView(field).setPositiveButton("Xem trước",null)
            .setNegativeButton("Hủy") { _, _ ->
                if (wasRestRunning && resting && paused && !pausedForSafety) resume()
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reps=field.text.toString().trim().toIntOrNull()
                if (reps==null || reps !in 1..100) {
                    field.error="Nhập từ 1 đến 100 ${plan.unit}"
                } else {
                    val proposal = WorkoutRemainingRepTarget.preview(
                        before, completedBefore, plan.maximumSets, reps
                    )
                    if (proposal == null) {
                        field.error = "Mục tiêu này đã được chọn hoặc không hợp lệ"
                    } else {
                        dialog.dismiss()
                        val previewText = "${selectedMove.name}\n${targetDose(selected)} → $reps ${plan.unit}.\n" +
                            "$disclosure Không sửa giá trị hiệp đã xác nhận hoặc thời gian vận động."
                        val confirm = AlertDialog.Builder(this).setTitle("Xác nhận mục tiêu còn lại")
                            .setMessage(previewText)
                            .setPositiveButton("Áp dụng") { _, _ ->
                                val currentDone = completedSetsByMove[selected] ?: 0
                                val currentMaximum = currentSetPlan()?.maximumSets ?: -1
                                val stale = completed || pausedForSafety || index != selected ||
                                    sessionId != selectedSessionId || moves.getOrNull(selected) != selectedMove ||
                                    repTargets[selected] != before || currentDone != completedBefore ||
                                    currentMaximum != plan.maximumSets || remainingMs != beforeRemainingMs ||
                                    resting != wasResting ||
                                    (wasResting && restRemainingMs != beforeRestRemainingMs)
                                val accepted = if (stale) null else WorkoutRemainingRepTarget.accept(
                                    proposal, repTargets[selected], currentDone, currentMaximum
                                )
                                if (accepted == null) {
                                    Toast.makeText(this,"Buổi tập đã thay đổi. Hãy xem trước lại.",Toast.LENGTH_LONG).show()
                                    if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                                } else {
                                    repTargets[selected] = accepted
                                    renderDose()
                                    renderSetControls()
                                    saveCheckpoint()
                                    speak("Mục tiêu mới là $reps ${plan.unit} cho ${proposal.remainingSets} hiệp còn lại. Các hiệp đã ghi được giữ nguyên.")
                                    if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                                }
                            }
                            .setNegativeButton("Giữ nguyên") { _, _ ->
                                if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                            }
                            .create()
                        confirm.setOnCancelListener {
                            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
                        }
                        confirm.show()
                    }
                }
            }
        }
        dialog.setOnCancelListener {
            if (wasRestRunning && resting && paused && !pausedForSafety) resume()
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
    /** Explain the active substitution from the local catalog; never infer a medical reason. */
    private fun currentSubstitutionExplanation(): String {
        val original = originalMoves.getOrNull(index)
        val replacement = substitutions[index]
        if (original == null || replacement == null) {
            return "Động tác hiện tại chưa được thay thế. Nếu muốn xem phương án khác, hãy nói: đổi bài dễ hơn."
        }
        val reason = WorkoutMoveSubstitution.explanationFor(original, replacement)
            ?: return "Đây là biến thể đã có trong thư viện bài tập. Chibi chưa có giải thích riêng cho cặp động tác này."
        return "Chibi gợi ý " + replacement.name + " thay cho " + original.name + " vì: " + reason +
            " Đây là giải thích về cách tổ chức động tác, không phải chẩn đoán hay bảo đảm phù hợp với mọi tình trạng sức khỏe."
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
        val options = WorkoutMoveSubstitution.alternativesFor(moves[index],workoutConstraints,availableWorkoutEquipment())
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
                val reason = WorkoutMoveSubstitution.explanationFor(original, replacement)
                val reasonText = reason?.let { "Vì sao Chibi gợi ý: " + it + "\n\n" }.orEmpty()
                AlertDialog.Builder(this).setTitle("Xác nhận thay động tác")
                    .setMessage(original.name + " → " + replacement.name + "\n\n" + reasonText +
                        "Mục tiêu: " + replacement.dose + "\n" + restGuidance +
                        "Cách tập: " + replacement.steps + "\nDễ hơn nữa: " + replacement.easier +
                        "\nLưu ý: " + replacement.avoid +
                        "\n\nChỉ thay bước hiện tại chưa được ghi nhận hoàn thành. Các bước khác và thời gian còn lại được giữ nguyên. Chỉ chọn nếu phù hợp với cơ thể bạn; đây là gợi ý offline, không phải tư vấn điều trị.")
                    .setPositiveButton("Đổi động tác") { _, _ ->
                        if (pausedForSafety || completed || resting || index != selectedIndex ||
                            index !in moves.indices || (completedSetsByMove[index] ?: 0) > 0 ||
                            index in completedMoveIndices || index in substitutions
                        ) {
                            Toast.makeText(this, "Buổi tập đã thay đổi. Hãy mở lại phần điều chỉnh.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        val updated = WorkoutMoveSubstitution.replace(moves, selectedIndex, replacement, workoutConstraints, availableWorkoutEquipment())
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
                        speak(currentSubstitutionExplanation() + " Thời gian còn lại được giữ nguyên. Hãy kiểm tra hướng dẫn trước khi tiếp tục.")
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

        val previewIndex = index
        val previewSessionId = sessionId
        val previewRemainingMs = remainingMs
        val previewShorterFrom = shorterFromIndex
        val previewResting = resting
        val previewRestRemainingMs = restRemainingMs
        val previewCompletedMoves = completedMoveIndices.toSet()
        val previewCompletedSets = completedSetsByMove.toMap()
        val previewMoves = moves.toList()

        fun estimate(activeRemainingMs: Long, shorterFrom: Int): Int =
            WorkoutSessionTimeEstimate.seconds(
                moves = previewMoves,
                currentIndex = previewIndex,
                completedMoves = previewCompletedMoves,
                completedSets = previewCompletedSets,
                currentExerciseRemainingSeconds = RemainingWorkoutDuration.ceilSeconds(activeRemainingMs),
                resting = previewResting,
                restSecondsRemaining = if (previewResting) RemainingWorkoutDuration.ceilSeconds(previewRestRemainingMs) else 0,
                level = workoutLevel,
                shorterFromIndex = shorterFrom
            )

        val beforeSeconds = estimate(previewRemainingMs, previewShorterFrom)
        val proposedFrom = minOf(previewShorterFrom, previewIndex)
        val afterCurrent = RemainingWorkoutDuration.shortenActiveMs(previewRemainingMs)
        val afterSeconds = estimate(afterCurrent, proposedFrom)

        fun format(seconds: Int): String = "${seconds / 60} phút ${seconds % 60} giây"
        val stepPreview = RemainingWorkoutDuration.preview(
            previewMoves.map { it.name },
            previewMoves.map { WorkoutDurationPlanner.activeSeconds(it) },
            previewIndex,
            previewRemainingMs,
            previewShorterFrom
        )
        val details = stepPreview.take(4).mapIndexed { offset, step ->
            val move = previewMoves.getOrNull(previewIndex + offset)
            val prescribedRest = move?.let { WorkoutDurationPlanner.prescribedRestSeconds(it) } ?: 0
            val restLabel = if (prescribedRest > 0) " + nghỉ theo giáo án ${prescribedRest}s" else ""
            "• ${step.name}: ${step.beforeSeconds}s → ${step.afterSeconds}s$restLabel"
        }.joinToString("\n") + if (stepPreview.size > 4) {
            "\n… và ${stepPreview.size - 4} bước tiếp theo"
        } else ""

        AlertDialog.Builder(this).setTitle("Xem trước giáo án rút ngắn")
            .setMessage(
                "Thời gian còn lại ước tính (gồm nghỉ theo giáo án và chuyển bước):\n" +
                    "Trước: ${format(beforeSeconds)}\nSau: ${format(afterSeconds)}\n\n" +
                    "Chỉ giảm thời lượng vận động của phần chưa hoàn thành; không rút ngắn số hiệp, số lần " +
                    "hoặc thời gian nghỉ theo giáo án. Nghỉ thêm ngoài giáo án có thể làm thời gian thực tế dài hơn.\n\n" +
                    details
            )
            .setPositiveButton("Xác nhận thay đổi") { _, _ ->
                if (pausedForSafety || completed || index != previewIndex ||
                    sessionId != previewSessionId || index !in moves.indices ||
                    remainingMs != previewRemainingMs || shorterFromIndex != previewShorterFrom ||
                    resting != previewResting || restRemainingMs != previewRestRemainingMs ||
                    moves != previewMoves || completedMoveIndices != previewCompletedMoves ||
                    completedSetsByMove != previewCompletedSets
                ) {
                    Toast.makeText(this, "Buổi tập đã thay đổi. Hãy xem trước lại.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                shorterFromIndex = proposedFrom
                remainingMs = afterCurrent
                val seconds = (remainingMs / 1000).toInt()
                counter.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                control.text = "▶ Tiếp tục giáo án ngắn hơn"
                saveCheckpoint()
                speak("Đã rút ngắn thời lượng vận động còn lại. Thời gian nghỉ và mục tiêu hiệp vẫn được giữ nguyên.")
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
    /**
     * Review bounded recognizer alternatives instead of silently trusting the first guess.
     * A safety symptom in the selected prefix still stops immediately; long/truncated
     * non-safety text must be edited before any workout command can be applied.
     */
    private fun reviewWorkoutVoiceCandidate(candidate: CoachSpeechTranscript.Candidate) {
        val phrase = candidate.text.trim()
        if (phrase.isBlank()) {
            Toast.makeText(this, "Câu nhận dạng đang trống.", Toast.LENGTH_SHORT).show()
            return
        }
        val review = WorkoutVoiceCandidatePolicy.review(candidate.copy(text = phrase))
        if (review.command == WorkoutVoiceCommands.Command.PAIN) {
            handleCoachCommand(phrase)
            return
        }
        if (review.requiresEdit) {
            editWorkoutVoiceCandidate(
                phrase,
                "Câu nhận dạng vượt giới hạn 160 ký tự hoặc đã được rút gọn. Chibi chưa thực hiện lệnh. Hãy rút gọn hoặc sửa lại ý chính; nếu đang đau hay khó thở, hãy dừng tập ngay."
            )
            return
        }
        AlertDialog.Builder(this).setTitle("🐰 Chibi nghe được")
            .setMessage("“$phrase”\n\n" + WorkoutVoiceCommands.describe(review.command, phrase) +
                "\n\nKiểm tra câu nhận dạng trước khi điều khiển buổi tập.")
            .setPositiveButton("Áp dụng") { _, _ -> handleCoachCommand(phrase) }
            .setNeutralButton("Sửa câu nhận dạng") { _, _ ->
                editWorkoutVoiceCandidate(phrase, "Chỉnh sửa câu nhận dạng rồi xác nhận trước khi áp dụng.")
            }
            .setNegativeButton("Không đúng", null)
            .show()
    }

    private fun editWorkoutVoiceCandidate(phrase: String, disclosure: String) {
        val corrected = EditText(this).apply {
            setText(phrase)
            setSelection(text.length)
            setSingleLine(false)
            minLines = 3
        }
        AlertDialog.Builder(this).setTitle("Sửa yêu cầu gửi Chibi")
            .setMessage(disclosure + "\n\nGiới hạn lệnh điều khiển là ${WorkoutVoiceCandidatePolicy.MAX_COMMAND_CHARS} ký tự.")
            .setView(ScrollView(this).apply { addView(corrected) })
            .setPositiveButton("Kiểm tra câu đã sửa") { _, _ ->
                val edited = corrected.text.toString().trim()
                when {
                    edited.isBlank() -> Toast.makeText(
                        this, "Câu sửa đang trống; buổi tập chưa thay đổi.", Toast.LENGTH_SHORT
                    ).show()
                    edited.length > WorkoutVoiceCandidatePolicy.MAX_COMMAND_CHARS -> Toast.makeText(
                        this, "Hãy rút gọn yêu cầu còn tối đa 160 ký tự rồi thử lại.", Toast.LENGTH_LONG
                    ).show()
                    else -> reviewWorkoutVoiceCandidate(
                        CoachSpeechTranscript.Candidate(edited, truncated = false)
                    )
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Review every recognizer alternative before using it. Safety reports take priority even
     * when they appear after an ordinary first guess; ordinary choices never change a workout
     * until the caller's own confirmation flow accepts them.
     */
    private fun reviewSpeechAlternatives(
        raw: List<String>?,
        title: String,
        message: String,
        emptyMessage: String,
        isSafety: (String) -> Boolean,
        onSelected: (CoachSpeechTranscript.Candidate) -> Unit
    ) {
        val candidates = CoachSpeechTranscript.candidates(raw)
        if (candidates.isEmpty()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_LONG).show()
            return
        }

        // Never hide a safety symptom behind a less urgent alternative from the recognizer.
        val safetyCandidate = candidates.firstOrNull { isSafety(it.text) }
        if (safetyCandidate != null) {
            onSelected(safetyCandidate)
            return
        }

        if (candidates.size == 1) {
            onSelected(candidates.single())
            return
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setItems(candidates.mapIndexed { index, candidate ->
                "${index + 1}. ${candidate.label}"
            }.toTypedArray()) { _, which ->
                candidates.getOrNull(which)?.let(onSelected)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == voiceRequest || requestCode == guideVoiceRequest ||
            requestCode == effortVoiceRequest || requestCode == previewVoiceRequest) {
            if (pendingRecognitionRequestCode == requestCode) pendingRecognitionRequestCode = null
            updateRestStepSpeakButton()
            // Post until the recognizer callback has finished processing its selected result.
            handler.post { drainPendingRestCompletionSpeech() }
        }
        if (requestCode == guideVoiceRequest) {
            val callback = guideVoiceResultHandler
            guideVoiceResultHandler = null
            if (resultCode == RESULT_OK && callback != null) {
                reviewSpeechAlternatives(
                    data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
                    title = "🐰 Chọn câu Chibi nghe được",
                    message = "Dịch vụ nhận dạng đưa ra nhiều phương án. Chọn câu đúng trước khi chuyển bước hoặc điều khiển hướng dẫn.",
                    emptyMessage = "Không nhận được câu nói rõ ràng. Bạn vẫn có thể dùng các nút hướng dẫn.",
                    isSafety = { WorkoutVoiceCommands.parse(it) == WorkoutVoiceCommands.Command.PAIN },
                    onSelected = { candidate ->
                        val phrase = candidate.text
                        if (WorkoutVoiceCommands.parse(phrase) == WorkoutVoiceCommands.Command.PAIN) {
                            handleCoachCommand(phrase)
                        } else {
                            callback(phrase)
                        }
                    }
                )
            } else if (resultCode != RESULT_OK) {
                Toast.makeText(this,"Đã hủy nhận giọng nói; buổi tập vẫn tạm dừng.",Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == effortVoiceRequest) {
            val callback = effortVoiceResultHandler
            effortVoiceResultHandler = null
            if (resultCode == RESULT_OK && callback != null) {
                reviewSpeechAlternatives(
                    data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
                    title = "🐰 Chọn câu trả lời về hiệp tập",
                    message = "Chọn câu gần đúng nhất. Chibi sẽ đọc lại mức cảm nhận và vẫn chờ bạn xác nhận trước khi lưu.",
                    emptyMessage = "Chưa nhận được câu trả lời; bạn vẫn có thể chọn cảm nhận bằng nút.",
                    // This contextual parser avoids treating the anatomical phrase "đầu gối"
                    // as a pain report, while still prioritizing explicit symptoms.
                    isSafety = { WorkoutEffortVoiceCommand.parse(it) == WorkoutEffortVoiceCommand.Result.SafetyStop },
                    onSelected = { candidate -> callback(candidate.text) }
                )
            } else if (resultCode != RESULT_OK) {
                Toast.makeText(this,"Chưa nhận được câu trả lời; bạn vẫn có thể chọn cảm nhận bằng nút.",Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == previewVoiceRequest) {
            val callback = previewVoiceResultHandler
            previewVoiceResultHandler = null
            if (resultCode == RESULT_OK && callback != null) {
                val raw = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val candidates = CoachSpeechTranscript.candidates(raw)
                val agreedAction = WorkoutPlanPreviewVoiceCommand.parseAlternatives(
                    candidates.map { it.text }
                )
                when (agreedAction) {
                    WorkoutPlanPreviewVoiceCommand.Action.SAFETY -> {
                        candidates.firstOrNull {
                            WorkoutPlanPreviewVoiceCommand.parse(it.text) ==
                                WorkoutPlanPreviewVoiceCommand.Action.SAFETY
                        }?.let { callback(it.text) }
                    }
                    WorkoutPlanPreviewVoiceCommand.Action.CONFIRM,
                    WorkoutPlanPreviewVoiceCommand.Action.CANCEL -> {
                        // All bounded recognizer alternatives agree, so no extra transcript tap is needed.
                        candidates.firstOrNull()?.let { callback(it.text) }
                    }
                    WorkoutPlanPreviewVoiceCommand.Action.UNSUPPORTED -> {
                        reviewSpeechAlternatives(
                            raw,
                            title = "🐰 Chọn câu xác nhận Chibi nghe được",
                            message = "Các phương án nhận dạng chưa thống nhất. Chọn câu đúng; Chibi vẫn yêu cầu xác nhận giọng nói lần hai trước khi áp dụng.",
                            emptyMessage = "Chưa nhận được câu nói rõ ràng; bản xem trước vẫn giữ nguyên.",
                            isSafety = { WorkoutVoiceCommands.parse(it) == WorkoutVoiceCommands.Command.PAIN },
                            onSelected = { candidate -> callback(candidate.text) }
                        )
                    }
                }
            } else if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Đã hủy nhận giọng nói; giáo án xem trước vẫn chưa thay đổi.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == voiceRequest) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Đã hủy nhận giọng nói; buổi tập vẫn tạm dừng.", Toast.LENGTH_SHORT).show()
                return
            }
            reviewSpeechAlternatives(
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
                title = "🐰 Chọn câu Chibi nghe được",
                message = "Dịch vụ nhận dạng đưa ra nhiều phương án. Chọn câu đúng trước khi điều khiển buổi tập.",
                emptyMessage = "Không nhận được câu nói rõ ràng. Bạn có thể nhập yêu cầu bằng chữ.",
                isSafety = { WorkoutVoiceCommands.parse(it) == WorkoutVoiceCommands.Command.PAIN },
                onSelected = ::reviewWorkoutVoiceCandidate
            )
            return
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
    /**
     * Shows a catalog-grounded breathing cue on demand. Reading is user-initiated,
     * uses the existing local TTS engine, and never opens the microphone.
     */
    private fun showBreathingGuide() {
        if (pausedForSafety) {
            Toast.makeText(
                this,
                "Buổi tập đang dừng vì đau; hãy nghỉ, không phát hướng dẫn vận động.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val selectedIndex = index
        val move = moves.getOrNull(selectedIndex) ?: return
        val cue = WorkoutBreathingGuide.cue(move.name)
        val dialog = AlertDialog.Builder(this)
            .setTitle("🌬️ Nhịp thở • " + move.name)
            .setMessage(
                cue + "\n\nGợi ý chung, không đánh giá tư thế hoặc thay thế tư vấn y tế."
            )
            .setNegativeButton("Đóng", null)
            .setPositiveButton("🔊 Nghe hướng dẫn", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when {
                    pausedForSafety -> {
                        dialog.dismiss()
                        Toast.makeText(
                            this@GuidedWorkoutActivity,
                            "Buổi tập đang dừng vì đau; không phát hướng dẫn vận động.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    pendingRecognitionRequestCode != null -> Toast.makeText(
                        this@GuidedWorkoutActivity,
                        "Đang nhận giọng nói; hãy đóng microphone trước khi nghe hướng dẫn.",
                        Toast.LENGTH_LONG
                    ).show()
                    index != selectedIndex || moves.getOrNull(selectedIndex)?.name != move.name -> {
                        dialog.dismiss()
                        Toast.makeText(
                            this@GuidedWorkoutActivity,
                            "Động tác đã thay đổi. Hãy mở lại hướng dẫn cho bài hiện tại.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    !speakCoachAnswer(cue) -> Toast.makeText(
                        this@GuidedWorkoutActivity,
                        "Giọng đọc chưa sẵn sàng; hướng dẫn vẫn hiển thị trên màn hình.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        dialog.show()
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
        // Latest catalog-grounded answer is kept only while this guide dialog is open.
        var lastCoachAnswer: String? = null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 12)
        }
        // The modal gets its own exercise animation. Pause the background copy to
        // avoid running two animators for the same movement while the guide is open.
        poseView.motionEnabled = false
        val pose = WorkoutPoseGuide.classify(move.name)
        val phaseCaption = TextView(this).apply {
            tag = "workout-step-phase-caption"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 2, 8, 10)
            setTextColor(Color.rgb(82, 66, 105))
            text = "Pha: ${WorkoutPoseMotion.phaseCue(pose, false)} · Hình minh họa, không đánh giá tư thế."
            contentDescription = text.toString()
        }
        val illustration = WorkoutPoseView(this).apply {
            tag = "workout-step-pose-preview"
            this.pose = pose
        }
        val stepProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = walkthrough.steps.size
            progress = walkthrough.position
            contentDescription = "Tiến độ hướng dẫn động tác"
        }
        var autoSpeakOnStepChange = false
        var lastRenderedPosition: Int? = null
        val autoSpeakToggle = CheckBox(this).apply {
            tag = "workout-step-auto-speak"
            text = "🔊 Tự đọc bước khi chuyển"
            contentDescription = "Bật hoặc tắt đọc tự động mỗi khi chuyển bước hướng dẫn"
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                autoSpeakOnStepChange = checked
                if (checked) {
                    lastGuideStepCue
                        ?.takeIf { it.moveIndex == index }
                        ?.speech
                        ?.let(::speak)
                } else {
                    spoken?.stop()
                }
            }
        }
        val label = TextView(this).apply {
            tag = "workout-step-label"
            textSize = 18f
            setPadding(8, 24, 8, 24)
            setTextColor(Color.rgb(70, 61, 85))
        }
        val previousButton = Button(this).apply {
            text = "← Bước trước"
            contentDescription = "Quay lại bước hướng dẫn trước"
            isEnabled = false
        }
        val voiceButton = Button(this).apply {
            tag = "workout-step-voice-control"
            text = "🎤 Nói: bước tiếp, bước trước, quay về bước đầu, hỏi bước 2 là gì, hỏi cách tập, hỏi dụng cụ, hỏi nhóm cơ, nghỉ bao lâu, nghỉ thêm, đọc cách tập dễ hơn, lưu ý an toàn, đọc tiến độ buổi tập, đọc lại câu trả lời, bật tự đọc, tắt tự đọc, điều chỉnh giáo án"
            contentDescription = "Mở nhận dạng giọng nói sau khi xác nhận quyền micro. Lệnh hỗ trợ: bước tiếp, bước trước, quay về bước đầu, hỏi bước 2 là gì, hỏi cách tập, hỏi dụng cụ, hỏi nhóm cơ, nghỉ bao lâu, nghỉ thêm, đọc cách tập dễ hơn, đọc lưu ý an toàn, đọc tiến độ buổi tập, đọc lại câu trả lời, bật hoặc tắt tự đọc, yêu cầu đổi bài nhẹ hơn hoặc điều chỉnh phần còn lại của giáo án. Chibi sẽ xem trước và chờ xác nhận, không tự áp dụng. Nếu đau, khó thở hoặc chóng mặt, hãy nói rõ để dừng buổi tập."
        }
        container.addView(illustration, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (140 * resources.displayMetrics.density).toInt()
        ))
        val workoutSummary = TextView(this).apply {
            tag = "workout-step-workout-summary"
            textSize = 14f
            setPadding(10, 8, 10, 12)
            text = WorkoutStepGuide.workoutSummary(
                dose = move.dose,
                plan = currentSetPlan(),
                completedSets = completedSetsByMove[index] ?: 0
            )
            contentDescription = text.toString()
        }
        container.addView(phaseCaption)
        container.addView(workoutSummary)
        container.addView(stepProgress)
        container.addView(autoSpeakToggle)
        container.addView(voiceButton)
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
            val previousPosition = lastRenderedPosition
            lastGuideStepCue = WorkoutStepGuide.SelectedCue(
                moveIndex = index,
                speech = WorkoutStepGuide.spokenCue(move.name, move.steps, walkthrough.position)
            )
            label.text = "Bước ${walkthrough.position}/${walkthrough.steps.size}\n\n" +
                "${walkthrough.currentStep}\n\n🌿 Dễ hơn: ${move.easier}\n⚠️ ${move.avoid}"
            label.contentDescription =
                "Bước ${walkthrough.position} trên ${walkthrough.steps.size}. ${walkthrough.currentStep}"
            stepProgress.progress = walkthrough.position
            val selectedPhase = WorkoutPoseMotion.guidePhaseForStep(pose, walkthrough.currentStep)
            illustration.showGuideFrame(selectedPhase ?: false)
            val phaseText = selectedPhase?.let {
                "Pha minh họa: ${WorkoutPoseMotion.phaseCue(pose, it)}"
            } ?: "Tư thế minh họa cho bước này"
            phaseCaption.text = "$phaseText · Hình minh họa, không đánh giá tư thế."
            phaseCaption.contentDescription = phaseCaption.text
            previousButton.isEnabled = walkthrough.canGoPrevious
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = walkthrough.canGoNext
                text = if (walkthrough.canGoNext) "Bước tiếp →" else "Đã xem hết"
            }
            val shouldSpeak = WorkoutStepGuide.shouldSpeakOnNavigation(
                autoSpeakOnStepChange, previousPosition, walkthrough.position
            )
            lastRenderedPosition = walkthrough.position
            if (shouldSpeak) {
                lastGuideStepCue?.speech?.let(::speak)
            }
        }
        // Populate the first step before the dialog is attached. This also keeps
        // accessibility text available to Robolectric and screen readers immediately.
        renderStep()
        voiceButton.setOnClickListener {
            requestGuideVoiceCommand { phrase ->
                when (WorkoutStepVoiceCommand.parse(phrase)) {
                    WorkoutStepVoiceCommand.Command.NEXT -> {
                        if (walkthrough.canGoNext) {
                            spoken?.stop()
                            walkthrough = walkthrough.next()
                            renderStep()
                        } else Toast.makeText(this,"Đã ở bước cuối của hướng dẫn.",Toast.LENGTH_SHORT).show()
                    }
                    WorkoutStepVoiceCommand.Command.PREVIOUS -> {
                        if (walkthrough.canGoPrevious) {
                            spoken?.stop()
                            walkthrough = walkthrough.previous()
                            renderStep()
                        } else Toast.makeText(this,"Đã ở bước đầu của hướng dẫn.",Toast.LENGTH_SHORT).show()
                    }
                    WorkoutStepVoiceCommand.Command.FIRST -> {
                        spoken?.stop()
                        walkthrough = walkthrough.first()
                        renderStep()
                        Toast.makeText(
                            this,
                            "Đã về bước đầu của hướng dẫn; tiến độ buổi tập được giữ nguyên.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    WorkoutStepVoiceCommand.Command.PAUSE -> {
                        // The guide deliberately pauses the timer before microphone capture.
                        // This command confirms that state without changing progress or rewards.
                        pause()
                        Toast.makeText(
                            this,
                            "Buổi tập đang tạm dừng. Nói “tiếp tục buổi tập” khi sẵn sàng.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    WorkoutStepVoiceCommand.Command.RESUME -> {
                        when {
                            completed -> Toast.makeText(
                                this, "Buổi tập đã kết thúc; không thể tiếp tục phiên này.", Toast.LENGTH_LONG
                            ).show()
                            pausedForSafety || paused -> resume()
                            else -> Toast.makeText(
                                this, "Buổi tập đang chạy.", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    WorkoutStepVoiceCommand.Command.REPEAT_ANSWER -> {
                        val answer = lastCoachAnswer
                        if (answer.isNullOrBlank()) {
                            Toast.makeText(this, "Chưa có câu trả lời nào trong hướng dẫn này để đọc lại.", Toast.LENGTH_SHORT).show()
                        } else {
                            speak(answer)
                        }
                    }
                    WorkoutStepVoiceCommand.Command.REPEAT -> {
                        val cue = lastGuideStepCue
                            ?.takeIf { it.moveIndex == index }
                            ?.speech
                            ?: WorkoutStepGuide.spokenCue(move.name,move.steps,walkthrough.position)
                        speak(cue)
                    }
                    WorkoutStepVoiceCommand.Command.STATUS -> {
                        val reply = currentWorkoutProgressReply()
                            ?: "Chibi chưa thể đọc tiến độ vì buổi tập chưa có động tác hợp lệ."
                        lastCoachAnswer = reply.take(1_200)
                        speak(reply)
                    }
                    WorkoutStepVoiceCommand.Command.SUMMARY -> {
                        val summary = WorkoutStepGuide.workoutSummary(
                            dose = targetDose(index),
                            plan = currentSetPlan(),
                            completedSets = completedSetsByMove[index] ?: 0
                        )
                        lastCoachAnswer = summary
                        speak(summary)
                    }
                    WorkoutStepVoiceCommand.Command.OPEN_EASIER_ALTERNATIVES -> {
                        // Leave the walkthrough before opening a second dialog. The existing
                        // substitution flow re-checks safety, completion, constraints and equipment,
                        // then requires a separate confirmation before changing the active move.
                        spoken?.stop()
                        dialog.dismiss()
                        Toast.makeText(
                            this,
                            "Chibi mở phương án thay thế đã duyệt; bài chỉ đổi sau khi mày xác nhận.",
                            Toast.LENGTH_LONG
                        ).show()
                        chooseEasierAlternative()
                    }
                    WorkoutStepVoiceCommand.Command.EASIER -> {
                        val answer = WorkoutStepGuide.spokenEasierCue(move.name, move.easier)
                        lastCoachAnswer = answer
                        speak(answer)
                    }
                    WorkoutStepVoiceCommand.Command.SAFETY_NOTE -> {
                        val answer = WorkoutStepGuide.spokenSafetyNoteCue(move.name, move.avoid)
                        lastCoachAnswer = answer
                        speak(answer)
                    }
                    WorkoutStepVoiceCommand.Command.AUTO_SPEAK_ON -> {
                        val alreadyEnabled = autoSpeakToggle.isChecked
                        autoSpeakToggle.isChecked = true
                        Toast.makeText(
                            this,
                            if (alreadyEnabled) "Chế độ tự đọc đang bật."
                            else "Đã bật tự đọc; Chibi sẽ đọc mỗi bước khi chuyển.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    WorkoutStepVoiceCommand.Command.AUTO_SPEAK_OFF -> {
                        val wasEnabled = autoSpeakToggle.isChecked
                        autoSpeakToggle.isChecked = false
                        Toast.makeText(
                            this,
                            if (wasEnabled) "Đã tắt tự đọc các bước."
                            else "Chế độ tự đọc đang tắt.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    WorkoutStepVoiceCommand.Command.SPEECH_SLOWER -> {
                        val nextRate = WorkoutSpeechRate.slower(workoutSpeechRate)
                        val changed = nextRate != workoutSpeechRate
                        workoutSpeechRate = nextRate
                        workoutSpeechRateRestored = true
                        spoken?.setSpeechRate(workoutSpeechRate)
                        val answer = if (changed) {
                            "Tao sẽ đọc chậm hơn. Mày có thể nói tốc độ bình thường để khôi phục tốc độ đã chọn."
                        } else {
                            "Tốc độ đọc đã ở mức chậm nhất."
                        }
                        lastCoachAnswer = answer
                        announceSpeechRate(answer)
                    }
                    WorkoutStepVoiceCommand.Command.SPEECH_FASTER -> {
                        val nextRate = WorkoutSpeechRate.faster(workoutSpeechRate)
                        val changed = nextRate != workoutSpeechRate
                        workoutSpeechRate = nextRate
                        workoutSpeechRateRestored = true
                        spoken?.setSpeechRate(workoutSpeechRate)
                        val answer = if (changed) {
                            "Tao sẽ đọc nhanh hơn. Mày có thể nói tốc độ bình thường để khôi phục tốc độ đã chọn."
                        } else {
                            "Tốc độ đọc đã ở mức nhanh nhất."
                        }
                        lastCoachAnswer = answer
                        announceSpeechRate(answer)
                    }
                    WorkoutStepVoiceCommand.Command.SPEECH_NORMAL -> {
                        workoutSpeechRate = WorkoutSpeechRate.normal(preferredWorkoutSpeechRate)
                        workoutSpeechRateRestored = true
                        spoken?.setSpeechRate(workoutSpeechRate)
                        val answer = "Đã khôi phục tốc độ đọc mày chọn trong cài đặt."
                        lastCoachAnswer = answer
                        announceSpeechRate(answer)
                    }
                    WorkoutStepVoiceCommand.Command.ASK_EXERCISE -> {
                        val answer = WorkoutExerciseStepQuestion.answer(
                            input = phrase,
                            moveName = move.name,
                            instructions = move.steps
                        ) ?: WorkoutExerciseConversation.answerForCurrentMove(
                            input = phrase,
                            move = move,
                            currentDose = targetDose(index)
                        )
                        val reply = answer ?: "Mày có thể hỏi cách tập bài này, mục tiêu bao nhiêu lần hoặc nghỉ bao lâu."
                        lastCoachAnswer = reply.take(1_200)
                        speak(reply)
                    }
                    WorkoutStepVoiceCommand.Command.REST -> {
                        val seconds = WorkoutRestRequest.seconds(phrase)
                        if (resting) confirmRestExtension(seconds) else confirmExtraRest(seconds)
                    }
                    WorkoutStepVoiceCommand.Command.CLOSE -> {
                        spoken?.stop()
                        dialog.dismiss()
                    }
                    WorkoutStepVoiceCommand.Command.SAFETY -> {
                        dialog.dismiss()
                        stopForSafety()
                    }
                    WorkoutStepVoiceCommand.Command.UNKNOWN -> {
                        when (WorkoutStepPlanHandoff.decide(phrase)) {
                            WorkoutStepPlanHandoff.Decision.PREVIEW_CHANGE -> {
                                // Leave the modal guide and reuse the main, guarded preview flow.
                                // Completed sets stay immutable; no plan change is applied from speech alone.
                                spoken?.stop()
                                dialog.dismiss()
                                handleCoachCommand(phrase)
                            }
                            WorkoutStepPlanHandoff.Decision.SAFETY_STOP -> {
                                dialog.dismiss()
                                stopForSafety()
                            }
                            WorkoutStepPlanHandoff.Decision.NOT_A_CHANGE -> {
                                AlertDialog.Builder(this).setTitle("Chibi chưa hiểu lệnh")
                                    .setMessage("Bạn có thể nói: bước tiếp, bước trước, đọc tiến độ, đổi bài nhẹ hơn, tao mệt rồi, còn 10 phút, không chống tay, không nằm sàn, hỏi cách tập, nghỉ thêm 30 giây hoặc đóng hướng dẫn. Yêu cầu đổi giáo án sẽ được xem trước và chỉ áp dụng khi mày xác nhận. Nếu đau, khó thở hay khó chịu, Chibi sẽ dừng buổi tập.")
                                    .setPositiveButton("Đã hiểu",null).show()
                            }
                        }
                    }
                }
            }
        }
        // Register before show so every dismissal path restores the background animator.
        dialog.setOnDismissListener {
            spoken?.stop()
            lastCoachAnswer = null
            guideVoiceResultHandler = null
            illustration.onPhaseChanged = null
            // Keep the active pose frozen if the guide was opened during a rest.
            poseView.motionEnabled = !resting
        }
        dialog.show()
        // AlertDialog creates its buttons during show. Install listeners synchronously
        // afterwards so the guide behaves identically on Android and Robolectric.
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
            val cue = lastGuideStepCue
                ?.takeIf { it.moveIndex == index }
                ?.speech
                ?: WorkoutStepGuide.spokenCue(move.name, move.steps, walkthrough.position)
            lastGuideStepCue = WorkoutStepGuide.SelectedCue(index, cue)
            speak(cue)
        }
    }

    private fun renderDose() {
        val move=moves[index]
        val restGuidance = currentSetPlan()?.let {
            "\n\n⏱ Nghỉ giữa hiệp đề xuất: ${it.defaultRestSeconds} giây. Có thể tùy chỉnh nếu cần."
        }.orEmpty()
        guide.text="🎯 ${targetDose(index)}${restGuidance}\n\n🧰 ${WorkoutContextCoach.equipmentCue(move)}\n\n💪 ${WorkoutContextCoach.muscleCue(move)}\n\n📖 Cách thực hiện:\n${move.steps}\n\n🌿 Giảm độ khó:\n${move.easier}\n\n⚠️ Lưu ý:\n${move.avoid}"
    }
    private fun displayStep() {
        lastGuideStepCue = null
        paused=true;handler.removeCallbacks(tick)
        resting=false
        restDeadline=0L
        restRemainingMs=0L
        setTimerExpired=false
        val move=moves[index]
        tenSecondCueGiven=false
        remainingMs=RemainingWorkoutDuration.seconds(
            WorkoutDurationPlanner.activeSeconds(move), index, shorterFromIndex
        )*1000L
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

    /** Relative speech edits are allowed only when this move has one exact current target. */
    private fun contextualSpokenTarget(utterance: String): com.chibialarm.app.fitness.WorkoutTargetCommand.SpokenTarget? {
        val plan = currentSetPlan() ?: return null
        val exactTarget = plan.takeIf { it.targetMin == it.targetMax }?.targetMin
        return com.chibialarm.app.fitness.WorkoutTargetCommand.spokenTarget(
            utterance,
            expectedUnit = plan.unit,
            currentTarget = exactTarget
        )
    }

    private fun clockText(milliseconds:Long):String {
        val seconds=(milliseconds.coerceAtLeast(0L)/1000L).toInt()
        return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
    }

    private fun restCounterText(milliseconds:Long):String = "Nghỉ " + clockText(milliseconds)

    private fun updateRestStepSpeakButton() {
        if (!::restStepSpeakButton.isInitialized) return
        val pendingCompletion = !restCompletionSpeechQueue.pendingMessage.isNullOrBlank() && !resting
        val hasCue = !pausedForSafety && (
            pendingCompletion || (resting && !restStepSpeechText.isNullOrBlank())
        )
        restStepSpeakButton.visibility = if (hasCue) android.view.View.VISIBLE else android.view.View.GONE
        restStepSpeakButton.text = if (pendingCompletion) "🔊 Nghe thông báo sau nghỉ"
            else "🔊 Nghe bước tiếp theo"
        restStepSpeakButton.isEnabled = hasCue && ready && pendingRecognitionRequestCode == null
        val actionDescription = if (pendingCompletion) {
            "Đọc lại thông báo kết thúc thời gian nghỉ; không thay đổi buổi tập"
        } else {
            "Đọc một lần hướng dẫn bước tiếp theo; không thay đổi bộ đếm nghỉ"
        }
        restStepSpeakButton.contentDescription = when {
            !hasCue -> "Không có hướng dẫn bước tiếp theo để đọc"
            pendingRecognitionRequestCode != null ->
                "$actionDescription; tạm khóa khi đang chờ microphone"
            !ready ->
                "$actionDescription; giọng đọc chưa sẵn sàng, thông báo vẫn hiển thị bằng chữ"
            else -> actionDescription
        }
    }

    private fun updateRestVoiceCueToggle() {
        if (!::restVoiceCueToggle.isInitialized) return
        restVoiceCueToggle.visibility = if (resting && !pausedForSafety && !completed) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        if (restVoiceCueToggle.isChecked != restVoiceCueEnabled) {
            updatingRestVoiceCueToggle = true
            restVoiceCueToggle.isChecked = restVoiceCueEnabled
            updatingRestVoiceCueToggle = false
        }
        restVoiceCueToggle.contentDescription = if (restVoiceCueEnabled) {
            "Đang bật lời nhắc nghỉ bằng giọng đọc khi còn 10 giây; không sử dụng microphone."
        } else {
            "Đã tắt lời nhắc nghỉ tự động bằng giọng đọc; có thể bật lại bất cứ lúc nào."
        }
    }

    private fun renderRestPreview() {
        if (!::restPreview.isInitialized || !::restPosePreview.isInitialized ||
            !::restStepPreview.isInitialized || !::restStepSpeakButton.isInitialized ||
            !::restVoiceCueToggle.isInitialized) return
        updateRestVoiceCueToggle()
        if (!resting || pausedForSafety || index !in moves.indices) {
            val pendingCompletion = restCompletionSpeechQueue.pendingMessage
            if (!pausedForSafety && !pendingCompletion.isNullOrBlank()) {
                val visibleMessage = "Thông báo sau nghỉ: $pendingCompletion"
                restPreview.text = visibleMessage
                restPreview.contentDescription = visibleMessage
                restPreview.visibility = android.view.View.VISIBLE
                restPosePreview.contentDescription = ""
                restPosePreview.visibility = android.view.View.GONE
                restPosePreview.motionEnabled = false
                restStepPreview.text = pendingCompletion
                restStepPreview.contentDescription = pendingCompletion
                restStepPreview.visibility = android.view.View.VISIBLE
                restStepSpeechText = pendingCompletion
            } else {
                restPreview.text = ""
                restPreview.contentDescription = ""
                restPreview.visibility = android.view.View.GONE
                restPosePreview.contentDescription = ""
                restPosePreview.visibility = android.view.View.GONE
                restPosePreview.motionEnabled = false
                restStepPreview.text = ""
                restStepPreview.contentDescription = ""
                restStepPreview.visibility = android.view.View.GONE
                restStepSpeechText = null
            }
            updateRestStepSpeakButton()
            if (::poseView.isInitialized) poseView.motionEnabled = !pausedForSafety
            return
        }
        val completedSets = completedSetsByMove[index] ?: 0
        val plan = currentSetPlan()
        val nextIndex = WorkoutRestPosePreview.nextMoveIndex(
            currentIndex = index,
            moveCount = moves.size,
            completedSets = completedSets,
            plan = plan
        )
        val upcomingMove = nextIndex?.let { moves.getOrNull(it) }
        val nextMoveAfterRest = upcomingMove?.takeIf { nextIndex != index }
        val preview = WorkoutRestVoiceCue.previewMessage(
            moveName = moves[index].name,
            completedSets = completedSets,
            plan = plan,
            nextMoveName = nextMoveAfterRest?.name,
            nextMoveDose = nextMoveAfterRest?.dose,
            currentRemainingLabel = clockText(remainingMs)
        )
        restPreview.text = preview
        restPreview.contentDescription = preview
        restPreview.visibility = android.view.View.VISIBLE

        // Freeze the current exercise illustration while resting to avoid two
        // simultaneous animations. Show the next required set or next move instead.
        if (::poseView.isInitialized) poseView.motionEnabled = false
        if (upcomingMove != null) {
            val stepCue = WorkoutRestStepPreview.forMove(upcomingMove)
            restStepPreview.text = stepCue?.let { "Bước cần chú ý: ${it.instruction}" }.orEmpty()
            restStepPreview.contentDescription = stepCue?.let {
                "Hướng dẫn bước tiếp theo của ${upcomingMove.name}: ${it.instruction}"
            }.orEmpty()
            restStepPreview.visibility = if (stepCue != null) android.view.View.VISIBLE else android.view.View.GONE
            val upcomingPlan = nextIndex?.let(::setPlan)
            val upcomingCompletedSets = nextIndex?.let { completedSetsByMove[it] ?: 0 } ?: 0
            restStepSpeechText = WorkoutRestStepPreview.spokenMessage(
                move = upcomingMove,
                plan = upcomingPlan,
                completedSets = upcomingCompletedSets
            )

            // Visibility changes call WorkoutPoseView.refreshMotion(), which resets its
            // phase and description. Make it visible first, then pin the selected still
            // frame and write the move-specific accessibility description last.
            restPosePreview.visibility = android.view.View.VISIBLE
            restPosePreview.motionEnabled = false
            restPosePreview.pose = WorkoutPoseGuide.classify(upcomingMove.name)
            restPosePreview.showGuideFrame(alternate = stepCue?.alternate ?: false)
            restPosePreview.contentDescription = buildString {
                append("Minh họa động tác ${upcomingMove.name}. ")
                stepCue?.let { append("Bước cần chú ý: ${it.instruction}. ") }
                append("Hình vector tham khảo, không phân tích tư thế thực tế.")
            }
        } else {
            restPosePreview.contentDescription = ""
            restPosePreview.visibility = android.view.View.GONE
            restPosePreview.motionEnabled = false
            restStepPreview.text = ""
            restStepPreview.contentDescription = ""
            restStepPreview.visibility = android.view.View.GONE
            restStepSpeechText = null
        }
        updateRestStepSpeakButton()
    }

    private fun renderSetControls() {
        renderRestPreview()
        val plan=currentSetPlan()
        val visibility=if(plan==null) android.view.View.GONE else android.view.View.VISIBLE
        setProgress.visibility=visibility
        completeSetButton.visibility=visibility
        val canSkipRest = resting && !pausedForSafety && !completed
        skipRestButton.visibility = if (canSkipRest) android.view.View.VISIBLE else android.view.View.GONE
        skipRestButton.isEnabled = canSkipRest
        if(plan==null) {
            setProgressBar.visibility=android.view.View.GONE
            restSetButton.visibility=android.view.View.GONE
            customSetRestButton.visibility=android.view.View.GONE
            return
        }
        val done=(completedSetsByMove[index] ?: 0).coerceIn(0,plan.maximumSets)
        val target=if(plan.targetMin==plan.targetMax) "${plan.targetMin} ${plan.unit}"
            else "${plan.targetMin}–${plan.targetMax} ${plan.unit}"
        val progressDisplay=WorkoutSetProgressDisplay.from(plan,done)
        setProgressBar.max=progressDisplay.progressMax
        setProgressBar.progress=progressDisplay.progress
        setProgressBar.contentDescription=progressDisplay.contentDescription
        setProgressBar.visibility=android.view.View.VISIBLE
        setProgress.text="Hiệp tự xác nhận: $done/${plan.maximumSets} • ${progressDisplay.summary} • $target"
        val actualValues=actualSetValuesByMove[index].orEmpty()
        val actualValueLabels=actualValues.mapIndexedNotNull { set,value ->
            if(value<=0) null else "Hiệp ${set+1}: $value ${plan.unit}"
        }
        if(actualValueLabels.isNotEmpty()) setProgress.append("\nGiá trị tự khai báo: "+actualValueLabels.joinToString(" • "))
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

    /** The user records the value actually completed; this is self-report, not sensor counting. */
    private fun completeCurrentSet() {
        if(pausedForSafety || completed || resting || index !in moves.indices) return
        val plan=currentSetPlan() ?: return
        if(activeElapsedMs()<=0L) {
            Toast.makeText(this,"Hãy bắt đầu buổi tập trước khi ghi nhận hiệp.",Toast.LENGTH_LONG).show()
            return
        }
        val before=completedSetsByMove[index] ?: 0
        if(before>=plan.maximumSets) return
        val selectedIndex=index
        val wasRunning=!paused
        pause()
        val maximumValue=if(plan.unit=="giây") 600 else 200
        val field=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
            hint="Giá trị thực tế (1–$maximumValue)"
            setText(plan.targetMin.toString())
            selectAll()
        }
        val dialog=AlertDialog.Builder(this)
            .setTitle("Ghi nhận hiệp ${before+1}")
            .setMessage("Động tác: ${moves[selectedIndex].name}\nNhập số ${plan.unit} bạn thực sự hoàn thành. Chibi không tự đếm và không dùng camera để xác minh.")
            .setView(field)
            .setPositiveButton("Xác nhận hiệp",null)
            .setNegativeButton("Hủy") { _, _ -> if(wasRunning && !pausedForSafety && !completed && index==selectedIndex) resume() }
            .create()
        dialog.setOnCancelListener { if(wasRunning && !pausedForSafety && !completed && index==selectedIndex) resume() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val actual=field.text.toString().trim().toIntOrNull()
                if(actual==null || actual !in 1..maximumValue) {
                    field.error="Nhập từ 1 đến $maximumValue"
                    return@setOnClickListener
                }
                if(pausedForSafety || completed || resting || index!=selectedIndex ||
                    currentSetPlan()!=plan || (completedSetsByMove[selectedIndex] ?: 0)!=before
                ) {
                    dialog.dismiss()
                    Toast.makeText(this,"Buổi tập đã thay đổi. Hãy kiểm tra lại trước khi ghi hiệp.",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                recordCurrentSet(actual,selectedIndex,before,plan)
            }
        }
        dialog.show()
    }

    /**
     * Ask for a lightweight perceived-effort check-in after a self-confirmed set.
     * The answer can lengthen rest or hold the next set, but never increases load/reps.
     */
    private fun showSetEffortCheckIn(
        selectedIndex: Int,
        selectedSessionId: String,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan,
        hasNextSet: Boolean,
        resultFeedback: String? = null
    ) {
        fun startPrescribedRest() {
            if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                startSetRest(
                    plan.defaultRestSeconds,
                    "Nghỉ theo thời gian của giáo án. Không cần cố tập nhanh."
                )
            }
        }

        val optionalNotice = when {
            !hasNextSet -> "Đây là hiệp cuối của động tác. Cảm nhận sẽ được lưu để xem lại; không tự tăng khối lượng. "
            completedSets >= plan.requiredSets -> "Bạn đã đủ hiệp tối thiểu; hiệp tiếp theo chỉ là tùy chọn nếu còn sức. "
            else -> "Còn hiệp theo giáo án nếu bạn thấy sẵn sàng. "
        }
        val levels = WorkoutEffortFeedback.Level.values()
        pendingEffortLevel = null
        val dialog = AlertDialog.Builder(this)
            .setTitle("💬 Hiệp vừa rồi thế nào?")
            .setMessage(buildString {
                resultFeedback?.takeIf { it.isNotBlank() }?.let {
                    append(it).append("\n\n")
                }
                append(optionalNotice)
                append("Chọn cảm nhận của bạn. Đây là tự đánh giá để điều chỉnh nghỉ, không phải phép đo thể lực.")
            })
            .setItems(levels.map { it.label }.toTypedArray()) { owner, choice ->
                val level = levels.getOrNull(choice) ?: return@setItems
                val active = owner as? AlertDialog ?: return@setItems
                if (activeEffortDialog !== active || !active.isShowing) return@setItems
                if (!isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                    activeEffortDialog = null
                    pendingEffortLevel = null
                    active.dismiss()
                    Toast.makeText(this, "Buổi tập đã thay đổi; không áp dụng cảm nhận cũ.", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                // A direct tap is an explicit manual answer. Voice results use the separate
                // positive confirmation button below and are never stored on recognition alone.
                activeEffortDialog = null
                pendingEffortLevel = null
                active.dismiss()
                applyEffortCheckIn(
                    selectedIndex, selectedSessionId, completedSets, plan, hasNextSet, level
                )
            }
            .setPositiveButton("Xác nhận giọng nói", null)
            .setNeutralButton("🎤 Trả lời bằng giọng nói", null)
            .setNegativeButton("Bỏ qua • nghỉ theo giáo án") { _, _ -> startPrescribedRest() }
            .create()

        activeEffortDialog = dialog
        dialog.setOnCancelListener(null)
        dialog.setOnDismissListener {
            if (activeEffortDialog === dialog) {
                activeEffortDialog = null
                pendingEffortLevel = null
            }
        }
        dialog.setOnShowListener {
            val spokenFeedback = resultFeedback?.takeIf { it.isNotBlank() }
            val prompt = if (spokenFeedback == null) {
                "Hiệp vừa rồi thế nào? Nói nhẹ, vừa sức, quá khó nhưng vẫn làm được, hoặc quá sức."
            } else {
                "$spokenFeedback Hiệp vừa rồi thế nào? Chọn nhẹ, vừa sức, khó hoặc quá sức."
            }
            // One utterance prevents QUEUE_FLUSH from cutting off the result summary.
            speak(prompt)
        }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            contentDescription = "Xác nhận mức cảm nhận Chibi nhận dạng được bằng giọng nói"
            setOnClickListener {
                val selected = pendingEffortLevel
                if (activeEffortDialog !== dialog || selected == null) return@setOnClickListener
                if (!isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                    activeEffortDialog = null
                    pendingEffortLevel = null
                    dialog.dismiss()
                    Toast.makeText(this@GuidedWorkoutActivity,
                        "Buổi tập đã thay đổi; Chibi chưa lưu cảm nhận.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                activeEffortDialog = null
                pendingEffortLevel = null
                dialog.dismiss()
                applyEffortCheckIn(
                    selectedIndex, selectedSessionId, completedSets, plan, hasNextSet, selected
                )
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
            contentDescription = "Trả lời cảm nhận hiệp bằng giọng nói sau khi xác nhận quyền micro"
            setOnClickListener {
                requestEffortVoiceCommand { phrase ->
                    handleEffortVoiceCheckIn(
                        selectedIndex, selectedSessionId, completedSets, plan,
                        hasNextSet, dialog, phrase
                    )
                }
            }
        }
    }

    private fun isSameEffortContext(
        selectedIndex: Int,
        selectedSessionId: String,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan
    ): Boolean =
        !pausedForSafety && !completed && !resting &&
            index == selectedIndex && sessionId == selectedSessionId &&
            currentSetPlan() == plan &&
            (completedSetsByMove[selectedIndex] ?: 0) == completedSets

    private fun handleEffortVoiceCheckIn(
        selectedIndex: Int,
        selectedSessionId: String,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan,
        hasNextSet: Boolean,
        checkInDialog: AlertDialog,
        utterance: String
    ) {
        if (activeEffortDialog !== checkInDialog || !checkInDialog.isShowing) return
        when (val parsed = WorkoutEffortVoiceCommand.parse(utterance)) {
            WorkoutEffortVoiceCommand.Result.SafetyStop -> {
                activeEffortDialog = null
                pendingEffortLevel = null
                checkInDialog.dismiss()
                stopForSafety()
            }
            WorkoutEffortVoiceCommand.Result.Confirm -> {
                val selected = pendingEffortLevel
                if (selected == null) {
                    checkInDialog.setMessage(
                        "Chibi chưa có mức cảm nhận để xác nhận. Hãy nói nhẹ, vừa sức, khó hoặc quá sức trước."
                    )
                    speak("Chibi chưa có mức cảm nhận để xác nhận. Hãy nói mức độ trước.")
                    return
                }
                if (!isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                    pendingEffortLevel = null
                    checkInDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    checkInDialog.setMessage(
                        "Buổi tập đã thay đổi. Chibi chưa lưu cảm nhận; hãy kiểm tra lại buổi tập."
                    )
                    Toast.makeText(this, "Buổi tập đã thay đổi; không áp dụng cảm nhận cũ.", Toast.LENGTH_LONG).show()
                    return
                }
                activeEffortDialog = null
                pendingEffortLevel = null
                checkInDialog.dismiss()
                speak("Đã xác nhận ${selected.label}.")
                applyEffortCheckIn(
                    selectedIndex, selectedSessionId, completedSets, plan, hasNextSet, selected
                )
            }
            WorkoutEffortVoiceCommand.Result.Revise -> {
                pendingEffortLevel = null
                checkInDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                checkInDialog.setMessage(
                    "Đã bỏ mức vừa nghe; Chibi chưa lưu cảm nhận. Hãy nói lại nhẹ, vừa sức, khó hoặc quá sức."
                )
                speak("Được, Chibi chưa lưu mức vừa rồi. Hãy nói lại cảm nhận của bạn.")
            }
            is WorkoutEffortVoiceCommand.Result.Report -> {
                if (!isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                    pendingEffortLevel = null
                    checkInDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    checkInDialog.setMessage("Buổi tập đã thay đổi. Chibi chưa lưu cảm nhận; hãy kiểm tra lại buổi tập.")
                    Toast.makeText(this, "Buổi tập đã thay đổi; không áp dụng cảm nhận cũ.", Toast.LENGTH_LONG).show()
                    return
                }
                pendingEffortLevel = parsed.level
                checkInDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                checkInDialog.setMessage(
                    "🐰 Chibi nghe được: ${parsed.level.label}.\n\n" +
                        "Đây là tự đánh giá của bạn, không phải phép đo thể lực. " +
                        "Chibi chưa lưu câu trả lời. Nếu đúng, nói “đúng rồi” hoặc nhấn xác nhận; " +
                        "nếu chưa đúng, nói “chưa đúng, chọn lại” hoặc chọn mức khác."
                )
                speak("Chibi nghe được ${parsed.level.label}. Hãy kiểm tra rồi xác nhận.")
            }
            null -> {
                pendingEffortLevel = null
                checkInDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                checkInDialog.setMessage(
                    "Chibi chưa hiểu cảm nhận. Hãy nói “nhẹ”, “vừa sức”, “quá khó nhưng vẫn làm được”, " +
                        "“khá vất vả nhưng vẫn kiểm soát được” hoặc “quá sức”, rồi thử lại; " +
                        "hoặc chọn trực tiếp trong danh sách. " +
                        "Câu chưa rõ sẽ không được ghi nhận."
                )
            }
        }
    }

    private fun applyEffortCheckIn(
        selectedIndex: Int,
        selectedSessionId: String,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan,
        hasNextSet: Boolean,
        level: WorkoutEffortFeedback.Level
    ) {
        if (!isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
            Toast.makeText(this, "Buổi tập đã thay đổi; không áp dụng gợi ý cũ.", Toast.LENGTH_LONG).show()
            return
        }
        val updated = runCatching {
            WorkoutEffortLog.append(
                effortLevelsByMove, selectedIndex, completedSets - 1, level, moves, completedSetsByMove
            )
        }.getOrElse {
            Toast.makeText(this, "Không thể lưu cảm nhận hiệp; tiến độ cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
            return
        }
        effortLevelsByMove.clear()
        effortLevelsByMove.putAll(updated)
        if (!saveCheckpoint()) {
            Toast.makeText(this, "Chưa lưu được cảm nhận này vào checkpoint; buổi tập vẫn có thể tiếp tục.", Toast.LENGTH_LONG).show()
        }
        if (!hasNextSet && level != WorkoutEffortFeedback.Level.VERY_HARD) {
            speak("Đã lưu cảm nhận cho hiệp cuối. Không cần tập thêm để ghi nhận.")
            return
        }

        // Use only earlier check-ins for this exact move; the current set was just appended.
        val previousEffortLevels = effortLevelsByMove[selectedIndex].orEmpty().take(completedSets - 1)
        val recommendation = WorkoutEffortFeedback.recommend(
            level, plan.defaultRestSeconds, previousEffortLevels
        )
        when (level) {
            WorkoutEffortFeedback.Level.EASY,
            WorkoutEffortFeedback.Level.MODERATE -> {
                startSetRest(recommendation.restSeconds, recommendation.message)
            }
            WorkoutEffortFeedback.Level.HARD -> {
                val confirm = AlertDialog.Builder(this)
                    .setTitle("🌿 Nghỉ thêm một chút?")
                    .setMessage(
                        recommendation.message + "\n\n" +
                            "Nghỉ theo giáo án: ${plan.defaultRestSeconds} giây. " +
                            "Phương án đề xuất: ${recommendation.restSeconds} giây. " +
                            "Số lần và tải tập không tự tăng."
                    )
                    .setPositiveButton("Nghỉ ${recommendation.restSeconds} giây") { _, _ ->
                        if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                            startSetRest(recommendation.restSeconds, recommendation.message)
                        }
                    }
                    .setNegativeButton("Nghỉ theo giáo án") { _, _ ->
                        if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                            startSetRest(plan.defaultRestSeconds)
                        }
                    }
                    .setNeutralButton("Điều chỉnh thêm", null)
                    .create()
                // Back/outside dismissal is not consent to start a timer. The two
                // explicit rest buttons are the only ways to choose a rest duration.
                // Dismiss this dialog before opening the option list to avoid stacked
                // AlertDialog windows on Android versions with stricter window handling.
                confirm.setOnCancelListener(null)
                confirm.show()
                confirm.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val contextStillMatches = isSameEffortContext(
                        selectedIndex, selectedSessionId, completedSets, plan
                    )
                    confirm.dismiss()
                    if (contextStillMatches) {
                        val options = mutableListOf<Pair<String, WorkoutMidSessionRequest.Request>>()
                        WorkoutEffortPlanChange.requestFor(level)?.let { request ->
                            options += "Thay động tác bằng biến thể nhẹ hơn" to request
                        }
                        WorkoutEffortPlanChange.repReductionPreviewRequest(
                            level, plan, completedSets, previousEffortLevels
                        )?.let { request ->
                            options += "Giảm tối đa 1 lần mỗi hiệp còn lại" to request
                        }
                        if (options.isNotEmpty()) {
                            AlertDialog.Builder(this@GuidedWorkoutActivity)
                                .setTitle("Chọn cách giảm tải")
                                .setItems(options.map { it.first }.toTypedArray()) { _, choice ->
                                    val selected = options.getOrNull(choice)?.second ?: return@setItems
                                    if (isSameEffortContext(
                                            selectedIndex, selectedSessionId, completedSets, plan
                                        )
                                    ) {
                                        applyMidSessionRequest(selected)
                                    }
                                }
                                .setNegativeButton("Để sau", null)
                                .show()
                        }
                    }
                }
            }
            WorkoutEffortFeedback.Level.VERY_HARD -> {
                // Stop the active timer before asking what to do next. A modal decision
                // must not let the exercise countdown continue behind the dialog.
                pause()
                val reviewButton = Button(this).apply {
                    tag = "workout-very-hard-preview"
                    text = "🌿 Xem phương án nhẹ hơn"
                    contentDescription = "Xem trước giáo án nhẹ hơn khi buổi tập vẫn đang tạm dừng"
                    isAllCaps = false
                }
                val dialogContent = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 12, 24, 0)
                    addView(TextView(this@GuidedWorkoutActivity).apply {
                        tag = "workout-very-hard-message"
                        text = recommendation.message +
                            "\n\nNghỉ theo giáo án: ${plan.defaultRestSeconds} giây. " +
                            "Thời gian nghỉ gợi ý: ${recommendation.restSeconds} giây. " +
                            "Chibi không tự bắt đầu hiệp hoặc động tác mới. " +
                            "Đây là tự đánh giá, không phải phép đo thể lực hay chẩn đoán. " +
                            "Nếu có đau, khó thở, chóng mặt hoặc buồn nôn, hãy kết thúc buổi tập."
                        textSize = 16f
                        setTextColor(Color.DKGRAY)
                        setPadding(0, 0, 0, 12)
                    })
                    addView(
                        reviewButton,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                val confirm = AlertDialog.Builder(this)
                    .setTitle("⚠️ Tạm dừng trước bước tiếp theo")
                    .setView(dialogContent)
                    .setPositiveButton("Nghỉ ${recommendation.restSeconds} giây") { _, _ ->
                        if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                            startSetRest(recommendation.restSeconds, recommendation.message)
                        }
                    }
                    .setNegativeButton("Giữ tạm dừng") { _, _ ->
                        if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                            saveCheckpoint()
                            speak("Đã giữ buổi tập ở trạng thái tạm dừng. Chỉ tiếp tục khi bạn thấy an toàn.")
                        }
                    }
                    .setNeutralButton("Kết thúc buổi tập") { _, _ ->
                        if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) finishWorkout()
                    }
                    .create()
                reviewButton.setOnClickListener {
                    val contextStillMatches = isSameEffortContext(
                        selectedIndex, selectedSessionId, completedSets, plan
                    )
                    confirm.dismiss()
                    if (contextStillMatches) {
                        // This opens the existing catalog-grounded preview. Applying it
                        // changes only unfinished work and leaves the session paused.
                        applyMidSessionRequest(
                            WorkoutEffortPlanChange.pausedAlternativePreviewRequest()
                        )
                    } else {
                        Toast.makeText(
                            this,
                            "Buổi tập đã thay đổi; Chibi chưa mở phương án cũ.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                confirm.setOnCancelListener {
                    if (isSameEffortContext(selectedIndex, selectedSessionId, completedSets, plan)) {
                        saveCheckpoint()
                    }
                }
                confirm.show()
            }
        }
    }

    private fun recordCurrentSet(actualValue:Int,selectedIndex:Int,before:Int,plan:WorkoutSetTracker.Plan) {
        if(pausedForSafety || completed || resting || index!=selectedIndex ||
            currentSetPlan()!=plan || (completedSetsByMove[selectedIndex] ?: 0)!=before
        ) return
        val selectedSessionId = sessionId
        val updated=runCatching {
            WorkoutSetRepetitionLog.append(actualSetValuesByMove,selectedIndex,before,actualValue,plan)
        }.getOrElse {
            Toast.makeText(this,"Không thể xác minh dữ liệu hiệp. Tiến độ cũ được giữ nguyên.",Toast.LENGTH_LONG).show()
            return
        }
        actualSetValuesByMove.clear()
        actualSetValuesByMove.putAll(updated)
        val after=WorkoutSetTracker.complete(before,plan)
        completedSetsByMove[selectedIndex]=after
        if(WorkoutSetTracker.isComplete(after,plan)) completedMoveIndices.add(selectedIndex)
        renderSetControls()
        saveCheckpoint()
        val valueText="$actualValue ${plan.unit} tự khai báo"
        val feedback = WorkoutSetResultCoach.feedback(
            moveName = moves[selectedIndex].name,
            setNumber = after,
            actualValue = actualValue,
            plan = plan,
            completedSetsAfter = after
        )?.message
        Toast.makeText(
            this,
            "Đã ghi nhận hiệp $after: $valueText.",
            Toast.LENGTH_LONG
        ).show()
        showSetEffortCheckIn(
            selectedIndex = selectedIndex,
            selectedSessionId = selectedSessionId,
            completedSets = after,
            plan = plan,
            hasNextSet = after < plan.maximumSets,
            resultFeedback = feedback
        )
    }

    /** Rest is a separate countdown and is excluded from active-exercise time. */
    private fun startSetRest(seconds:Int, spokenCue:String?=null) {
        if(pausedForSafety || completed || resting || seconds !in 1..600) return
        pause()
        val restState = WorkoutRestSession.start(remainingMs, seconds)
        remainingMs = restState.exerciseRemainingMs
        resting=true
        restRemainingMs=restState.restRemainingMs
        restTenSecondCueGiven=false
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

    private fun announceSpeechRate(message:String) {
        if (ready) speak(message)
        else Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    }

    private fun speak(
        message: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ): Boolean {
        if (!ready || message.isBlank()) return false
        val engine = spoken ?: return false
        engine.setSpeechRate(workoutSpeechRate)
        return engine.speak(message.take(350), queueMode, null, "fitness-${index}") == TextToSpeech.SUCCESS
    }

    /**
     * Deliver a rest-completion announcement only after Android accepts it.
     * The queue is checkpointed, so a missing Vietnamese voice or an active
     * microphone never silently discards the user's next-step guidance.
     */
    private fun drainPendingRestCompletionSpeech() {
        val message = restCompletionSpeechQueue.takeIfReady(
            microphoneActive = pendingRecognitionRequestCode != null,
            pausedForSafety = pausedForSafety,
            speechEngineReady = ready && spoken != null
        ) ?: run {
            updateRestStepSpeakButton()
            return
        }
        if (speak(message, TextToSpeech.QUEUE_ADD)) {
            saveCheckpoint()
            renderRestPreview()
        } else {
            restCompletionSpeechQueue.restore(message)
            saveCheckpoint()
            updateRestStepSpeakButton()
        }
    }

    /** Speak the complete coach answer as bounded utterances instead of truncating it at 350 chars. */
    private fun speakCoachAnswer(message: String): Boolean {
        if (!ready || message.isBlank()) return false
        val engine = spoken ?: return false
        val chunks = WorkoutSpeechText.chunks(message)
        if (chunks.isEmpty()) return false

        engine.setSpeechRate(workoutSpeechRate)
        for ((position, chunk) in chunks.withIndex()) {
            val queueMode = if (position == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(
                chunk,
                queueMode,
                null,
                "fitness-coach-${UUID.randomUUID()}"
            )
            if (result != TextToSpeech.SUCCESS) {
                engine.stop()
                return false
            }
        }
        return true
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
        // Keep every self-reported set in the journal, but only required sets affect XP.
        val selfReportedSets=completedSetsByMove.values.sum()
        val requiredSetsForReward=WorkoutSetTracker.requiredSetsForReward(completedSetsByMove,moves)
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
        val setValueSummary=WorkoutSetRepetitionLog.summary(actualSetValuesByMove,moves)
        val effortValueSummary=WorkoutEffortLog.summary(effortLevelsByMove,moves,completedSetsByMove)
        val workoutNote=buildString {
            append("Chibi Coach: $completedCount/${moves.size} động tác được ghi nhận; $setCompletedMoves động tác tự xác nhận hiệp ($selfReportedSets hiệp); $timerCompletedMoves động tác theo bộ đếm; $mins phút hoạt động. Số hiệp và giá trị từng hiệp là tự khai báo, không được cảm biến xác minh.")
            if(setValueSummary.isNotBlank()) append(" Giá trị từng hiệp: $setValueSummary.")
            if(substitutionSummary.isNotBlank()) append(" Thay động tác: $substitutionSummary.")
        }
        val saved=WorkoutData.appendSession(this,Workout(
            LifeData.today(),program.name,mins,workoutNote,sessionId,completedCount,selfReportedSets,
            setLog = setValueSummary,
            effortLog = effortValueSummary))
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
            selfReportedSets = requiredSetsForReward
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
                if (!isFinishing && !isDestroyed) showWorkoutReward(display, effortValueSummary)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isFinishing && !isDestroyed) showWorkoutRewardPending(effortValueSummary)
            }
        }
    }

    private fun showWorkoutReward(display: WorkoutRewardDisplay, effortSummary: String) {
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
            text = "EXP tính từ động tác hoàn thành và hiệp bắt buộc đã tự xác nhận. Hiệp tùy chọn vẫn được lưu vào nhật ký, không cần tập thêm để nhận thưởng."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 80, 110))
            setPadding(8, 0, 8, 10)
        })
        panel.addView(TextView(this).apply {
            text = "🐰 Chibi cấp ${display.level} • Tổng ${display.totalXp} EXP"
            textSize = 17f
            setTextColor(Color.rgb(75, 55, 95))
            gravity = Gravity.CENTER
        })
        panel.addView(Button(this).apply {
            text = "🔊 Nghe tổng kết EXP"
            isAllCaps = false
            tag = "workout-reward-speech-preview"
            contentDescription = "Nghe tổng kết phần thưởng EXP; chỉ phát khi bạn nhấn, không đọc nhật ký tập hoặc cảm nhận sức khỏe"
            setOnClickListener {
                val spokenSummary = WorkoutRewardSpeechSummary.build(display)
                if (!ready || !speak(spokenSummary)) {
                    Toast.makeText(
                        this@GuidedWorkoutActivity,
                        "Giọng đọc chưa sẵn sàng. Bạn vẫn có thể xem tổng kết bằng chữ.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        if (effortSummary.isNotBlank()) {
            val visibleSummary = effortSummary.take(600).let {
                if (effortSummary.length > 600) "$it…" else it
            }
            panel.addView(TextView(this).apply {
                text = "🧭 Cảm nhận các hiệp bạn đã tự ghi\n$visibleSummary\n\nĐây là tự đánh giá tại thời điểm tập, không phải phép đo thể lực hay khả năng hồi phục."
                textSize = 13f
                setTextColor(Color.rgb(75, 70, 88))
                setPadding(8, 12, 8, 8)
                maxLines = 8
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = "Cảm nhận hiệp đã tự ghi: $effortSummary. Không phải phép đo thể lực hay khả năng hồi phục."
            })
        }
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

    private fun showWorkoutRewardPending(effortSummary: String) {
        AlertDialog.Builder(this)
            .setTitle("Đã lưu buổi tập")
            .setMessage(buildString {
                append("Nhật ký đã được lưu an toàn trên máy. Chưa thể đồng bộ EXP ngay lúc này; ")
                append("Chibi RPG sẽ thử lại từ cùng mã buổi tập khi bạn mở màn hình RPG. ")
                append("Không cần tập lại và không mất nhật ký.")
                if (effortSummary.isNotBlank()) {
                    append("\n\nCảm nhận các hiệp bạn đã tự ghi:\n")
                    append(effortSummary.take(500))
                    if (effortSummary.length > 500) append("…")
                    append("\nĐây là tự đánh giá, không phải phép đo thể lực.")
                }
            })
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
        previewVoiceResultHandler = null
        guideVoiceResultHandler = null
        effortVoiceResultHandler = null
        rewardScope.cancel()
        spoken?.stop();spoken?.shutdown()
        super.onDestroy()
    }
}
