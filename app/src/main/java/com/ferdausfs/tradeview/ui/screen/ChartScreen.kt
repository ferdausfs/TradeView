package com.ferdausfs.tradeview.ui.screen

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ferdausfs.tradeview.bridge.ChartWebView
import com.ferdausfs.tradeview.bridge.JsBridge
import com.ferdausfs.tradeview.ui.theme.TvBackground
import com.ferdausfs.tradeview.ui.theme.TvPanel
import com.ferdausfs.tradeview.ui.theme.TvText

@Composable
fun ChartScreen() {
    val context = LocalContext.current
    val chart = remember {
        ChartWebView(context).apply {
            addJavascriptInterface(
                JsBridge(
                    onCrosshair = { _, _ -> },
                    onReady = {},
                    onVisibleRange = { _, _ -> },
                ),
                "AndroidBridge",
            )
        }
    }

    DisposableEffect(Unit) {
        chart.loadChart()
        onDispose {
            chart.removeJavascriptInterface("AndroidBridge")
            (chart.parent as? android.view.ViewGroup)?.removeView(chart)
            chart.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        AndroidView(
            factory = { chart },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar per spec §4: symbol + timeframe on the left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(TvPanel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BTCUSDT",
                color = TvText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "1m",
                color = TvText,
                fontSize = 13.sp,
            )
        }
    }
}

@Suppress("unused")
private fun WebView.unused() = Unit
