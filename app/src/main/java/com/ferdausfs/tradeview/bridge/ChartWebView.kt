package com.ferdausfs.tradeview.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Owns the WebView that renders the chart. Loads bundled assets only — no CDN at runtime
 * (lightweight-charts is pinned and shipped inside APK assets).
 */
class ChartWebView(context: Context) : WebView(context) {

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
            // Pinch zoom is handled by the chart itself, so the WebView must not claim it.
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

    fun setData(candlesJson: String) {
        evaluateJavascript("window.chartApi.setData($candlesJson);", null)
    }

    fun updateLast(candleJson: String) {
        evaluateJavascript("window.chartApi.update($candleJson);", null)
    }

    fun setTheme(dark: Boolean) {
        evaluateJavascript("window.chartApi.setTheme(${if (dark) "true" else "false"});", null)
    }

    private inner class AssetClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            // The chart page must never navigate away from the bundled shell.
            val url = request.url.toString()
            return !url.startsWith("https://appassets.androidplatform.net/")
        }
    }
}
