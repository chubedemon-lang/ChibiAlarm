package com.chibialarm.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Gemini 3.8 voice generation via documented Interactions REST. Costs and access depend on user's key. */
object GeminiVoice {
    val choices=arrayOf("Zephyr","Puck","Charon","Kore","Fenrir","Leda","Orus","Aoede",
        "Callirrhoe","Autonoe","Enceladus","Iapetus","Umbriel","Algieba","Despina",
        "Erinome","Algenib","Rasalgethi","Laomedeia","Achernar","Alnilam","Schedar",
        "Gacrux","Pulcherrima","Achird","Zubenelgenubi","Vindemiatrix","Sadachbia",
        "Sadaltager","Sulafat")
    val styles=arrayOf("cheerful and playful, warm Vietnamese pronunciation",
        "soft, gentle and peaceful","energetic morning wake-up","natural, friendly",
        "confident and motivational","slow and reassuring","excited, lively")
    fun create(ctx:Context,key:String,words:String,voice:String,style:String):File {
        var last:Exception?=null
        for(model in listOf("gemini-3.8-flash-tts","gemini-3.8-flash-lite-tts")) {
            try { return createWithModel(ctx,key,words,voice,style,model) }
            catch(e:Exception) {
                last=e
                val msg=e.message.orEmpty()
                if(!msg.contains("HTTP 404") && !msg.contains("HTTP 429")) throw e
            }
        }
        throw last ?: IllegalStateException("Không tìm được model TTS tương thích.")
    }
    private fun createWithModel(ctx:Context,key:String,words:String,voice:String,style:String,model:String):File {
        require(key.isNotBlank()) { "Hãy nhập API key trong Gemini AI Studio." }
        val input=JSONArray().put(JSONObject().put("type","user_input").put("content",
            JSONArray().put(JSONObject().put("type","text").put("text",words.take(400))
                .put("annotations",JSONArray().put(JSONObject().put("type","speech_metadata").put("style",style))))))
        val payload=JSONObject()
            .put("model",model)
            .put("input",input)
            .put("response_format",JSONObject().put("type","audio"))
            .put("generation_config",JSONObject().put("speech_config",
                JSONArray().put(JSONObject().put("voice",voice))))
        val conn=(URL("https://generativelanguage.googleapis.com/v1beta/interactions").openConnection() as HttpsURLConnection).apply {
            requestMethod="POST";connectTimeout=20000;readTimeout=100000;doOutput=true
            setRequestProperty("x-goog-api-key",key)
            setRequestProperty("Content-Type","application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            val content=(if(status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if(status !in 200..299) {
                val detail=try { JSONObject(content).optJSONObject("error")?.optString("message") ?: "" }catch(_:Exception){ "" }
                throw IllegalStateException("Gemini TTS HTTP $status: "+detail.take(270))
            }
            val result=JSONObject(content)
            val steps=result.optJSONArray("steps") ?: JSONArray()
            var b64:String?=null
            for(i in 0 until steps.length()) {
                val output=steps.optJSONObject(i) ?: continue
                if(output.optString("type")!="model_output")continue
                val parts=output.optJSONArray("content") ?: continue
                for(j in 0 until parts.length()) {
                    val p=parts.optJSONObject(j) ?: continue
                    if(p.optString("type")=="audio" && p.optString("data").isNotEmpty()) b64=p.optString("data")
                }
            }
            if(b64.isNullOrBlank()) throw IllegalStateException("Gemini không trả âm thanh. Kiểm tra quyền model TTS và hạn mức API.")
            if(b64.length>14_000_000) throw IllegalStateException("Âm thanh quá lớn.")
            val bytes=Base64.decode(b64,Base64.DEFAULT)
            if(bytes.size<48) throw IllegalStateException("Dữ liệu âm thanh không hợp lệ.")
            return File(ctx.cacheDir,"chibi_voice_preview.wav").apply { writeBytes(bytes) }
        } finally { conn.disconnect() }
    }
    fun useAsAlarm(ctx:Context,preview:File) {
        require(preview.exists()) { "Chưa tạo âm thanh AI." }
        preview.copyTo(File(ctx.filesDir,"chibi_voice_alarm.wav"),overwrite=true)
        ctx.getSharedPreferences("settings",0).edit().putBoolean("aiVoiceEnabled",true).apply()
    }
    fun disable(ctx:Context) {
        ctx.getSharedPreferences("settings",0).edit().putBoolean("aiVoiceEnabled",false).apply()
    }
}
