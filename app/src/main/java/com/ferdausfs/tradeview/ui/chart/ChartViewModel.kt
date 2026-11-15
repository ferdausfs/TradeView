package com.ferdausfs.tradeview.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferdausfs.tradeview.domain.Aggregator
import com.ferdausfs.tradeview.domain.Indicators
import com.ferdausfs.tradeview.domain.PineLite
import com.ferdausfs.tradeview.domain.ReplayEngine
import com.ferdausfs.tradeview.domain.SampleSeries
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import com.ferdausfs.tradeview.domain.model.toJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class IndicatorInstance(
    val id: String,
    val kind: String,          // EMA | SMA | BB | VWAP | RSI | MACD | ATR | SCRIPT
    val period: Int = 14,
    val color: String,
    val script: String? = null,
    val scriptLabel: String? = null,
) {
    val isOverlay: Boolean
        get() = kind == "EMA" || kind == "SMA" || kind == "BB" || kind == "VWAP"

    val title: String
        get() = when (kind) {
            "EMA" -> "EMA $period"
            "SMA" -> "SMA $period"
            "BB" -> "BB $period"
            "VWAP" -> "VWAP"
            "RSI" -> "RSI $period"
            "MACD" -> "MACD"
            "ATR" -> "ATR $period"
            "SCRIPT" -> scriptLabel ?: "Script"
            else -> kind
        }
}

@Serializable
data class SavedScript(val name: String, val code: String)

data class ChartUiState(
    val symbol: String = "BTCUSDT",
    val timeframe: Timeframe = Timeframe.M1,
    val candles: List<Candle> = emptyList(),
    val lastCandle: Candle? = null,
    val crosshairCandle: Candle? = null,
    val indicators: List<IndicatorInstance> = emptyList(),
    val scripts: List<SavedScript> = emptyList(),
    val replayActive: Boolean = false,
    val replayPlaying: Boolean = false,
    val replayIndex: Int = 0,
    val replayTotal: Int = 0,
    val replaySpeed: Double = 2.0,
    val symbolPickerOpen: Boolean = false,
    val indicatorSheetOpen: Boolean = false,
    val scriptEditorOpen: Boolean = false,
    val scriptDraft: String = "",
    val scriptError: String? = null,
    val toast: String? = null,
)

class ChartViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state

    /** Bumped whenever candles must be re-pushed to the WebView. */
    val dataVersion = MutableStateFlow(0)
    /** Bumped whenever indicator series must be re-pushed. */
    val indicatorVersion = MutableStateFlow(0)

    private var base1m: List<Candle> = emptyList()
    private val replay = ReplayEngine()
    private var replayJob: Job? = null

    private val prefs = HashMap<String, String>()

    init {
        restore()
        if (base1m.isEmpty()) {
            setSymbolInternal(_state.value.symbol)
        }
    }

    // ---------------------------------------------------------------- data

    private fun rebuild() {
        val s = _state.value
        val aggregated = Aggregator.aggregate(base1m, s.timeframe)
        val visible = if (replay.isActive) replay.visible().let { Aggregator.aggregate(it, s.timeframe) } else aggregated
        _state.update {
            it.copy(
                candles = visible,
                lastCandle = visible.lastOrNull(),
                crosshairCandle = null,
            )
        }
        dataVersion.value++
        pushIndicators()
    }

    /** Bump so ChartScreen re-serialises and pushes the indicator series to the chart. */
    private fun pushIndicators() {
        indicatorVersion.value++
    }

    fun setSymbol(symbol: String) {
        setSymbolInternal(symbol)
        persist()
    }

    private fun setSymbolInternal(symbol: String) {
        base1m = SampleSeries.generate(symbol, Timeframe.M1, count = 900)
        if (replay.isActive) replay.exit()
        _state.update { it.copy(symbol = symbol, symbolPickerOpen = false) }
        rebuild()
    }

    fun setTimeframe(tf: Timeframe) {
        if (tf == _state.value.timeframe) return
        _state.update { it.copy(timeframe = tf) }
        rebuild()
        persist()
    }

    // ---------------------------------------------------------------- indicators

    fun addIndicator(kind: String) {
        val color = nextColor(_state.value.indicators.size)
        val instance = IndicatorInstance(
            id = "$kind-${System.currentTimeMillis()}",
            kind = kind,
            period = defaultPeriod(kind),
            color = color,
        )
        _state.update { it.copy(indicators = it.indicators + instance) }
        pushIndicators()
        persist()
    }

    fun removeIndicator(id: String) {
        _state.update { st -> st.copy(indicators = st.indicators.filterNot { it.id == id }) }
        pushIndicators()
        persist()
    }

    fun updateIndicator(instance: IndicatorInstance) {
        _state.update { st -> st.copy(indicators = st.indicators.map { if (it.id == instance.id) instance else it }) }
        pushIndicators()
        persist()
    }

    fun openScriptEditor() {
        _state.update { it.copy(scriptEditorOpen = true, scriptDraft = it.scriptDraft.ifEmpty { "plot(ema(close, 20))" }, scriptError = null, indicatorSheetOpen = false) }
    }

    fun closeScriptEditor() {
        _state.update { it.copy(scriptEditorOpen = false) }
    }

    fun setScriptDraft(text: String) {
        _state.update { it.copy(scriptDraft = text, scriptError = null) }
    }

    /** Compile + live-preview the draft on the current symbol without saving. */
    fun previewScript() {
        val draft = _state.value.scriptDraft
        val result = PineLite.compile(draft, _state.value.candles)
        result.fold(
            onSuccess = {
                val instance = IndicatorInstance(
                    id = "SCRIPT-preview",
                    kind = "SCRIPT",
                    color = "#F0B90B",
                    script = draft,
                    scriptLabel = it.label,
                )
                _state.update { st -> st.copy(indicators = st.indicators.filterNot { it.id == "SCRIPT-preview" } + instance, scriptError = null) }
                pushIndicators()
            },
            onFailure = { e ->
                _state.update { it.copy(scriptError = e.message ?: "error") }
            },
        )
    }

    fun saveScript() {
        val s = _state.value
        val result = PineLite.compile(s.scriptDraft, s.candles)
        result.fold(
            onSuccess = { compiled ->
                val name = compiled.label.removePrefix("S: ").ifEmpty { "script" }
                val saved = SavedScript(name = name, code = s.scriptDraft)
                val scripts = s.scripts.filterNot { it.code == saved.code } + saved
                _state.update {
                    it.copy(
                        scripts = scripts,
                        indicators = it.indicators.map { ind ->
                            if (ind.id == "SCRIPT-preview") ind.copy(id = "SCRIPT-${System.currentTimeMillis()}", scriptLabel = compiled.label) else ind
                        },
                        scriptEditorOpen = false,
                        toast = "Script saved",
                    )
                }
                pushIndicators()
                persist()
            },
            onFailure = { e -> _state.update { it.copy(scriptError = e.message ?: "error") } },
        )
    }

    fun loadScript(saved: SavedScript) {
        _state.update { it.copy(scriptDraft = saved.code, scriptEditorOpen = true, scriptError = null) }
    }

    fun addSavedScript(saved: SavedScript) {
        val instance = IndicatorInstance(
            id = "SCRIPT-${System.currentTimeMillis()}",
            kind = "SCRIPT",
            color = nextColor(_state.value.indicators.size),
            script = saved.code,
            scriptLabel = "S: " + saved.name.take(24),
        )
        _state.update { it.copy(indicators = it.indicators + instance, indicatorSheetOpen = false) }
        pushIndicators()
        persist()
    }

    fun deleteScript(saved: SavedScript) {
        _state.update { st -> st.copy(scripts = st.scripts.filterNot { it == saved }) }
        persist()
    }

    // ---------------------------------------------------------------- replay

    fun startReplayBarsBack(barsBack: Int) {
        val base = Aggregator.aggregate(base1m, _state.value.timeframe)
        if (base.size < 30) return
        val start = (base.size - barsBack).coerceIn(10, base.size)
        replay.arm(base, start)
        _state.update {
            it.copy(
                replayActive = true,
                replayPlaying = false,
                replayIndex = start,
                replayTotal = base.size,
                toast = null,
            )
        }
        rebuild()
        startTicker()
    }

    fun togglePlay() {
        if (!replay.isActive) return
        if (replay.isPlaying) {
            replay.pause()
            replayJob?.cancel()
        } else {
            replay.play()
            startTicker()
        }
        _state.update { it.copy(replayPlaying = replay.isPlaying) }
    }

    fun stepForward() {
        if (!replay.isActive) return
        replay.stepForward()
        publishReplay()
    }

    fun stepBack() {
        if (!replay.isActive) return
        replay.stepBack()
        publishReplay()
    }

    fun setReplaySpeed(perSecond: Double) {
        replay.setSpeed(perSecond)
        _state.update { it.copy(replaySpeed = perSecond) }
    }

    fun exitReplay() {
        replayJob?.cancel()
        replay.exit()
        _state.update { it.copy(replayActive = false, replayPlaying = false, replayIndex = 0, replayTotal = 0) }
        rebuild()
    }

    private fun startTicker() {
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive && replay.isPlaying) {
                delay(100)
                val now = System.nanoTime()
                val dt = (now - last) / 1_000_000_000.0
                last = now
                replay.tick(dt)
                publishReplay()
            }
        }
    }

    private fun publishReplay() {
        val snap = replay.snapshot()
        val visible = replay.visible().let { Aggregator.aggregate(it, _state.value.timeframe) }
        _state.update {
            it.copy(
                candles = visible,
                lastCandle = visible.lastOrNull(),
                crosshairCandle = null,
                replayIndex = snap.visibleCount,
                replayTotal = snap.total,
                replayPlaying = snap.playing,
            )
        }
        dataVersion.value++
        pushIndicators()
    }

    // ---------------------------------------------------------------- crosshair / legend

    fun onCrosshair(timeSec: Long, price: Double) {
        val hit = _state.value.candles.firstOrNull { it.time == timeSec }
        if (hit != null) _state.update { it.copy(crosshairCandle = hit) }
    }

    fun onCrosshairGone() {
        _state.update { it.copy(crosshairCandle = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    // ---------------------------------------------------------------- sheet toggles

    fun toggleSymbolPicker(open: Boolean) = _state.update { it.copy(symbolPickerOpen = open) }
    fun toggleIndicatorSheet(open: Boolean) = _state.update { it.copy(indicatorSheetOpen = open) }

    // ---------------------------------------------------------------- bridge payloads

    fun candlesJson(): String = _state.value.candles.toJsonArray()

    /**
     * Builds the indicator payload: one entry per pane. Pane 0 is the main (price) pane;
     * every non-overlay indicator gets its own pane.
     */
    fun indicatorsJson(): String {
        val candles = _state.value.candles
        if (candles.size < 5) return "[]"
        val overlays = ArrayList<String>()
        val panes = ArrayList<String>()

        for (instance in _state.value.indicators) {
            when (instance.kind) {
                "EMA" -> overlays += """{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, Indicators.ema(closes(candles), instance.period))}}"""
                "SMA" -> overlays += """{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, Indicators.sma(closes(candles), instance.period))}}"""
                "BB" -> {
                    val bb = Indicators.bollinger(candles, instance.period)
                    overlays += """{"kind":"line","color":"${instance.color}","width":1,"data":${lineData(candles, bb.upper)}}"""
                    overlays += """{"kind":"line","color":"${instance.color}","width":1,"data":${lineData(candles, bb.middle)}}"""
                    overlays += """{"kind":"line","color":"${instance.color}","width":1,"data":${lineData(candles, bb.lower)}}"""
                }
                "VWAP" -> overlays += """{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, Indicators.vwap(candles))}}"""
                "RSI" -> panes += """{"series":[{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, Indicators.rsi(candles, instance.period))}}],"fixedRange":{"min":0,"max":100}}"""
                "MACD" -> {
                    val (macd, signal, hist) = Indicators.macd(closes(candles))
                    panes += """{"series":[{"kind":"histogram","color":"#26A69A","width":1,"data":${histData(candles, hist)}},{"kind":"line","color":"#2962FF","width":2,"data":${lineData(candles, macd)}},{"kind":"line","color":"#FF9800","width":2,"data":${lineData(candles, signal)}}]}"""
                }
                "ATR" -> panes += """{"series":[{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, Indicators.atr(candles, instance.period))}}]}"""
                "SCRIPT" -> {
                    if (instance.script != null) {
                        PineLite.compile(instance.script, candles).fold(
                            onSuccess = {
                                panes += """{"series":[{"kind":"line","color":"${instance.color}","width":2,"data":${lineData(candles, it.values)}}]}"""
                            },
                            onFailure = { },
                        )
                    }
                }
            }
        }
        return """{"overlays":[${overlays.joinToString(",")}],"panes":[${panes.joinToString(",")}]}"""
    }

    private fun closes(candles: List<Candle>) = candles.map { it.close }

    private fun lineData(candles: List<Candle>, values: List<Double?>): String {
        val sb = StringBuilder("[")
        var first = true
        values.forEachIndexed { i, v ->
            if (v == null || v.isNaN() || v.isInfinite()) return@forEachIndexed
            if (!first) sb.append(',')
            first = false
            sb.append("""{"time":${candles[i].time},"value":${fmt(v)}}""")
        }
        return sb.append(']').toString()
    }

    private fun histData(candles: List<Candle>, values: List<Double?>): String {
        val sb = StringBuilder("[")
        var first = true
        values.forEachIndexed { i, v ->
            if (v == null || v.isNaN() || v.isInfinite()) return@forEachIndexed
            if (!first) sb.append(',')
            first = false
            val color = if (v >= 0) "#26A69A" else "#EF5350"
            sb.append("""{"time":${candles[i].time},"value":${fmt(v)},"color":"$color"}""")
        }
        return sb.append(']').toString()
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    // ---------------------------------------------------------------- persistence

    private fun persist() {
        val s = _state.value
        prefs["symbol"] = s.symbol
        prefs["tf"] = s.timeframe.name
        prefs["indicators"] = json.encodeToString(ListSerializer(IndicatorInstance.serializer()), s.indicators)
        prefs["scripts"] = json.encodeToString(ListSerializer(SavedScript.serializer()), s.scripts)
        Store.write(prefs)
    }

    private fun restore() {
        val map = Store.read() ?: return
        prefs.putAll(map)
        val symbol = map["symbol"] ?: "BTCUSDT"
        val tf = map["tf"]?.let { runCatching { Timeframe.valueOf(it) }.getOrNull() } ?: Timeframe.M1
        val indicators = map["indicators"]?.let {
            runCatching { json.decodeFromString(ListSerializer(IndicatorInstance.serializer()), it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val scripts = map["scripts"]?.let {
            runCatching { json.decodeFromString(ListSerializer(SavedScript.serializer()), it) }.getOrDefault(emptyList())
        } ?: emptyList()
        base1m = SampleSeries.generate(symbol, Timeframe.M1, count = 900)
        val aggregated = Aggregator.aggregate(base1m, tf)
        _state.update {
            it.copy(
                symbol = symbol,
                timeframe = tf,
                candles = aggregated,
                lastCandle = aggregated.lastOrNull(),
                indicators = indicators,
                scripts = scripts,
            )
        }
        dataVersion.value++
    }

    private fun setSymbolInternalIfNeeded() {}

    private fun nextColor(index: Int): String {
        val palette = listOf("#2962FF", "#FF9800", "#AB47BC", "#26C6DA", "#EC407A", "#8BC34A", "#FF7043")
        return palette[index % palette.size]
    }

    private fun defaultPeriod(kind: String): Int = when (kind) {
        "EMA" -> 20
        "SMA" -> 50
        "BB" -> 20
        "RSI" -> 14
        "ATR" -> 14
        else -> 14
    }
}
