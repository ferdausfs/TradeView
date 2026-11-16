package com.ferdausfs.tradeview.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ferdausfs.tradeview.bridge.ChartWebView
import com.ferdausfs.tradeview.bridge.JsBridge
import com.ferdausfs.tradeview.data.Instruments
import com.ferdausfs.tradeview.data.Settings
import com.ferdausfs.tradeview.data.TickerStream
import com.ferdausfs.tradeview.data.YahooSource
import com.ferdausfs.tradeview.data.toTicker
import com.ferdausfs.tradeview.domain.model.DrawKind
import com.ferdausfs.tradeview.ui.common.Scrim
import com.ferdausfs.tradeview.ui.theme.LocalTokens
import kotlinx.coroutines.delay

@Composable
fun ChartScreen(vm: ChartViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val dataVersion by vm.dataVersion.collectAsState()
    val indicatorVersion by vm.indicatorVersion.collectAsState()
    val tickVersion by vm.tickVersion.collectAsState()
    val drawingsVersion by vm.drawingsVersion.collectAsState()
    val t = LocalTokens.current

    var logScale by remember { mutableStateOf(false) }
    var settingsFor by remember { mutableStateOf<IndicatorInstance?>(null) }
    var quotes by remember { mutableStateOf<Map<String, TickerStream.Quote>>(emptyMap()) }
    var watchlist by remember { mutableStateOf(Settings.watchlist()) }

    val webView = remember {
        ChartWebView(context).apply {
            addJavascriptInterface(
                JsBridge(
                    onCrosshair = vm::onCrosshair,
                    crosshairGone = vm::onCrosshairGone,
                    ready = { markReady() },
                    tap = vm::onTap,
                ),
                "AndroidBridge",
            )
        }
    }

    DisposableEffect(Unit) {
        webView.loadChart()
        onDispose {
            webView.removeJavascriptInterface("AndroidBridge")
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // Full redraw: candles + indicators (indicators depend on the visible slice).
    LaunchedEffect(dataVersion, indicatorVersion) {
        webView.setData(vm.candlesJson())
        webView.setIndicators(vm.indicatorsJson())
    }

    // Cheap path for streaming: patch only the last bar.
    LaunchedEffect(tickVersion) {
        vm.lastTick?.let { webView.updateLastBar(it) }
    }

    LaunchedEffect(drawingsVersion) {
        webView.setDrawings(vm.drawingsJson())
    }

    LaunchedEffect(state.instrument.id, state.timeframe) {
        webView.fitContent()
        while (true) {
            vm.refreshQuote()
            delay(45_000)
        }
    }

    // Toast auto-dismiss.
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2400)
            vm.consumeToast()
        }
    }

    // Quote polling while the browser sheet is open: crypto from Binance, forex from Yahoo.
    LaunchedEffect(state.browserOpen) {
        if (!state.browserOpen) return@LaunchedEffect
        val yahoo = YahooSource()
        while (true) {
            val crypto = runCatching { TickerStream().snapshot(Instruments.crypto) }.getOrNull()
            val fx = runCatching { yahoo.quotes(Instruments.forex) }.getOrNull()
            if (crypto != null) quotes = quotes + crypto
            if (!fx.isNullOrEmpty()) quotes = quotes + fx.mapValues { it.value.toTicker() }
            delay(20_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        ChartTopBar(
            instrument = state.instrument,
            timeframe = state.timeframe,
            last = state.crosshairCandle ?: state.lastCandle,
            quote = state.quote ?: quotes[state.instrument.id],
            sourceLabel = state.sourceLabel,
            live = state.live,
            loading = state.loading,
            replayActive = state.replayActive,
            tool = state.tool,
            onSymbolClick = { vm.toggleBrowser(true) },
            onLiveToggle = { vm.setLive(!state.live) },
            onIndicatorsClick = { vm.toggleIndicatorSheet(true) },
            onReplayClick = { if (state.replayActive) vm.exitReplay() else vm.startReplayBarsBack(120) },
            onSettingsClick = { vm.toggleSettings(true) },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            // TradingView-style floating drawing rail on the chart's left edge.
            DrawingRail(
                tool = state.tool,
                hasSelection = state.selectedDrawingId != null,
                hasDrawings = state.drawings.isNotEmpty(),
                onTool = vm::setTool,
                onDeleteSelected = vm::removeSelectedDrawing,
                onClearAll = vm::clearDrawings,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp),
            )

            if (vm.isDrawTool(state.tool)) {
                Text(
                    "${state.tool.label} · tap the chart",
                    color = t.accent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(t.panel.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                TvToast(state.toast)
            }
        }

        ReplayBar(
            visible = state.replayActive,
            playing = state.replayPlaying,
            index = state.replayIndex,
            total = state.replayTotal,
            speed = state.replaySpeed,
            barTime = state.candles.lastOrNull()?.time,
            onPlayPause = vm::togglePlay,
            onStepBack = vm::stepBack,
            onStepForward = vm::stepForward,
            onSpeed = vm::setReplaySpeed,
            onExit = vm::exitReplay,
            onGoToDate = vm::startReplayFromDay,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.panel)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeframeRow(selected = state.timeframe, onSelect = vm::setTimeframe)
        }

        ChartBottomBar(
            logScale = logScale,
            indicatorCount = state.indicators.size,
            onLogScale = { logScale = !logScale; webView.setLogScale(logScale) },
            onIndicators = { vm.toggleIndicatorSheet(true) },
            onFit = { webView.fitContent() },
            onBrowser = { vm.toggleBrowser(true) },
        )
    }

    // ---------------------------------------------------------------- sheets
    if (state.browserOpen) {
        Scrim(onClick = { vm.toggleBrowser(false) }) {
            BrowserSheet(
                selectedId = state.instrument.id,
                watchlist = watchlist,
                quotes = quotes,
                onSelect = { vm.setInstrument(it) },
                onToggleStar = { id ->
                    Settings.toggleFavorite(id)
                    watchlist = Settings.watchlist()
                },
                onClose = { vm.toggleBrowser(false) },
            )
        }
    }

    if (state.indicatorSheetOpen) {
        Scrim(onClick = { vm.toggleIndicatorSheet(false) }) {
            IndicatorSheet(
                instances = state.indicators,
                scripts = state.scripts,
                onAdd = vm::addIndicator,
                onRemove = vm::removeIndicator,
                onEditParams = { settingsFor = it },
                onOpenScriptEditor = vm::openScriptEditor,
                onAddScript = vm::addSavedScript,
                onDeleteScript = vm::deleteScript,
                onClose = { vm.toggleIndicatorSheet(false) },
            )
        }
    }

    if (state.scriptEditorOpen) {
        Scrim(onClick = vm::closeScriptEditor) {
            ScriptEditorSheet(
                draft = state.scriptDraft,
                error = state.scriptError,
                onDraftChange = vm::setScriptDraft,
                onPreview = vm::previewScript,
                onSave = vm::saveScript,
                onClose = vm::closeScriptEditor,
            )
        }
    }

    if (state.settingsOpen) {
        Scrim(onClick = { vm.toggleSettings(false) }) {
            SettingsSheet(
                live = state.live,
                keys = mapOf(
                    "twelvedata" to Settings.twelveDataKey(),
                    "tradermade" to Settings.traderMadeKey(),
                    "alphavantage" to Settings.alphaVantageKey(),
                ),
                drawingCount = state.drawings.size,
                logScale = logScale,
                onLive = vm::setLive,
                onLogScale = { logScale = it; webView.setLogScale(it) },
                onKeyChange = vm::setApiKey,
                onApplyKeys = vm::applyKeys,
                onClearDrawings = vm::clearDrawings,
                onClose = { vm.toggleSettings(false) },
            )
        }
    }

    settingsFor?.let { inst ->
        Scrim(onClick = { settingsFor = null }) {
            IndicatorSettingsSheet(
                instance = inst,
                onApply = { updated ->
                    vm.updateIndicator(updated)
                    settingsFor = null
                },
                onClose = { settingsFor = null },
            )
        }
    }
}
