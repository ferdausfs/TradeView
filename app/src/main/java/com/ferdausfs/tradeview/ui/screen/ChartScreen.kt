package com.ferdausfs.tradeview.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        // Top bar (spec §4): symbol + timeframe on the left, actions arrive in later phases.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvPanel)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BTCUSDT",
                color = TvText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "1m", color = TvText, fontSize = 13.sp)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            AndroidView(
                factory = { chart },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
