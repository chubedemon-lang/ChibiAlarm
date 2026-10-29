package com.chibialarm.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ImportedClip(val id:String,val name:String,val filename:String,val kind:String,val seconds:Int)
object VoiceClips {
    private const val MAX_BYTES=20L*1024*1024
    private const val MAX_CLIPS=15
    private fun prefs(ctx:Context)=ctx.getSharedPreferences("chibi_voice_library",0)
    private fun dir(ctx:Context)=File(ctx.filesDir,"voice_library").apply { mkdirs() }
    fun clips(ctx:Context):List<ImportedClip> = try {
        val a=JSONArray(prefs(ctx).getString("library","[]"))
        (0 until a.length()).map { i ->
            val o=a.getJSONObject(i)
            ImportedClip(o.getString("id"),o.getString("name"),o.getString("filename"),
                o.optString("kind","Đã nhập"),o.optInt("seconds",0))
        }.filter { File(dir(ctx),it.filename).exists() }
    }catch(_:Exception){ emptyList() }
    private fun write(ctx:Context,items:List<ImportedClip>) {
        val arr=JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id",it.id).put("name",it.name).put("filename",it.filename)
                .put("kind",it.kind).put("seconds",it.seconds))
        }
        prefs(ctx).edit().putString("library",arr.toString()).apply()
    }
    fun file(ctx:Context,clip:ImportedClip)=File(dir(ctx),clip.filename)
    fun chosen(ctx:Context):String=ctx.getSharedPreferences("settings",0).getString("alarmAudioPath","").orEmpty()
    fun select(ctx:Context,clip:ImportedClip) {
        val file=file(ctx,clip)
        require(file.exists()) { "File âm thanh không còn trên máy." }
        ctx.getSharedPreferences("settings",0).edit()
            .putBoolean("aiVoiceEnabled",true).putString("alarmAudioPath",file.absolutePath).apply()
    }
    fun systemVoice(ctx:Context) {
        ctx.getSharedPreferences("settings",0).edit()
            .putBoolean("aiVoiceEnabled",false).remove("alarmAudioPath").apply()
    }
    fun delete(ctx:Context,clip:ImportedClip) {
        val target=file(ctx,clip)
        if(chosen(ctx)==target.absolutePath)systemVoice(ctx)
        target.delete()
        write(ctx,clips(ctx).filterNot { it.id==clip.id })
    }
    fun importClip(ctx:Context,source:Uri,label:String,kind:String="Audio đã xuất"):ImportedClip {
        val current=clips(ctx)
        require(current.size<MAX_CLIPS) { "Tối đa $MAX_CLIPS file. Hãy xóa bớt để nhập thêm." }
        val resolver=ctx.contentResolver
        val mime=resolver.getType(source).orEmpty().lowercase()
        var display=""
        resolver.query(source,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use {
            if(it.moveToFirst()) {
                val name=it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if(name>=0)display=it.getString(name).orEmpty()
                val size=it.getColumnIndex(OpenableColumns.SIZE)
                if(size>=0 && !it.isNull(size))require(it.getLong(size)<=MAX_BYTES) {
                    "File quá lớn (giới hạn 20 MB)."
                }
            }
        }
        val ext=display.substringAfterLast('.',"").lowercase()
        val allowed=setOf("mp3","m4a","wav","aac","ogg","flac")
        require(ext in allowed || mime.startsWith("audio/")) {
            "Chỉ hỗ trợ file âm thanh MP3, M4A, WAV, AAC, OGG hoặc FLAC."
        }
        val suffix=if(ext in allowed) ext else when {
            "mpeg" in mime -> "mp3"
            "wav" in mime -> "wav"
            "flac" in mime -> "flac"
            "ogg" in mime -> "ogg"
            else -> "m4a"
        }
        val id=System.currentTimeMillis().toString()+("-"+java.util.UUID.randomUUID().toString().take(6))
        val target=File(dir(ctx),"clip_$id.$suffix")
        try {
            val input=resolver.openInputStream(source) ?: throw IllegalStateException("Không đọc được file được chọn.")
            input.use { ins ->
                target.outputStream().use { out ->
                    val buf=ByteArray(16*1024)
                    var total=0L
                    while(true) {
                        val n=ins.read(buf)
                        if(n<0)break
                        total+=n
                        if(total>MAX_BYTES)throw IllegalStateException("File vượt quá 20 MB.")
                        out.write(buf,0,n)
                    }
                }
            }
            require(target.length()>128) { "File trống hoặc không phải âm thanh hợp lệ." }
            val retriever=MediaMetadataRetriever()
            val sec=try {
                retriever.setDataSource(target.absolutePath)
                ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L)/1000L).toInt()
            }finally { retriever.release() }
            require(sec in 1..300) { "File cần dài từ 1 giây đến 5 phút." }
            val clip=ImportedClip(id,label.trim().ifEmpty { display.substringBeforeLast(".") }.take(55),target.name,kind,sec)
            write(ctx,current+clip)
            return clip
        }catch(e:Exception) {
            target.delete()
            throw e
        }
    }
}
