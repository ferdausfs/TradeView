package com.ferdausfs.tradeview.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Owns the chart WebView. Kotlin owns all data/logic; this class only forwards JSON to the
 * thin `window.chartApi` surface (spec §2) and receives crosshair events back.
 */
class ChartWebView(context: Context) : WebView(context) {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val pending = ArrayDeque<String>()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.parseColor("#131722"))
        configureSettings()
        webViewClient = AssetClient()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun loadChart() {
        loadUrl("https://appassets.androidplatform.net/assets/chart/index.html")
    }

    fun markReady() {
        ready = true
        flushPending()
    }

    /** Queue-or-run: nothing is lost if Kotlin pushes before the page finishes loading. */
    fun run(js: String) {
        main.post {
            if (!ready) {
                pending.addLast(js)
                if (pending.size > 8) pending.removeFirst()
                return@post
            }
            evaluateJavascript(js, null)
        }
    }

    private fun flushPending() {
        main.post {
            while (pending.isNotEmpty()) evaluateJavascript(pending.removeFirst(), null)
        }
    }

    fun setData(json: String) = run("window.chartApi.setData($json);")

    fun setIndicators(json: String) = run("window.chartApi.setIndicators($json);")

    fun setLogScale(on: Boolean) = run("window.chartApi.setLogScale(${if (on) "true" else "false"});")

    fun setTheme(dark: Boolean) = run("window.chartApi.setTheme(${if (dark) "true" else "false"});")

    fun fitContent() = run("window.chartApi.fitContent();")

    private inner class AssetClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return !url.startsWith("https://appassets.androidplatform.net/")
        }
    }
}