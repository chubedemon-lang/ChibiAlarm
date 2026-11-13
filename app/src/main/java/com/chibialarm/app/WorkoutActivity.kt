package com.chibialarm.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Workout(
    val date:String,
    val name:String,
    val minutes:Int,
    val note:String,
    val sessionId:String = "",
    val completedMoves:Int = 0,
    val selfReportedSets:Int = 0,
    /** Bounded, self-reported per-set values; empty for legacy/manual entries. */
    val setLog:String = "",
    /** Optional per-set subjective effort labels; empty for legacy/manual entries. */
    val effortLog:String = ""
)

object WorkoutData {
    private val lock = Any()
    private const val GUEST_PREFERENCES = "workouts_v12"

    private fun prefs(ctx: Context, name: String = GUEST_PREFERENCES) =
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * Names an app-private Fitness journal for one VERIFIED identity subject.
     *
     * Obtain verifiedSubject only from a trusted backend identity verifier. Never pass a
     * display name, email, editable field, Intent extra or unverified token claim.
     * This partitions local storage; it does not authenticate the caller or encrypt data.
     */
    internal fun accountPreferencesName(verifiedSubject: String): String {
        val databaseFile = com.chibialarm.app.rpg.RpgDatabase.accountDatabaseFile(verifiedSubject)
        val digest = databaseFile.removePrefix("chibi_rpg_account_").removeSuffix(".db")
        check(digest.matches(Regex("[0-9a-f]{64}"))) { "Unexpected verified-account namespace" }
        return "workouts_account_" + digest + "_v1"
    }

    private fun accountPrefs(ctx: Context, verifiedSubject: String) =
        prefs(ctx, accountPreferencesName(verifiedSubject))

    /** Null means the stored journal is malformed; callers must not replace it silently. */
    private fun readHistory(source: android.content.SharedPreferences): List<Workout>? {
        return try {
            val arr = JSONArray(source.getString("history", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Workout(
                    o.optString("date"),
                    o.optString("name"),
                    o.optInt("minutes"),
                    o.optString("note"),
                    o.optString("sessionId"),
                    o.optInt("completedMoves", 0),
                    o.optInt("selfReportedSets", 0),
                    o.optString("setLog", ""),
                    o.optString("effortLog", "")
                )
            }
        } catch (_: Exception) { null }
    }

    /** Legacy guest journal. The v1.3 preference name and bytes are intentionally unchanged. */
    fun history(ctx: Context): List<Workout> = readHistory(prefs(ctx)) ?: emptyList()

    /** Read only the journal belonging to a backend-verified account subject. */
    internal fun historyForVerifiedAccount(ctx: Context, verifiedSubject: String): List<Workout> =
        readHistory(accountPrefs(ctx, verifiedSubject)) ?: emptyList()

    private fun encode(items: List<Workout>): String {
        val arr = JSONArray()
        items.forEach { workout ->
            val item = JSONObject()
                .put("date", workout.date)
                .put("name", workout.name)
                .put("minutes", workout.minutes)
                .put("note", workout.note)
            // Legacy/manual entries keep their original shape; only guided sessions
            // carry the structured evidence needed for an idempotent RPG reward.
            if (workout.sessionId.isNotBlank()) {
                item.put("sessionId", workout.sessionId)
                    .put("completedMoves", workout.completedMoves)
                    .put("selfReportedSets", workout.selfReportedSets)
                if (workout.setLog.isNotBlank()) item.put("setLog", workout.setLog.take(1_200))
                if (workout.effortLog.isNotBlank()) item.put("effortLog", workout.effortLog.take(1_200))
            }
            arr.put(item)
        }
        return arr.toString()
    }

    private fun saveTo(source: android.content.SharedPreferences, items: List<Workout>) {
        synchronized(lock) {
            // Do not turn a parse failure into an empty journal and overwrite legacy bytes.
            if (readHistory(source) == null) return
            source.edit().putString("history", encode(items)).apply()
        }
    }

    /** Legacy guest save path; existing callers retain their v1.3 storage behavior. */
    fun save(ctx: Context, items: List<Workout>) = saveTo(prefs(ctx), items)

    /** Save only to one verified account's journal; never copies guest history. */
    internal fun saveForVerifiedAccount(
        ctx: Context,
        verifiedSubject: String,
        items: List<Workout>
    ) = saveTo(accountPrefs(ctx, verifiedSubject), items)

    private fun appendSessionTo(
        source: android.content.SharedPreferences,
        workout: Workout
    ): Boolean {
        val id = workout.sessionId
        val validId = runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)
        if (!validId || workout.minutes !in 1..600 ||
            workout.completedMoves !in 2..500 || workout.selfReportedSets !in 0..2_000 ||
            workout.setLog.length > 1_200 || workout.effortLog.length > 1_200
        ) return false

        synchronized(lock) {
            // A malformed journal is not an empty journal. Preserve it rather than replacing
            // unknown bytes with a new account's or guest's workout.
            val existing = readHistory(source) ?: return false
            if (existing.any { it.sessionId == id }) return true
            return source.edit().putString("history", encode(existing + workout)).commit()
        }
    }

    /** Append a guided session exactly once to the legacy guest journal. */
    fun appendSession(ctx: Context, workout: Workout): Boolean =
        appendSessionTo(prefs(ctx), workout)

    /** Append/dedupe inside one verified account only; identical IDs in other scopes are independent. */
    internal fun appendSessionForVerifiedAccount(
        ctx: Context,
        verifiedSubject: String,
        workout: Workout
    ): Boolean = appendSessionTo(accountPrefs(ctx, verifiedSubject), workout)

    /** Explicitly clear one account's Fitness journal; guest and other account files are untouched. */
    internal fun deleteVerifiedAccountHistory(ctx: Context, verifiedSubject: String): Boolean =
        synchronized(lock) { accountPrefs(ctx, verifiedSubject).edit().clear().commit() }
}
class WorkoutActivity:Activity() {
    private val workouts=arrayOf("🚶 Đi bộ","🏃 Chạy bộ","🧘 Yoga","💪 Tập sức mạnh","🚴 Đạp xe","🏋️ Tập tạ","🌼 Giãn cơ","🌙 Thiền")
    private fun text(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(79,54,91));setPadding(5,8,5,8)
    }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);render() }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1;setPadding(24,31,24,31);setBackgroundColor(Color.rgb(241,252,247)) }
        root.addView(text("🏃 Chibi Workout",28f))
        root.addView(text("Ghi nhật ký luyện tập ngoại tuyến. Theo dõi đều đặn, không ép tập quá sức.",15f))
        root.addView(Button(this).apply { text="＋ Ghi buổi tập";setOnClickListener {addWorkout()} })
        val all=WorkoutData.history(this)
        val cal=Calendar.getInstance()
        val formatter=SimpleDateFormat("yyyy-MM-dd",Locale.US)
        val week=(0..6).map {
            val d=formatter.format(cal.time)
            val mins=all.filter { w->w.date==d }.sumOf { w->w.minutes }
            cal.add(Calendar.DATE,-1)
            d to mins
        }.reversed()
        val total=week.sumOf { it.second }
        root.addView(text("📊 7 ngày: $total phút   •   ${all.size} buổi đã ghi",19f))
        val max=week.maxOfOrNull { it.second }?.coerceAtLeast(30) ?: 30
        week.forEach { (day,mins)->
            val line=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
            line.addView(text(day.substring(5),13f),LinearLayout.LayoutParams(80,-2))
            line.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                this.max=max;progress=mins
            },LinearLayout.LayoutParams(0,22,1f))
            line.addView(text(" $mins p",13f))
            root.addView(line)
        }
        root.addView(Button(this).apply { text="📤 Chia sẻ báo cáo CSV";setOnClickListener {
            val csv="date,exercise,minutes,note,completed_moves,self_reported_sets,set_log,effort_log\n"+all.joinToString("\n") {
                listOf(
                    it.date, it.name, it.minutes.toString(), it.note,
                    it.completedMoves.toString(), it.selfReportedSets.toString(), it.setLog, it.effortLog
                ).joinToString(",") { value ->
                    "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""
                }
            }
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Chibi workout log")
                putExtra(Intent.EXTRA_TEXT,csv)
            }
            startActivity(Intent.createChooser(intent,"Xuất nhật ký luyện tập"))
        } })
        root.addView(text("🗓 Nhật ký gần đây",21f))
        if(all.isEmpty())root.addView(text("Chưa có buổi tập. Bắt đầu với 5–10 phút vận động nhẹ nhé!",15f))
        all.asReversed().take(30).forEachIndexed { index,w->
            val row=LinearLayout(this).apply {
                orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(12,10,12,10)
                background=GradientDrawable().apply { setColor(Color.WHITE);cornerRadius=22f }
            }
            row.addView(text("${w.date}\n${w.name} • ${w.minutes} phút"+(if(w.note.isBlank())"" else "\n"+w.note),15f),
                LinearLayout.LayoutParams(0,-2,1f))
            if (w.setLog.isNotBlank() || w.effortLog.isNotBlank()) row.addView(Button(this).apply {
                text = "Chi tiết"
                contentDescription = "Xem số hiệp và cảm nhận gắng sức tự khai báo của ${w.name}"
                setOnClickListener {
                    val details = buildString {
                        if (w.setLog.isNotBlank()) append(w.setLog)
                        if (w.effortLog.isNotBlank()) {
                            if (isNotEmpty()) append("\n\n")
                            append("Cảm nhận từng hiệp: ").append(w.effortLog)
                        }
                    }
                    AlertDialog.Builder(this@WorkoutActivity)
                        .setTitle("Chi tiết buổi tập • ${w.name}")
                        .setMessage(details + "\n\nSố liệu và cảm nhận do bạn tự khai báo; không phải kết quả đo từ camera hoặc thiết bị y tế.")
                        .setPositiveButton("Đóng", null)
                        .show()
                }
            })
            row.addView(Button(this).apply { text="🗑";setOnClickListener {
                AlertDialog.Builder(this@WorkoutActivity).setTitle("Xóa buổi tập?")
                    .setPositiveButton("Xóa"){_,_->
                        val current=WorkoutData.history(this@WorkoutActivity).toMutableList()
                        val original=all.size-1-index
                        if(original in current.indices)current.removeAt(original)
                        WorkoutData.save(this@WorkoutActivity,current);render()
                    }.setNegativeButton("Hủy",null).show()
            } })
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=9 })
        }
        root.addView(text("Dữ liệu này do bạn tự nhập, không phải số đo từ thiết bị y tế hoặc Health Connect.",12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun addWorkout() {
        val form=LinearLayout(this).apply { orientation=1;setPadding(24,9,24,12) }
        val spinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@WorkoutActivity,android.R.layout.simple_spinner_dropdown_item,workouts)
        }
        form.addView(spinner)
        form.addView(text("Thời gian luyện tập (phút)",14f))
        val minutes=EditText(this).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText("20") }
        form.addView(minutes)
        val note=EditText(this).apply { hint="Ghi chú (không bắt buộc)";maxLines=3 }
        form.addView(note)
        val dialog=AlertDialog.Builder(this).setTitle("🏃 Buổi tập hôm nay")
            .setView(form).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val mins=minutes.text.toString().toIntOrNull()
                if(mins==null || mins !in 1..600)minutes.error="Nhập 1 đến 600 phút"
                else {
                    WorkoutData.save(this,WorkoutData.history(this)+Workout(
                        LifeData.today(),spinner.selectedItem.toString(),mins,note.text.toString().take(150)))
                    dialog.dismiss();render()
                }
            }
        }
        dialog.show()
    }
}
