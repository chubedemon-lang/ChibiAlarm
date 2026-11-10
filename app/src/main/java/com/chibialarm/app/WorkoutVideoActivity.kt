package com.chibialarm.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Parses only canonical HTTPS YouTube watch/short/embed links.
 * No stream extraction, arbitrary URL loading, or user-supplied HTML is allowed.
 */
internal object WorkoutVideoUrl {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val shortHosts = setOf("youtu.be")

    fun extractVideoId(raw: String): String? {
        if (raw.length !in 1..2048) return null
        return try {
            val uri = URI(raw.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return null

            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            val candidate = when {
                host in shortHosts && segments.size == 1 -> segments[0]
                host in youtubeHosts && segments == listOf("watch") -> singleVideoQueryValue(uri)
                host in youtubeHosts && segments.size == 2 &&
                    segments[0] in setOf("shorts", "embed", "live") -> segments[1]
                else -> null
            }
            candidate?.takeIf(videoIdPattern::matches)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }

    private fun singleVideoQueryValue(uri: URI): String? {
        val values = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 0) return@mapNotNull null
            val name = URLDecoder.decode(part.substring(0, separator), "UTF-8")
            if (name != "v") return@mapNotNull null
            URLDecoder.decode(part.substring(separator + 1), "UTF-8")
        }
        return values.singleOrNull()
    }
}

/** Plays user-selected YouTube videos through the official embed player. */
class WorkoutVideoActivity : Activity() {
    private var player: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = WorkoutVideoUrl.extractVideoId(intent.getStringExtra("url").orEmpty())
        if (videoId == null) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 28, 14, 16)
        }
        root.addView(TextView(this).apply {
            text = "▶ Video hướng dẫn • YouTube"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Video do bạn chọn. Hãy quan sát kỹ thuật và dừng nếu đau hoặc chóng mặt."
            textSize = 14f
        })

        val web = WebView(this)
        player = web
        configurePlayer(web)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "Mở trên YouTube"
            isAllCaps = false
            setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/watch?v=$videoId")
                    )
                )
            }
        })
        root.addView(Button(this).apply {
            text = "← Quay lại bài tập"
            isAllCaps = false
            setOnClickListener { finish() }
        })

        setContentView(root)
        val html = """
            <!doctype html>
            <html>
              <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
              <body style="margin:0;background:#111">
                <iframe
                  width="100%" height="100%"
                  src="https://www.youtube.com/embed/$videoId?playsinline=1"
                  title="YouTube exercise tutorial"
                  frameborder="0"
                  allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; web-share"
                  allowfullscreen>
                </iframe>
              </body>
            </html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }

    private fun configurePlayer(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true // Required by YouTube's official embed player.
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Keep top-level navigation on YouTube. The explicit button is the
                // only route that opens another app/browser.
                if (!request.isForMainFrame) return false
                return !isAllowedYoutubePage(request.url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !isAllowedYoutubePage(Uri.parse(url))
            }
        }
    }

    private fun isAllowedYoutubePage(uri: Uri): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "consent.youtube.com") &&
            (uri.port == -1 || uri.port == 443)
    }

    override fun onPause() {
        player?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.onResume()
    }

    override fun onDestroy() {
        player?.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        player = null
        super.onDestroy()
    }
}
