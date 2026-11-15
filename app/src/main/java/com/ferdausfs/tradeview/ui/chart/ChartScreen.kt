package com.ferdausfs.tradeview.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ferdausfs.tradeview.bridge.ChartWebView
import com.ferdausfs.tradeview.bridge.JsBridge
import com.ferdausfs.tradeview.domain.SampleSeries
import com.ferdausfs.tradeview.domain.model.Timeframe
import com.ferdausfs.tradeview.ui.theme.TvBackground

@Composable
fun ChartScreen(vm: ChartViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val dataVersion by vm.dataVersion.collectAsState()
    val indicatorVersion by vm.indicatorVersion.collectAsState()

    var symbolSheet by remember { mutableStateOf(false) }
    var tfSheet by remember { mutableStateOf(false) }
    var settingsFor by remember { mutableStateOf<IndicatorInstance?>(null) }
    var logScale by remember { mutableStateOf(false) }

    val webView = remember {
        ChartWebView(context).apply {
            addJavascriptInterface(
                JsBridge(
                    onCrosshair = vm::onCrosshair,
                    crosshairGone = vm::onCrosshairGone,
                    ready = { markReady() },
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

    // Data + indicators are pushed whenever either version counter changes.
    LaunchedEffect(dataVersion, indicatorVersion) {
        webView.setData(vm.candlesJson())
        webView.setIndicators(vm.indicatorsJson())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .statusBarsPadding(),
    ) {
        ChartTopBar(
            symbol = state.symbol,
            timeframe = state.timeframe,
            last = state.crosshairCandle ?: state.lastCandle,
            replayActive = state.replayActive,
            onSymbolClick = { symbolSheet = true },
            onTimeframeClick = { tfSheet = true },
            onReplayClick = {
                if (state.replayActive) vm.exitReplay() else vm.startReplayBarsBack(120)
            },
            onIndicatorsClick = { vm.toggleIndicatorSheet(true) },
            onSettingsClick = { logScale = !logScale; webView.setLogScale(logScale) },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            state.crosshairCandle?.let { c ->
                Text(
                    "${c.time}  O ${c.open}  H ${c.high}  L ${c.low}  C ${c.close}",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(6.dp),
                )
            }
        }

        if (state.replayActive) {
            ReplayBar(
                playing = state.replayPlaying,
                index = state.replayIndex,
                total = state.replayTotal,
                speed = state.replaySpeed,
                onPlayPause = vm::togglePlay,
                onStepBack = vm::stepBack,
                onStepForward = vm::stepForward,
                onSpeed = vm::setReplaySpeed,
                onExit = vm::exitReplay,
            )
        }

        TimeframeRow(selected = state.timeframe, onSelect = vm::setTimeframe)

        ChartBottomBar(
            logScale = logScale,
            onLogScale = { logScale = !logScale; webView.setLogScale(logScale) },
            onIndicators = { vm.toggleIndicatorSheet(true) },
            onReplay = { if (state.replayActive) vm.exitReplay() else vm.startReplayBarsBack(120) },
            onFit = { webView.fitContent() },
        )
    }

    // ---- sheets (spec §4/§5: mobile pickers are bottom sheets, never dropdowns) ----
    if (symbolSheet) {
        SheetOverlay(onClose = { symbolSheet = false }) { close ->
            SymbolSheet(
                symbols = SampleSeries.symbols.map { it.symbol },
                selected = state.symbol,
                onSelect = { vm.setSymbol(it); close() },
                onClose = close,
            )
        }
    }

    if (tfSheet) {
        SheetOverlay(onClose = { tfSheet = false }) { close ->
            TimeframeSheet(
                selected = state.timeframe.label,
                onSelect = { label ->
                    Timeframe.entries.firstOrNull { it.label == label }?.let(vm::setTimeframe)
                    close()
                },
                onClose = close,
            )
        }
    }

    if (state.indicatorSheetOpen) {
        SheetOverlay(onClose = { vm.toggleIndicatorSheet(false) }) { close ->
            IndicatorSheet(
                instances = state.indicators,
                scripts = state.scripts,
                onAdd = vm::addIndicator,
                onRemove = vm::removeIndicator,
                onToggle = {},
                onEditParams = { inst -> settingsFor = inst },
                onOpenScriptEditor = vm::openScriptEditor,
                onAddScript = vm::addSavedScript,
                onDeleteScript = vm::deleteScript,
                onClose = close,
            )
        }
    }

    if (state.scriptEditorOpen) {
        SheetOverlay(onClose = vm::closeScriptEditor) { close ->
            ScriptEditorSheet(
                draft = state.scriptDraft,
                error = state.scriptError,
                onDraftChange = vm::setScriptDraft,
                onPreview = vm::previewScript,
                onSave = vm::saveScript,
                onClose = close,
            )
        }
    }

    settingsFor?.let { inst ->
        SheetOverlay(onClose = { settingsFor = null }) { close ->
            IndicatorSettingsSheet(
                instance = inst,
                onApply = { updated ->
                    vm.updateIndicator(updated)
                    close()
                },
                onClose = close,
            )
        }
    }
}

@Composable
private fun SheetOverlay(
    onClose: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = interaction, indication = null) { onClose() }
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content(onClose)
    }
}