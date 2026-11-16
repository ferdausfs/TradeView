package com.ferdausfs.tradeview.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdausfs.tradeview.data.Instruments
import com.ferdausfs.tradeview.data.TickerStream
import com.ferdausfs.tradeview.domain.model.DrawKind
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Market
import com.ferdausfs.tradeview.domain.model.priceText
import com.ferdausfs.tradeview.ui.common.TvPrimaryButton
import com.ferdausfs.tradeview.ui.common.TvSecondaryButton
import com.ferdausfs.tradeview.ui.theme.LocalTokens

/** Sheet scaffold: rounded top, grab handle, title row. */
@Composable
fun SheetScaffold(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val t = LocalTokens.current
    val rise = remember { Animatable(36f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, animationSpec = tween(220)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = rise.value.dp)
            .heightIn(max = 600.dp)
            .background(t.panel, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 34.dp, height = 4.dp)
                .background(t.stroke, RoundedCornerShape(50)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = t.textMuted, fontSize = 10.sp)
            }
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = t.textMuted,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                    .padding(7.dp),
            )
        }
        content()
    }
}

@Composable
fun TvSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .background(t.background, RoundedCornerShape(9.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = t.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = t.text, fontSize = 14.sp),
            modifier = Modifier.weight(1f),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(t.accent),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = t.textMuted, fontSize = 14.sp)
                inner()
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Instrument browser (spec P3): market tabs, watchlist star, live quotes.
// ---------------------------------------------------------------------------

@Composable
fun BrowserSheet(
    selectedId: String,
    watchlist: List<String>,
    quotes: Map<String, TickerStream.Quote>,
    onSelect: (Instrument) -> Unit,
    onToggleStar: (String) -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTokens.current
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) } // 0 = Watch, 1 = Crypto, 2 = Forex
    val tabs = listOf("Watchlist", "Crypto", "Forex")

    val source = when (tab) {
        0 -> Instruments.all.filter { watchlist.contains(it.id) }
        1 -> Instruments.crypto
        else -> Instruments.forex
    }
    val filtered = if (query.isBlank()) source else source.filter {
        it.symbol.contains(query, true) || it.name.contains(query, true)
    }

    SheetScaffold("Browse markets", "${Instruments.all.size} instruments · crypto + forex", onClose) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, label ->
                val active = i == tab
                Box(
                    modifier = Modifier
                        .background(if (active) t.accentSoft else Color.Transparent, RoundedCornerShape(7.dp))
                        .border(1.dp, if (active) t.accent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(7.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = i }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(label, color = if (active) t.accent else t.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        TvSearchField(query, { query = it }, "Search symbol or name")
        LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (tab == 0) "No starred instruments yet — tap the star on any row." else "Nothing matches \"$query\".",
                        color = t.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            items(filtered, key = { it.id }) { instrument ->
                InstrumentRow(
                    instrument = instrument,
                    selected = instrument.id == selectedId,
                    starred = watchlist.contains(instrument.id),
                    quote = quotes[instrument.id],
                    onClick = { onSelect(instrument) },
                    onStar = { onToggleStar(instrument.id) },
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun InstrumentRow(
    instrument: Instrument,
    selected: Boolean,
    starred: Boolean,
    quote: TickerStream.Quote?,
    onClick: () -> Unit,
    onStar: () -> Unit,
) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) t.accentSoft else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(if (instrument.market == Market.CRYPTO) t.amber.copy(alpha = 0.18f) else t.accent.copy(alpha = 0.18f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                instrument.symbol.take(1),
                color = if (instrument.market == Market.CRYPTO) t.amber else t.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(instrument.symbol, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(instrument.name, color = t.textMuted, fontSize = 10.sp, maxLines = 1)
        }
        if (quote != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(priceText(quote.last, instrument.decimals), color = t.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                val up = quote.changePct >= 0
                Text(
                    "%s%.2f%%".format(if (up) "+" else "", quote.changePct),
                    color = if (up) t.up else t.down,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (starred) "Unstar" else "Add to watchlist",
            tint = if (starred) t.amber else t.textMuted,
            modifier = Modifier
                .size(30.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onStar)
                .padding(5.dp),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = t.accent, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Indicators
// ---------------------------------------------------------------------------

private val IndicatorCatalog = listOf(
    Triple("EMA", "Moving Average Exponential", "Overlay"),
    Triple("SMA", "Moving Average Simple", "Overlay"),
    Triple("BB", "Bollinger Bands", "Overlay"),
    Triple("VWAP", "Volume Weighted Average Price", "Overlay"),
    Triple("RSI", "Relative Strength Index", "Pane"),
    Triple("MACD", "MACD", "Pane"),
    Triple("ATR", "Average True Range", "Pane"),
)

@Composable
fun IndicatorSheet(
    instances: List<IndicatorInstance>,
    scripts: List<SavedScript>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditParams: (IndicatorInstance) -> Unit,
    onOpenScriptEditor: () -> Unit,
    onAddScript: (SavedScript) -> Unit,
    onDeleteScript: (SavedScript) -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTokens.current
    var query by remember { mutableStateOf("") }
    SheetScaffold("Indicators & scripts", "${instances.size} on chart", onClose) {
        TvSearchField(query, { query = it }, "Search indicators")
        LazyColumn(modifier = Modifier.heightIn(max = 470.dp)) {
            if (instances.isNotEmpty()) {
                item { SectionLabel("On chart") }
                items(instances, key = { it.id }) { inst ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(Color(android.graphics.Color.parseColor(inst.color)), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(inst.title, color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            "settings",
                            color = t.accent,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditParams(inst) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = t.textMuted,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRemove(inst.id) }
                                .padding(6.dp),
                        )
                    }
                }
            }

            item { SectionLabel("Built-in") }
            val catalog = IndicatorCatalog.filter {
                query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true)
            }
            items(catalog) { (kind, label, pane) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAdd(kind) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = t.text, fontSize = 13.sp)
                        Text(pane, color = t.textMuted, fontSize = 10.sp)
                    }
                    Icon(Icons.Filled.Add, contentDescription = null, tint = t.accent, modifier = Modifier.size(17.dp))
                }
            }

            item { SectionLabel("Saved scripts") }
            if (scripts.isEmpty()) {
                item {
                    Text(
                        "No scripts yet — write one with the Pine-lite editor.",
                        color = t.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            items(scripts, key = { it.code }) { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAddScript(saved) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = t.amber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(saved.name, color = t.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = t.down,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDeleteScript(saved) }
                            .padding(6.dp),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenScriptEditor)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = t.up, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("New indicator script (Pine-lite)", color = t.up, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
fun ScriptEditorSheet(
    draft: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTokens.current
    SheetScaffold("Indicator script", "Pine-lite subset", onClose) {
        Text(
            "plot(expr) · close open high low volume · + - * / · sma(x,n) ema(x,n) rsi(n) atr(n) vwap()",
            color = t.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .background(t.background, RoundedCornerShape(10.dp))
                .border(1.dp, t.stroke, RoundedCornerShape(10.dp))
                .padding(11.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(t.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 190.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        if (error != null) {
            Text(error, color = t.down, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 3.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TvSecondaryButton("Preview on chart", Modifier.weight(1f), onClick = onPreview)
            TvPrimaryButton("Save & add", Modifier.weight(1f), onClick = onSave)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun IndicatorSettingsSheet(
    instance: IndicatorInstance,
    onApply: (IndicatorInstance) -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTokens.current
    var periodText by remember(instance.id) { mutableStateOf(instance.period.toString()) }
    var colorText by remember(instance.id) { mutableStateOf(instance.color) }
    SheetScaffold(instance.title, "Parameters", onClose) {
        if (instance.kind != "MACD" && instance.kind != "VWAP" && instance.kind != "SCRIPT") {
            Text("Length", color = t.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .background(t.background, RoundedCornerShape(8.dp))
                    .border(1.dp, t.stroke, RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = periodText,
                    onValueChange = { periodText = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    textStyle = TextStyle(color = t.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(t.accent),
                )
            }
        }
        Text("Colour", color = t.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp))
        Row(modifier = Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("#2962FF", "#FF9800", "#AB47BC", "#26C6DA", "#EC407A", "#8BC34A", "#F0B90B").forEach { c ->
                val picked = colorText.equals(c, true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(android.graphics.Color.parseColor(c)), CircleShape)
                        .border(if (picked) 2.dp else 0.dp, t.text, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { colorText = c },
                    contentAlignment = Alignment.Center,
                ) {
                    if (picked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TvPrimaryButton(
            "Apply",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        ) {
            val period = periodText.toIntOrNull() ?: instance.period
            onApply(instance.copy(period = period.coerceIn(1, 300), color = colorText))
        }
        Spacer(Modifier.height(14.dp))
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
fun SettingsSheet(
    live: Boolean,
    keys: Map<String, String>,
    drawingCount: Int,
    logScale: Boolean,
    onLive: (Boolean) -> Unit,
    onLogScale: (Boolean) -> Unit,
    onKeyChange: (provider: String, key: String) -> Unit,
    onApplyKeys: () -> Unit,
    onClearDrawings: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTokens.current
    SheetScaffold("Chart settings", "$drawingCount drawings on chart", onClose) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            ToggleRow("Live data", "Binance stream for crypto · poll for FX", live) { onLive(it) }
            ToggleRow("Logarithmic scale", "Price axis in log steps", logScale) { onLogScale(it) }
            SectionLabel("Data providers")
            Text(
                "Add any keys you have — they fail over in order, then Yahoo (free, no key), " +
                    "then ECB daily. Crypto always uses Binance.",
                color = t.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
            )
            KeyField(
                label = "TwelveData",
                hint = "free: 800 requests/day · intraday forex",
                value = keys["twelvedata"].orEmpty(),
                onChange = { onKeyChange("twelvedata", it) },
            )
            KeyField(
                label = "TraderMade",
                hint = "free tier · forex specialist, true intraday OHLC",
                value = keys["tradermade"].orEmpty(),
                onChange = { onKeyChange("tradermade", it) },
            )
            KeyField(
                label = "Alpha Vantage",
                hint = "free: 25 requests/day · extra backup",
                value = keys["alphavantage"].orEmpty(),
                onChange = { onKeyChange("alphavantage", it) },
            )
            TvPrimaryButton(
                "Apply keys & reload data",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                onClick = onApplyKeys,
            )
            Text(
                "After applying, the source chip above the chart shows which API answered " +
                    "(TwelveData / TraderMade / AlphaVantage / Yahoo).",
                color = t.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 2.dp),
            )
            if (drawingCount > 0) {
                TvSecondaryButton(
                    "Remove all drawings ($drawingCount)",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    onClick = onClearDrawings,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun KeyField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    val t = LocalTokens.current
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
        Text(
            label,
            color = t.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Text(hint, color = t.textMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.background, RoundedCornerShape(8.dp))
                .border(1.dp, t.stroke, RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(t.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("paste key…", color = t.textMuted, fontSize = 13.sp)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = t.textMuted, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .background(if (checked) t.accent else t.stroke, RoundedCornerShape(50))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text.uppercase(),
        color = t.textMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 4.dp),
    )
}
