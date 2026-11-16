package com.chibialarm.app

import android.app.*
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.*
import android.view.Gravity
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
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
    private lateinit var attachButton:Button
    private var moveName=""
    private var reviewedUrl=""
    private var pendingReviewUrl=""
    private var reviewedRelevant=false
    private var reviewGeneration=0

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        moveName=intent.getStringExtra("move").orEmpty()
        val root=LinearLayout(this).apply {
            orientation=1
            setPadding(22,30,22,24)
            setBackgroundColor(Color.rgb(249,242,252))
        }
        root.addView(TextView(this).apply {
            text="🎬 Chibi • Xem xét video tập luyện"
            textSize=23f
        })
        root.addView(TextView(this).apply {
            text="Động tác: "+moveName
            textSize=18f
        })
        root.addView(TextView(this).apply {
            text="Chibi chỉ kiểm tra tiêu đề, tác giả và ảnh thu nhỏ công khai. Không thể xem hoặc xác minh kỹ thuật trong video."
            textSize=13f
        })
        field=EditText(this).apply {
            hint="Dán link video YouTube"
            setSingleLine()
            setText(intent.getStringExtra("url").orEmpty())
        }
        root.addView(field)
        root.addView(Button(this).apply {
            text="🔎 Kiểm tra video"
            setOnClickListener { review() }
        })
        thumb=ImageView(this).apply {
            adjustViewBounds=true
            maxHeight=460
            contentDescription="Ảnh thu nhỏ video; không phải bằng chứng xác minh kỹ thuật tập."
        }
        root.addView(thumb)
        info=TextView(this).apply {
            textSize=16f
            text="Dán link rồi bấm Kiểm tra."
            setPadding(6,15,6,15)
        }
        root.addView(info)
        root.addView(Button(this).apply {
            text="▶ Xem video gốc"
            setOnClickListener {
                val uri=youtubeUri(field.text.toString().trim())
                if(uri!=null) {
                    try { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
                    catch(_:Exception) { Toast.makeText(this@ExerciseVideoReviewActivity,"Không mở được video.",Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this@ExerciseVideoReviewActivity,"Hãy dùng link YouTube HTTPS hợp lệ.",Toast.LENGTH_SHORT).show()
                }
            }
        })
        attachButton=Button(this).apply {
            text="⭐ Gắn video này vào động tác"
            isEnabled=false
            setOnClickListener {
                if(!reviewedRelevant || reviewedUrl.isBlank() ||
                    field.text.toString().trim()!=reviewedUrl
                ) {
                    Toast.makeText(this@ExerciseVideoReviewActivity,"Video chưa vượt qua bước sàng lọc tiêu đề. Hãy kiểm tra lại hoặc chọn video khác.",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val key=intent.getStringExtra("videoKey").orEmpty()
                if(key.isNotBlank()) {
                    getSharedPreferences("workout_videos",0).edit()
                        .putString("link_"+key,reviewedUrl).apply()
                }
                Toast.makeText(this@ExerciseVideoReviewActivity,"Đã lưu video có tiêu đề liên quan. Hãy tự xem kỹ trước khi tập.",Toast.LENGTH_LONG).show()
                finish()
            }
        }
        root.addView(attachButton)

        field.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) = Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val current=s?.toString()?.trim().orEmpty()
                val reviewIsStale =
                    (pendingReviewUrl.isNotBlank() && current!=pendingReviewUrl) ||
                    (reviewedUrl.isNotBlank() && current!=reviewedUrl)
                if(reviewIsStale) {
                    reviewGeneration++
                    pendingReviewUrl=""
                    reviewedUrl=""
                    reviewedRelevant=false
                    attachButton.isEnabled=false
                    info.text="Link đã thay đổi. Hãy kiểm tra lại video trước khi gắn vào động tác."
                    thumb.setImageDrawable(null)
                    thumb.contentDescription="Chưa có ảnh thu nhỏ đã kiểm tra."
                }
            }
            override fun afterTextChanged(s:Editable?) = Unit
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun youtubeUri(link:String):Uri? {
        val uri=runCatching { Uri.parse(link) }.getOrNull() ?: return null
        val host=uri.host?.lowercase(Locale.ROOT) ?: return null
        val authority=uri.encodedAuthority?.lowercase(Locale.ROOT)
        if(uri.scheme!="https" ||
            host !in setOf("youtube.com","www.youtube.com","m.youtube.com","youtu.be") ||
            authority !in setOf(host,"$host:443")
        ) return null
        return uri
    }

    private fun readBounded(input:java.io.InputStream,limit:Int):ByteArray {
        val output=ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        var total=0
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            total+=count
            if(total>limit) throw IOException("Response exceeds size limit")
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }

    private fun loadThumbnail(imageUrl:String):Bitmap? {
        var connection:HttpURLConnection?=null
        return try {
            val uri=Uri.parse(imageUrl)
            val host=uri.host?.lowercase(Locale.ROOT)
            val authority=uri.encodedAuthority?.lowercase(Locale.ROOT)
            if(uri.scheme!="https" || host !in setOf("i.ytimg.com","img.youtube.com") ||
                authority !in setOf(host,"$host:443")
            ) return null

            connection=URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout=5000
            connection.readTimeout=5000
            connection.instanceFollowRedirects=false
            if(connection.responseCode !in 200..299) return null
            if(connection.contentLength>1_500_000) return null
            if(connection.contentType?.substringBefore(';')?.startsWith("image/")!=true) return null
            val bytes=connection.inputStream.use { readBounded(it,1_500_000) }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
            var sample=1
            while(bounds.outWidth/sample>720 || bounds.outHeight/sample>720) sample*=2
            val options=BitmapFactory.Options().apply { inSampleSize=sample }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        } catch(_:Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun review() {
        val link=field.text.toString().trim()
        if(youtubeUri(link)==null) {
            pendingReviewUrl=""
            reviewedUrl=""
            reviewedRelevant=false
            attachButton.isEnabled=false
            info.text="Hiện chỉ hỗ trợ kiểm tra metadata YouTube bằng link HTTPS chính thức. TikTok vẫn có thể mở ở ứng dụng hoặc trang chính thức."
            return
        }

        val requestId=++reviewGeneration
        pendingReviewUrl=link
        reviewedUrl=""
        reviewedRelevant=false
        attachButton.isEnabled=false
        info.text="Đang đọc thông tin công khai…"
        thumb.setImageDrawable(null)
        thumb.contentDescription="Đang tải ảnh thu nhỏ của video."

        Thread {
            var connection:HttpURLConnection?=null
            try {
                val endpoint="https://www.youtube.com/oembed?url="+Uri.encode(link)+"&format=json"
                connection=URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout=8000
                connection.readTimeout=8000
                connection.instanceFollowRedirects=false
                if(connection.responseCode !in 200..299) throw IOException("YouTube metadata unavailable")
                if(connection.contentLength>64*1024) throw IOException("Metadata response too large")
                val data=connection.inputStream.use { String(readBounded(it,64*1024),Charsets.UTF_8) }
                val json=JSONObject(data)
                val title=json.optString("title")
                val author=json.optString("author_name").take(120)
                val imageUrl=json.optString("thumbnail_url")
                val relevant=ExerciseVideoRelevance.matches(moveName,title)
                val bitmap=loadThumbnail(imageUrl)
                runOnUiThread {
                    if(requestId!=reviewGeneration || field.text.toString().trim()!=link) return@runOnUiThread
                    pendingReviewUrl=""
                    reviewedUrl=link
                    reviewedRelevant=relevant
                    attachButton.isEnabled=relevant
                    thumb.setImageBitmap(bitmap)
                    thumb.contentDescription=if(bitmap!=null) {
                        "Ảnh thu nhỏ video: $title. Không xác minh tư thế hoặc độ an toàn."
                    } else {
                        "Không tải được ảnh thu nhỏ. Tiêu đề video: $title."
                    }
                    val relevanceNotice=if(relevant) {
                        "Tiêu đề có từ khóa khớp với động tác. Nút gắn video đã mở; đây chỉ là sàng lọc từ tiêu đề."
                    } else {
                        "Tiêu đề chưa có từ khóa đủ liên quan tới động tác. Nút gắn video đang khóa; hãy chọn video khác."
                    }
                    info.text="Video: $title\nKênh: $author\n\n$relevanceNotice\n\n" +
                        "⚠️ Không phân tích khung hình, tư thế, kỹ thuật, độ an toàn hoặc chứng chỉ người hướng dẫn. Ảnh thu nhỏ chỉ để nhận biết video. Hãy xem toàn bộ video và dừng nếu đau."
                }
            } catch(_:Exception) {
                runOnUiThread {
                    if(requestId!=reviewGeneration || field.text.toString().trim()!=link) return@runOnUiThread
                    pendingReviewUrl=""
                    reviewedUrl=""
                    reviewedRelevant=false
                    attachButton.isEnabled=false
                    info.text="Không đọc được thông tin video. Kiểm tra kết nối hoặc thử link YouTube khác."
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    override fun onDestroy() {
        reviewGeneration++
        super.onDestroy()
    }
}
