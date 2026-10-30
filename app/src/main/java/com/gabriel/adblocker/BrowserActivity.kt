package com.gabriel.adblocker

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import java.io.ByteArrayInputStream

/** Small browser that drops requests to blocklisted hosts and hides leftover ad elements. */
class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var bar: EditText

    private val css = """
        [id*="google_ads"],[id^="ad-"],[id^="ad_"],[class*="adsbygoogle"],[class*="ad-banner"],
        [class*="advert"],[id*="advert"],[class*="sponsored"],ins.adsbygoogle,iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"],
        ytd-ad-slot-renderer,ytd-promoted-sparkles-web-renderer,#masthead-ad,.ytp-ad-overlay-container,
        .ytp-ad-module,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,
        ytd-display-ad-renderer,ytd-banner-promo-renderer{display:none!important}
    """.trimIndent().replace("\n", " ")

    // YouTube (web): click skip, or mute and jump to the end of any ad that is playing.
    private val js = """
        (function(){
          if (window.__abInstalled) return; window.__abInstalled = true;
          var st=document.createElement('style'); st.textContent='$css'; document.documentElement.appendChild(st);
          setInterval(function(){
            var p=document.querySelector('.html5-video-player.ad-showing, .html5-video-player.ad-interrupting');
            var skip=document.querySelector('.ytp-ad-skip-button,.ytp-skip-ad-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button');
            if (skip) skip.click();
            if (p) { var v=document.querySelector('video');
              if (v && isFinite(v.duration) && v.duration>0) { v.muted=true; v.currentTime=v.duration; } }
          }, 300);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Blocklist.ensureLoaded(this)
        bar = EditText(this).apply {
            hint = "Search or enter address"; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, r: WebResourceRequest): WebResourceResponse? {
                    val host = r.url.host?.lowercase() ?: return null
                    return if (Blocklist.isBlocked(host))
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
                }
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    bar.setText(url); view.evaluateJavascript(js, null)
                }
                override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript(js, null) }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        go(intent.dataString ?: "https://m.youtube.com")
    }

    private fun go(input: String) {
        val t = input.trim()
        web.loadUrl(when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            '.' in t && ' ' !in t -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
