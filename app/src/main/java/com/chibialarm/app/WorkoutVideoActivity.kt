package com.chibialarm.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView

/** Plays user-selected YouTube videos through the official embed player, without extracting streams. */
class WorkoutVideoActivity:Activity() {
    private var player:WebView?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val raw=intent.getStringExtra("url").orEmpty()
        val uri=Uri.parse(raw)
        val host=uri.host.orEmpty().lowercase()
        val id=when(host) {
            "youtu.be" -> uri.pathSegments.firstOrNull()
            "www.youtube.com","youtube.com","m.youtube.com" ->
                if(uri.path?.startsWith("/shorts/")==true || uri.path?.startsWith("/embed/")==true)
                    uri.pathSegments.getOrNull(1) else uri.getQueryParameter("v")
            else -> null
        }
        if(id==null || !id.matches(Regex("[A-Za-z0-9_-]{11}"))) { finish();return }
        val root=LinearLayout(this).apply { orientation=1;setPadding(14,28,14,16) }
        root.addView(TextView(this).apply { text="▶ Video hướng dẫn • YouTube";textSize=22f })
        root.addView(TextView(this).apply {
            text="Video do bạn chọn. Hãy quan sát kỹ thuật và dừng nếu đau hoặc chóng mặt.";textSize=14f
        })
        val web=WebView(this)
        player=web
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.webChromeClient=WebChromeClient()
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { text="Mở trên YouTube";setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+id)))
        } })
        root.addView(Button(this).apply { text="← Quay lại bài tập";setOnClickListener { finish() } })
        setContentView(root)
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:#111'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/"+id+"?playsinline=1' title='YouTube exercise tutorial' frameborder='0' allow='accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture' allowfullscreen></iframe></body></html>"
        web.loadDataWithBaseURL("https://www.youtube.com",html,"text/html","UTF-8",null)
    }
    override fun onPause() { player?.onPause();super.onPause() }
    override fun onResume() { super.onResume();player?.onResume() }
    override fun onDestroy() { player?.destroy();player=null;super.onDestroy() }
}
