package com.ferdausfs.tradeview.bridge

import android.webkit.JavascriptInterface

/**
 * The single JS → Kotlin surface. Keep it thin: only events, no logic (spec §2).
 */
class JsBridge(
    private val onCrosshair: (time: Long, price: Double) -> Unit,
    private val crosshairGone: () -> Unit,
    private val ready: () -> Unit,
) {

    @JavascriptInterface
    fun onChartReady() {
        ready()
    }

    @JavascriptInterface
    fun onCrosshairMove(time: Double, price: Double) {
        if (time <= 0.0 || price.isNaN() || price.isInfinite()) return
        onCrosshair(time.toLong(), price)
    }

    @JavascriptInterface
    fun onCrosshairGone() {
        crosshairGone()
    }
}