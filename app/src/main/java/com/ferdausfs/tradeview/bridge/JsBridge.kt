package com.ferdausfs.tradeview.bridge

import android.webkit.JavascriptInterface

/**
 * The single JS → Kotlin surface. Keep it thin: everything that is not chart rendering
 * lives on the Kotlin side (spec §2), so this interface only relays events.
 */
class JsBridge(
    private val onCrosshair: (time: Long, price: Double) -> Unit,
    private val onReady: () -> Unit,
    private val onVisibleRange: (from: Long, to: Long) -> Unit,
) {

    @JavascriptInterface
    fun onChartReady() {
        onReady()
    }

    @JavascriptInterface
    fun onCrosshairMove(time: Double, price: Double) {
        if (time <= 0.0 || price.isNaN() || price.isInfinite()) return
        onCrosshair(time.toLong(), price)
    }

    @JavascriptInterface
    fun onVisibleRangeChange(from: Double, to: Double) {
        onVisibleRange(from.toLong(), to.toLong())
    }
}
