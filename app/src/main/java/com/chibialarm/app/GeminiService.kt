package com.chibialarm.app

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Queries available Gemini models for this particular API key; avoids hard-coded retired model IDs. */
object GeminiService {
    private const val ROOT="https://generativelanguage.googleapis.com/v1beta/"
    private fun request(path:String,key:String,body:String?=null):JSONObject {
        val conn=(URL(ROOT+path).openConnection() as HttpsURLConnection).apply {
            requestMethod=if(body==null) "GET" else "POST"
            connectTimeout=15000;readTimeout=45000
            setRequestProperty("x-goog-api-key",key)
            setRequestProperty("Accept","application/json")
            if(body!=null) { doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8") }
        }
        try {
            if(body!=null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            val text=(if(status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if(status !in 200..299) {
                val detail=try { JSONObject(text).optJSONObject("error")?.optString("message") } catch(_:Exception) { null }
                val hint=when(status) {
                    400 -> "Yêu cầu không hợp lệ."
                    401,403 -> "API key không được chấp nhận hoặc thiếu quyền."
                    404 -> "Model hoặc endpoint không tồn tại."
                    429 -> "Google đã giới hạn lượt gọi hoặc quota của dự án. Hãy kiểm tra hạn mức trong Google AI Studio, chờ thời điểm được đặt lại hoặc dùng API key thuộc dự án khác có quota. Gemini Pro không tự cấp quota API."
                    else -> "Lỗi kết nối Gemini."
                }
                throw IllegalStateException("HTTP $status: $hint"+if(status==429) "" else " "+(detail ?: "").take(180))
            }
            return JSONObject(text)
        } finally { conn.disconnect() }
    }
    fun generate(key:String,idea:String):Pair<String,String> = generatePrompt(key,
        "Hãy viết lời đánh thức bằng tiếng Việt, chibi, vui vẻ, dài 1-3 câu, không markdown. Chủ đề: "+idea)
    fun generatePrompt(key:String,prompt:String):Pair<String,String> {
        val models=mutableListOf<String>()
        var page:String?=null
        for(pageIndex in 0 until 4) {
            val response=request("models?pageSize=100"+(if(page.isNullOrBlank()) "" else "&pageToken="+java.net.URLEncoder.encode(page,"UTF-8")),key)
            val arr=response.optJSONArray("models") ?: JSONArray()
            for(i in 0 until arr.length()) {
                val model=arr.getJSONObject(i)
                val methods=model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                val supported=(0 until methods.length()).any { methods.optString(it)=="generateContent" }
                val name=model.optString("name")
                if(supported && name.startsWith("models/gemini-") &&
                    listOf("embedding","tts","image","audio","live","robotics","computer-use").none { name.contains(it) }) models.add(name)
            }
            page=response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if(page==null) break
        }
        if(models.isEmpty()) throw IllegalStateException("API key này không có model Gemini hỗ trợ generateContent.")
        val ordered=models.distinct().sortedWith(compareBy<String> {
            when {
                it.contains("flash-lite") -> 0
                it.contains("flash") && !it.contains("lite") -> 1
                it.contains("pro") -> 2
                else -> 3
            }
        }.thenByDescending { it })
        val payload=JSONObject().put("contents",JSONArray().put(
            JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt))))).toString()
        var lastError:Exception?=null
        for(model in ordered.take(3)) {
            try {
                val response=request("$model:generateContent",key,payload)
                val candidates=response.optJSONArray("candidates")
                val parts=candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val text=buildString {
                    if(parts!=null) for(i in 0 until parts.length()) {
                        if(isNotEmpty()) append("\n")
                        append(parts.optJSONObject(i)?.optString("text","") ?: "")
                    }
                }.trim().take(12000)
                if(text.isNotBlank()) return text to model.removePrefix("models/")
                lastError=IllegalStateException("Model không trả về văn bản. Có thể phản hồi bị chặn.")
            } catch(e:Exception) {
                lastError=e
                if(!e.message.orEmpty().contains("HTTP 404")) throw e
            }
        }
        throw lastError ?: IllegalStateException("Không có model khả dụng.")
    }
}
