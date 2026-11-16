package com.ferdausfs.tradeview.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferdausfs.tradeview.data.BinanceKlineStream
import com.ferdausfs.tradeview.data.HistoryRepository
import com.ferdausfs.tradeview.data.Instruments
import com.ferdausfs.tradeview.data.Settings
import com.ferdausfs.tradeview.data.TickerStream
import com.ferdausfs.tradeview.data.YahooSource
import com.ferdausfs.tradeview.data.toTicker
import com.ferdausfs.tradeview.domain.Aggregator
import com.ferdausfs.tradeview.domain.DrawModel
import com.ferdausfs.tradeview.domain.Indicators
import com.ferdausfs.tradeview.domain.PineLite
import com.ferdausfs.tradeview.domain.ReplayEngine
import com.ferdausfs.tradeview.domain.SampleSeries
import com.ferdausfs.tradeview.domain.model.Anchor
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.DrawKind
import com.ferdausfs.tradeview.domain.model.Drawing
import com.ferdausfs.tradeview.domain.model.Feed
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Timeframe
import com.ferdausfs.tradeview.domain.model.toJson
import com.ferdausfs.tradeview.domain.model.toJsonArray
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ChartUiState(
    val instrument: Instrument = Instruments.default(),
    val timeframe: Timeframe = Timeframe.M1,
    val candles: List<Candle> = emptyList(),
    val lastCandle: Candle? = null,
    val crosshairCandle: Candle? = null,
    val sourceLabel: String = "Loading",
    val loading: Boolean = true,
    val live: Boolean = Settings.liveEnabled,
    val quote: TickerStream.Quote? = null,
    val indicators: List<IndicatorInstance> = emptyList(),
    val scripts: List<SavedScript> = emptyList(),
    val drawings: List<Drawing> = emptyList(),
    val tool: DrawKind = DrawKind.Cursor,
    val selectedDrawingId: String? = null,
    val replayActive: Boolean = false,
    val replayPlaying: Boolean = false,
    val replayIndex: Int = 0,
    val replayTotal: Int = 0,
    val replaySpeed: Double = 2.0,
    val indicatorSheetOpen: Boolean = false,
    val browserOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val scriptEditorOpen: Boolean = false,
    val scriptDraft: String = "",
    val scriptError: String? = null,
    val toast: String? = null,
)

class ChartViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val history = HistoryRepository()
    private val klines = BinanceKlineStream()
    private val ticker = TickerStream()
    private val yahoo = YahooSource()
    private val drawModel = DrawModel()

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state

    /** Bumped whenever candles must be re-pushed to the WebView. */
    val dataVersion = MutableStateFlow(0)

    /** Bumped whenever indicator series must be re-pushed. */
    val indicatorVersion = MutableStateFlow(0)

    /** Bumped on a single live bar update (cheap path: JS `update`, not `setData`). */
    val tickVersion = MutableStateFlow(0)
    var lastTick: Candle? = null
        private set

    val drawingsVersion = MutableStateFlow(0)

    private var full: List<Candle> = emptyList()
    private val replay = ReplayEngine()
    private var replayJob: Job? = null
    private var streamJob: Job? = null
    private var pollJob: Job? = null
    private var prefs = HashMap<String, String>()

    init {
        restore()
        loadHistory()
    }

    // ---------------------------------------------------------------- data

    fun loadHistory() {
        val s = _state.value
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = history.load(s.instrument, s.timeframe, limit = if (s.timeframe == Timeframe.D1) 500 else 720)
            full = result.candles
            if (replay.isActive) replay.arm(Aggregator.aggregate(full, s.timeframe), replay.snapshot().visibleCount)
            _state.update { it.copy(sourceLabel = result.kind, loading = false) }
            rebuild()
            startLiveFeed()
        }
    }

    private fun rebuild() {
        val s = _state.value
        val aggregated = Aggregator.aggregate(full, s.timeframe)
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

    fun setInstrument(instrument: Instrument) {
        if (instrument.id == _state.value.instrument.id) {
            _state.update { it.copy(browserOpen = false) }
            return
        }
        if (replay.isActive) exitReplay()
        _state.update { it.copy(instrument = instrument, browserOpen = false) }
        persist()
        loadHistory()
    }

    fun setTimeframe(tf: Timeframe) {
        if (tf == _state.value.timeframe) return
        if (replay.isActive) exitReplay()
        _state.update { it.copy(timeframe = tf) }
        loadHistory()
        persist()
    }

    // ---------------------------------------------------------------- live feed

    fun setLive(enabled: Boolean) {
        Settings.liveEnabled = enabled
        _state.update { it.copy(live = enabled) }
        if (enabled) startLiveFeed() else stopLiveFeed()
    }

    private fun startLiveFeed() {
        stopLiveFeed()
        val s = _state.value
        if (!s.live || replay.isActive) return
        val instrument = s.instrument
        val tf = s.timeframe
        when (history.effectiveFeed(instrument)) {
            Feed.BINANCE -> streamJob = viewModelScope.launch {
                klines.candles(instrument, tf).collect { update ->
                    applyLiveCandle(update.candle, finalized = update.closed)
                }
            }
            Feed.TWELVEDATA, Feed.YAHOO -> pollJob = viewModelScope.launch {
                while (isActive) {
                    delay(45_000)
                    runCatching { history.fetch(instrument, tf, limit = 8) }.getOrNull()
                        ?.lastOrNull()
                        ?.let { applyLiveCandle(it, finalized = false) }
                }
            }
            Feed.DEMO -> Unit // demo series has no live source; static until refreshed
        }
    }

    private fun stopLiveFeed() {
        streamJob?.cancel(); streamJob = null
        pollJob?.cancel(); pollJob = null
    }

    /** Merges a streaming bar into [full] without rebuilding the whole series. */
    private fun applyLiveCandle(candle: Candle, finalized: Boolean) {
        if (full.isEmpty()) return
        val step = _state.value.timeframe.seconds
        val existing = full.indexOfLast { it.time == candle.time }
        full = when {
            existing >= 0 -> full.toMutableList().also { it[existing] = candle }
            candle.time > full.last().time && candle.time - full.last().time <= step * 4 -> full + candle
            else -> return
        }
        val visible = _state.value.candles
        val idx = visible.indexOfLast { it.time == candle.time }
        val newVisible = when {
            idx >= 0 -> visible.toMutableList().also { it[idx] = candle }
            visible.isNotEmpty() && candle.time > visible.last().time -> visible + candle
            else -> visible
        }
        _state.update { it.copy(candles = newVisible, lastCandle = newVisible.lastOrNull()) }
        lastTick = candle
        tickVersion.value++
        if (finalized) pushIndicators()
    }

    /** Quote strip data: crypto via Binance ticker, forex via Yahoo spark (single batched call). */
    fun refreshQuote() {
        val instrument = _state.value.instrument
        if (history.effectiveFeed(instrument) == Feed.DEMO) return
        viewModelScope.launch {
            val eff = history.effectiveFeed(instrument)
            val q = if (eff == Feed.BINANCE) {
                ticker.snapshot(listOf(instrument))[instrument.id]
            } else {
                yahoo.quotes(listOf(instrument))[instrument.id]?.toTicker()
            }
            q?.let { q2 -> _state.update { st -> st.copy(quote = q2) } }
        }
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
        _state.update { it.copy(indicators = it.indicators + instance, indicatorSheetOpen = false) }
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

    fun toggleIndicatorSheet(open: Boolean) = _state.update { it.copy(indicatorSheetOpen = open) }

    private fun pushIndicators() {
        indicatorVersion.value++
    }

    // ---------------------------------------------------------------- scripts

    fun openScriptEditor() {
        _state.update {
            it.copy(
                scriptEditorOpen = true,
                scriptDraft = it.scriptDraft.ifEmpty { "plot(ema(close, 20))" },
                scriptError = null,
                indicatorSheetOpen = false,
            )
        }
    }

    fun closeScriptEditor() = _state.update { it.copy(scriptEditorOpen = false) }

    fun setScriptDraft(text: String) = _state.update { it.copy(scriptDraft = text, scriptError = null) }

    /** Compile + live-preview the draft on the current symbol without saving. */
    fun previewScript() {
        val draft = _state.value.scriptDraft
        PineLite.compile(draft, _state.value.candles).fold(
            onSuccess = {
                val instance = IndicatorInstance(
                    id = PreviewScriptId,
                    kind = "SCRIPT",
                    color = "#F0B90B",
                    script = draft,
                    scriptLabel = it.label,
                )
                _state.update { st ->
                    st.copy(indicators = st.indicators.filterNot { it.id == PreviewScriptId } + instance, scriptError = null)
                }
                pushIndicators()
            },
            onFailure = { e -> _state.update { it.copy(scriptError = e.message ?: "error") } },
        )
    }

    fun saveScript() {
        val s = _state.value
        PineLite.compile(s.scriptDraft, s.candles).fold(
            onSuccess = { compiled ->
                val name = compiled.label.removePrefix("S: ").ifEmpty { "script" }
                val saved = SavedScript(name = name, code = s.scriptDraft)
                _state.update {
                    it.copy(
                        scripts = it.scripts.filterNot { sc -> sc.code == saved.code } + saved,
                        indicators = it.indicators.map { ind ->
                            if (ind.id == PreviewScriptId) {
                                ind.copy(id = "SCRIPT-${System.currentTimeMillis()}", scriptLabel = compiled.label)
                            } else ind
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

    // ---------------------------------------------------------------- drawings (P6)

    fun setTool(tool: DrawKind) {
        drawModel.cancelPending()
        _state.update {
            it.copy(
                tool = tool,
                selectedDrawingId = null,
                drawings = drawModel.all(),
                toast = when (tool) {
                    DrawKind.TrendLine -> "Tap two points to draw a trend line"
                    DrawKind.Horizontal -> "Tap the price level"
                    DrawKind.Rectangle -> "Tap two corners"
                    else -> null
                },
            )
        }
        publishDrawings()
    }

    /** A tap coming from the WebView in data space. */
    fun onTap(timeSec: Long, price: Double) {
        val tool = _state.value.tool
        if (!drawModel.hasTool(tool)) {
            // Cursor mode: select or deselect whatever is under the finger.
            drawModel.hitTest(Anchor(timeSec, price), visiblePriceSpan())
            _state.update { it.copy(selectedDrawingId = drawModel.selectedId, drawings = drawModel.all()) }
            publishDrawings()
            return
        }
        when (drawModel.addPoint(tool, Anchor(timeSec, price))) {
            "created" -> {
                _state.update {
                    it.copy(
                        tool = DrawKind.Cursor,
                        drawings = drawModel.all(),
                        toast = "Drawing added",
                    )
                }
                publishDrawings()
                persist()
            }
            "armed" -> _state.update {
                it.copy(toast = if (tool == DrawKind.Horizontal) null else "Now tap the second point")
            }
        }
    }

    fun removeSelectedDrawing() {
        drawModel.removeSelected()
        _state.update { it.copy(drawings = drawModel.all(), selectedDrawingId = null, tool = DrawKind.Cursor) }
        publishDrawings()
        persist()
    }

    fun clearDrawings() {
        drawModel.clearAll()
        _state.update { it.copy(drawings = emptyList(), selectedDrawingId = null) }
        publishDrawings()
        persist()
    }

    /** Kotlin cannot read the JS viewport synchronously; approximate from the visible bars. */
    private fun visiblePriceSpan(): Double {
        val visible = _state.value.candles
        if (visible.isEmpty()) return 0.0
        val hi = visible.maxOf { it.high }
        val lo = visible.minOf { it.low }
        return (hi - lo).coerceAtLeast(hi * 0.001)
    }

    private fun publishDrawings() {
        drawingsVersion.value++
    }

    fun drawingsJson(): String = _state.value.drawings.toJson()

    // ---------------------------------------------------------------- replay

    fun startReplayBarsBack(barsBack: Int) {
        val base = Aggregator.aggregate(full, _state.value.timeframe)
        if (base.size < 30) return
        val start = (base.size - barsBack).coerceIn(10, base.size)
        replay.arm(base, start)
        stopLiveFeed()
        _state.update {
            it.copy(
                replayActive = true,
                replayPlaying = false,
                replayIndex = start,
                replayTotal = base.size,
                tool = DrawKind.Cursor,
                toast = null,
            )
        }
        rebuild()
        startTicker()
    }

    /**
     * TradingView-style replay: the trader types a day ("2025-03-14") and the chart arms with
     * history up to that date — every bar after it is unknown until played forward.
     */
    fun startReplayFromDay(day: String) {
        val base = Aggregator.aggregate(full, _state.value.timeframe)
        val target = runCatching {
            java.time.LocalDate.parse(day.trim()).toEpochDay() * 86_400L
        }.getOrNull()
        if (base.size < 30 || target == null) {
            _state.update { it.copy(toast = if (target == null) "Date format: YYYY-MM-DD" else "Not enough history") }
            return
        }
        val index = base.indexOfFirst { it.time >= target }
        val start = when {
            index < 0 -> base.size // date is after all bars: replay from the end
            else -> index
        }.coerceIn(10, base.size)
        if (start <= 10) {
            _state.update { it.copy(toast = "No data before that date") }
            return
        }
        replay.arm(base, start)
        stopLiveFeed()
        _state.update {
            it.copy(
                replayActive = true,
                replayPlaying = false,
                replayIndex = start,
                replayTotal = base.size,
                tool = DrawKind.Cursor,
                toast = "Replay from $day",
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

    fun stepForward() { if (replay.isActive) { replay.stepForward(); publishReplay() } }

    fun stepBack() { if (replay.isActive) { replay.stepBack(); publishReplay() } }

    fun setReplaySpeed(perSecond: Double) {
        replay.setSpeed(perSecond)
        _state.update { it.copy(replaySpeed = perSecond) }
    }

    fun exitReplay() {
        replayJob?.cancel()
        replay.exit()
        _state.update { it.copy(replayActive = false, replayPlaying = false, replayIndex = 0, replayTotal = 0) }
        rebuild()
        startLiveFeed()
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

    // ---------------------------------------------------------------- crosshair / misc

    fun onCrosshair(timeSec: Long, price: Double) {
        val hit = _state.value.candles.firstOrNull { it.time == timeSec } ?: return
        _state.update { it.copy(crosshairCandle = hit) }
    }

    fun onCrosshairGone() = _state.update { it.copy(crosshairCandle = null) }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun toggleBrowser(open: Boolean) = _state.update { it.copy(browserOpen = open) }

    fun toggleSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun isDrawTool(tool: DrawKind): Boolean = drawModel.hasTool(tool)


    fun setTwelveDataKey(key: String) {
        Settings.setTwelveDataKey(key)
        loadHistory()
    }

    // ---------------------------------------------------------------- bridge payloads

    fun candlesJson(): String = _state.value.candles.toJsonArray()

    /**
     * Builds the indicator payload: overlays on the price pane, one entry per oscillator pane.
     */
    fun indicatorsJson(): String {
        val candles = _state.value.candles
        if (candles.size < 5) return """{"overlays":[],"panes":[]}"""
        val overlays = ArrayList<String>()
        val panes = ArrayList<String>()

        for (instance in _state.value.indicators) {
            when (instance.kind) {
                "EMA" -> overlays += line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, Indicators.ema(closes(candles), instance.period)))
                "SMA" -> overlays += line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, Indicators.sma(closes(candles), instance.period)))
                "BB" -> {
                    val bb = Indicators.bollinger(candles, instance.period)
                    listOf(bb.upper, bb.middle, bb.lower).forEach { band ->
                        overlays += line("""{"kind":"line","color":"${instance.color}","width":1,"data":""", lineData(candles, band))
                    }
                }
                "VWAP" -> overlays += line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, Indicators.vwap(candles)))
                "RSI" -> panes += """{"series":[${line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, Indicators.rsi(candles, instance.period)))}}],"fixedRange":{"min":0,"max":100}}"""
                "MACD" -> {
                    val (macd, signal, hist) = Indicators.macd(closes(candles))
                    val series = buildString {
                        append("""{"kind":"histogram","color":"#26A69A","width":1,"data":""").append(histData(candles, hist)).append('}')
                        append(',').append(line("""{"kind":"line","color":"#2962FF","width":2,"data":""", lineData(candles, macd)))
                        append(',').append(line("""{"kind":"line","color":"#FF9800","width":2,"data":""", lineData(candles, signal)))
                    }
                    panes += """{"series":[$series]}"""
                }
                "ATR" -> panes += """{"series":[${line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, Indicators.atr(candles, instance.period)))}}]}"""
                "SCRIPT" -> {
                    val script = instance.script ?: continue
                    PineLite.compile(script, candles).fold(
                        onSuccess = {
                            panes += """{"series":[${line("""{"kind":"line","color":"${instance.color}","width":2,"data":""", lineData(candles, it.values))}}]}"""
                        },
                        onFailure = { },
                    )
                }
            }
        }
        return """{"overlays":[${overlays.joinToString(",")}],"panes":[${panes.joinToString(",")}]}"""
    }

    private fun line(prefix: String, dataJson: String): String = prefix + dataJson + "}"

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
        prefs["instrument"] = s.instrument.id
        prefs["tf"] = s.timeframe.name
        prefs["tool"] = s.tool.name
        prefs["indicators"] = json.encodeToString(ListSerializer(IndicatorInstance.serializer()), s.indicators)
        prefs["scripts"] = json.encodeToString(ListSerializer(SavedScript.serializer()), s.scripts)
        prefs["drawings"] = json.encodeToString(ListSerializer(Drawing.serializer()), s.drawings)
        Store.write(prefs)
    }

    private fun restore() {
        val map = Store.read()
        val prefsMap = map ?: emptyMap()
        prefs.putAll(prefsMap)
        val instrument = prefsMap["instrument"]?.let { Instruments.find(it) } ?: Instruments.default()
        val tf = prefsMap["tf"]?.let { runCatching { Timeframe.valueOf(it) }.getOrNull() } ?: Timeframe.M1
        val indicators = decodeList(prefsMap["indicators"], IndicatorInstance.serializer())
        val scripts = decodeList(prefsMap["scripts"], SavedScript.serializer())
        val drawings = decodeList(prefsMap["drawings"], Drawing.serializer())
        drawModel.replace(drawings)
        full = SampleSeries.generate(instrument, tf, 400) // placeholder until REST answers
        _state.update {
            it.copy(
                instrument = instrument,
                timeframe = tf,
                candles = full,
                lastCandle = full.lastOrNull(),
                indicators = indicators,
                scripts = scripts,
                drawings = drawings,
            )
        }
        dataVersion.value++
    }

    private inline fun <reified T> decodeList(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        raw?.let { runCatching { json.decodeFromString(ListSerializer(serializer), it) }.getOrDefault(emptyList()) } ?: emptyList()

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

    private companion object {
        const val PreviewScriptId = "SCRIPT-preview"
    }
}
