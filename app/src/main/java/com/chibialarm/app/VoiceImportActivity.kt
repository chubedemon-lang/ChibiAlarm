package com.chibialarm.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import java.io.File
import java.util.Locale

/**
 * Import ONLY audio selected by the user. Does not scrape CapCut or bundle its proprietary models.
 * MP3/M4A/WAV/FLAC/OGG support depends on Android codecs.
 */
class VoiceImportActivity:Activity() {
    private val PICK_AUDIO=3902
    private val RECORD_REQUEST=3903
    private var media:MediaPlayer?=null
    private var recorder:MediaRecorder?=null
    private var recordingFile:File?=null
    private var recordingSince=0L
    private lateinit var status:TextView
    private lateinit var list:LinearLayout
    private lateinit var recordingButton:Button
    private fun txt(s:String,size:Float=16f)=TextView(this).apply {
        text=s;textSize=size;setTextColor(Color.rgb(90,56,105));setPadding(7,8,7,8)
    }
    private fun bg(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=28f }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=1;setPadding(23,26,23,30);setBackgroundColor(Color.rgb(253,240,249))
        }
        root.addView(txt("🎧 Chibi Voice Library",28f))
        root.addView(txt("Kho âm thanh cá nhân • dùng offline làm báo thức • không yêu cầu Gemini",14f))
        val help=LinearLayout(this).apply { orientation=1;setPadding(17,14,17,14);background=bg(Color.rgb(240,229,255)) }
        help.addView(txt("🎙 Giọng từ CapCut",20f))
        help.addView(txt("Trong CapCut, tạo lời nói tiếng Việt bằng Text-to-Speech, xuất MP3/M4A/WAV khi tính năng hỗ trợ, rồi nhập file đó vào Chibi. Chỉ sử dụng âm thanh mà bạn có quyền dùng theo điều khoản CapCut.",14f))
        help.addView(Button(this).apply { text="🌐 Mở trang giọng CapCut";setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.capcut.com/vi-vn/tools/text-to-speech")))
            }catch(_:Exception){}
        } })
        root.addView(help)
        root.addView(Button(this).apply {
            text="📂 Nhập âm thanh từ CapCut hoặc điện thoại"
            setOnClickListener {
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type="audio/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },PICK_AUDIO)
                }catch(e:Exception){
                    status.text="Không mở được trình chọn tệp: "+e.message
                }
            }
        })
        recordingButton=Button(this).apply {
            text="🎤 Ghi âm giọng của tôi"
            setOnClickListener {
                if(recorder==null) {
                    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),RECORD_REQUEST)
                    }else startRecording()
                }else finishRecording()
            }
        }
        root.addView(recordingButton)
        root.addView(Button(this).apply { text="🎙 Mở giọng Việt hệ thống";setOnClickListener {
            startActivity(Intent(this@VoiceImportActivity,VoiceStudioActivity::class.java))
        } })
        status=txt("Bạn có thể lưu tối đa 15 file, dài 1 giây–5 phút, tối đa 20 MB/file.",14f)
        root.addView(status)
        root.addView(txt("🎵 Âm thanh đã lưu",21f))
        list=LinearLayout(this).apply { orientation=1 }
        root.addView(list)
        root.addView(Button(this).apply {
            text="■ Dừng nghe thử"
            setOnClickListener { stopPreview();status.text="Đã dừng phát thử" }
        })
        root.addView(Button(this).apply {
            text="🔄 Dùng giọng Việt TTS mặc định"
            setOnClickListener {
                stopPreview()
                VoiceClips.systemVoice(this@VoiceImportActivity)
                status.text="Đã chuyển sang giọng Android TTS"
                populate()
            }
        })
        root.addView(txt("Lưu ý: file xuất từ CapCut là lời đọc đã ghi sẵn. Chibi phát lại file này khi báo thức, không thể khiến giọng CapCut đọc nội dung mới tùy ý. Không nhập giọng người khác khi chưa được phép.",12f))
        setContentView(ScrollView(this).apply { addView(root) })
        populate()
    }
    private fun populate() {
        list.removeAllViews()
        val items=VoiceClips.clips(this)
        if(items.isEmpty())list.addView(txt("Chưa có file. Thử nhập lời đọc từ CapCut hoặc tự ghi âm nhé!",15f))
        items.forEach { item->
            val file=VoiceClips.file(this,item)
            val active=VoiceClips.chosen(this)==file.absolutePath &&
                getSharedPreferences("settings",0).getBoolean("aiVoiceEnabled",false)
            val card=LinearLayout(this).apply {
                orientation=1;setPadding(15,12,15,12)
                background=bg(if(active) Color.rgb(211,248,226) else Color.WHITE)
            }
            card.addView(txt((if(active) "✅ " else "🎧 ")+item.name,18f))
            card.addView(txt(item.kind+" • "+item.seconds+" giây",13f))
            val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply {
                text="▶ Thử";isAllCaps=false
                setOnClickListener { playClip(file) }
            },LinearLayout.LayoutParams(0,-2,1f))
            buttons.addView(Button(this).apply {
                text="✓ Chọn";isAllCaps=false
                setOnClickListener {
                    try {
                        VoiceClips.select(this@VoiceImportActivity,item)
                        status.text="Đã chọn làm giọng báo thức: "+item.name
                        populate()
                    }catch(e:Exception){status.text=e.message ?: "Không chọn được"}
                }
            },LinearLayout.LayoutParams(0,-2,1f))
            buttons.addView(Button(this).apply {
                text="🗑";isAllCaps=false
                setOnClickListener {
                    AlertDialog.Builder(this@VoiceImportActivity).setTitle("Xóa file?")
                        .setMessage("File sẽ bị xóa khỏi Chibi: "+item.name)
                        .setPositiveButton("Xóa"){_,_->
                            stopPreview();VoiceClips.delete(this@VoiceImportActivity,item);populate()
                        }.setNegativeButton("Hủy",null).show()
                }
            },LinearLayout.LayoutParams(0,-2,0.7f))
            card.addView(buttons)
            list.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=13 })
        }
    }
    private fun playClip(file:File) {
        stopPreview()
        try {
            media=MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stopPreview() }
                start()
            }
            status.text="🔊 Đang nghe thử: "+file.name
        }catch(e:Exception) {
            stopPreview();status.text="Không phát được âm thanh: "+e.message
        }
    }
    private fun stopPreview() {
        try { media?.stop() }catch(_:Exception){}
        try { media?.release() }catch(_:Exception){}
        media=null
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==PICK_AUDIO && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            val input=EditText(this).apply { hint="Ví dụ: Giọng nữ CapCut dễ thương";setText("Giọng CapCut của tôi") }
            AlertDialog.Builder(this).setTitle("Đặt tên file giọng")
                .setMessage("Chỉ nhập âm thanh có quyền sử dụng.")
                .setView(input)
                .setPositiveButton("Nhập & lưu"){_,_->
                    try {
                        val clip=VoiceClips.import(this,uri,input.text.toString(),"File do người dùng chọn")
                        status.text="✅ Đã lưu: "+clip.name;populate()
                    }catch(e:Exception){status.text="Không nhập được: "+e.message}
                }.setNegativeButton("Hủy",null).show()
        }
    }
    private fun startRecording() {
        try {
            recordingFile=File(cacheDir,"chibi_recording_${System.currentTimeMillis()}.m4a")
            val target=recordingFile ?: return
            recorder=if(Build.VERSION.SDK_INT>=31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(target.absolutePath)
                setMaxDuration(60_000)
                prepare()
                start()
            }
            recordingSince=SystemClock.elapsedRealtime()
            recordingButton.text="■ Dừng và lưu ghi âm"
            status.text="🔴 Đang ghi âm từ micro, tối đa 60 giây. Chỉ ghi giọng của bạn hoặc có sự đồng ý."
        }catch(e:Exception) {
            try { recorder?.release() }catch(_:Exception){}
            recorder=null
            recordingFile?.delete()
            status.text="Không thu âm được: "+e.message
        }
    }
    private fun finishRecording() {
        val r=recorder ?: return
        recorder=null;recordingButton.text="🎤 Ghi âm giọng của tôi"
        val file=recordingFile ?: return
        try {
            val duration=SystemClock.elapsedRealtime()-recordingSince
            r.stop();r.release()
            if(duration<1200L)throw IllegalStateException("Hãy ghi âm ít nhất 2 giây.")
            val input=EditText(this).apply {setText("Giọng tôi ghi âm")}
            AlertDialog.Builder(this).setTitle("🎤 Lưu ghi âm")
                .setView(input).setPositiveButton("Lưu"){_,_->
                    try {
                        val clip=VoiceClips.import(this,Uri.fromFile(file),input.text.toString(),"Tự ghi âm")
                        status.text="✅ Đã lưu: "+clip.name;populate()
                    }catch(e:Exception){status.text=e.message ?: "Không lưu được"}
                    finally {file.delete()}
                }.setNegativeButton("Bỏ"){_,_->file.delete()}.show()
        }catch(e:Exception) {
            try {r.release()}catch(_:Exception){}
            file.delete()
            status.text="Ghi âm không hợp lệ: "+e.message
        }
    }
    override fun onRequestPermissionsResult(code:Int,perms:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,perms,results)
        if(code==RECORD_REQUEST) {
            if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)startRecording()
            else status.text="Cần cho phép micro để tự ghi giọng. Nhập file có sẵn vẫn hoạt động."
        }
    }
    override fun onDestroy() {
        stopPreview()
        try { recorder?.stop() }catch(_:Exception){}
        try { recorder?.release() }catch(_:Exception){}
        recorder=null
        recordingFile?.delete()
        super.onDestroy()
    }
}
