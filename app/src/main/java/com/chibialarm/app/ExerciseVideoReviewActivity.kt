package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.*
import android.view.Gravity
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale

/**
 * Metadata-based screening only. No claim of visual/video understanding.
 * YouTube oEmbed provides title, author and thumbnail without an API key.
 */
class ExerciseVideoReviewActivity:Activity() {
    private lateinit var info:TextView
    private lateinit var thumb:ImageView
    private lateinit var field:EditText
    private var moveName=""
    private var reviewedUrl=""
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        moveName=intent.getStringExtra("move").orEmpty()
        val root=LinearLayout(this).apply { orientation=1;setPadding(22,30,22,24);setBackgroundColor(Color.rgb(249,242,252)) }
        root.addView(TextView(this).apply { text="🎬 Chibi • Xem xét video tập luyện";textSize=23f })
        root.addView(TextView(this).apply { text="Động tác: "+moveName;textSize=18f })
        root.addView(TextView(this).apply { text="Chibi kiểm tra tiêu đề, tác giả và mức phù hợp theo từ khóa. Chưa thể xem và xác minh kỹ thuật trong video.";textSize=13f })
        field=EditText(this).apply { hint="Dán link video YouTube";setSingleLine();setText(intent.getStringExtra("url").orEmpty()) }
        root.addView(field)
        root.addView(Button(this).apply { text="🔎 Kiểm tra video";setOnClickListener { review() } })
        thumb=ImageView(this).apply { adjustViewBounds=true;maxHeight=460 }
        root.addView(thumb)
        info=TextView(this).apply { textSize=16f;text="Dán link video rồi bấm Kiểm tra.";setPadding(6,15,6,15) }
        root.addView(info)
        root.addView(Button(this).apply { text="▶ Xem video gốc";setOnClickListener {
            val url=field.text.toString().trim()
            if(url.startsWith("https://")) try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }catch(_:Exception){}
        } })
        root.addView(Button(this).apply { text="⭐ Gắn video này vào động tác";setOnClickListener {
            if(reviewedUrl.isBlank()) { Toast.makeText(this@ExerciseVideoReviewActivity,"Hãy kiểm tra video trước",Toast.LENGTH_SHORT).show();return@setOnClickListener }
            val key=intent.getStringExtra("videoKey").orEmpty()
            if(key.isNotBlank())getSharedPreferences("workout_videos",0).edit().putString("link_"+key,reviewedUrl).apply()
            Toast.makeText(this@ExerciseVideoReviewActivity,"Đã lưu video cho bài tập",Toast.LENGTH_LONG).show()
            finish()
        } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun review() {
        val link=field.text.toString().trim()
        val uri=Uri.parse(link)
        val host=uri.host.orEmpty().lowercase(Locale.ROOT)
        if(uri.scheme!="https" || host !in listOf("youtube.com","www.youtube.com","m.youtube.com","youtu.be")) {
            info.text="Hiện chỉ hỗ trợ phân tích metadata YouTube. TikTok vẫn có thể mở bằng liên kết chính thức.";return
        }
        info.text="Đang đọc thông tin công khai…"
        Thread {
            try {
                val endpoint="https://www.youtube.com/oembed?url="+Uri.encode(link)+"&format=json"
                val conn=URL(endpoint).openConnection() as HttpURLConnection
                conn.connectTimeout=8000;conn.readTimeout=8000
                val data=conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json=JSONObject(data)
                val title=json.optString("title")
                val author=json.optString("author_name")
                val imageUrl=json.optString("thumbnail_url")
                val tokens=moveName.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>=4}
                val titleLow=title.lowercase(Locale.ROOT)
                val matches=tokens.count{it in titleLow}
                val relevant=matches>=1
                val warning=if(relevant) "Tiêu đề có từ khóa liên quan tới động tác." else "Chưa tìm thấy từ khóa động tác trong tiêu đề; cần xem lại."
                val bitmap=try { URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) } }catch(_:Exception){null}
                runOnUiThread {
                    reviewedUrl=link
                    thumb.setImageBitmap(bitmap)
                    info.text="Video: "+title+"\nKênh: "+author+"\n\n"+warning+
                        "\n\n⚠️ Chỉ là sàng lọc theo tiêu đề. Không xác minh tư thế, độ an toàn, độ chính xác hay chứng chỉ của tác giả. Hãy xem kỹ trước khi tập; dừng nếu đau."
                }
            }catch(e:Exception){runOnUiThread { info.text="Không đọc được thông tin video. Kiểm tra kết nối hoặc thử link YouTube khác.";reviewedUrl="" }}
        }.start()
    }
}
