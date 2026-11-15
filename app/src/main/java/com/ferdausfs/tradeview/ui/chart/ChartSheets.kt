package com.ferdausfs.tradeview.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdausfs.tradeview.ui.theme.TvAccent
import com.ferdausfs.tradeview.ui.theme.TvBackground
import com.ferdausfs.tradeview.ui.theme.TvDown
import com.ferdausfs.tradeview.ui.theme.TvGrid
import com.ferdausfs.tradeview.ui.theme.TvPanel
import com.ferdausfs.tradeview.ui.theme.TvText
import com.ferdausfs.tradeview.ui.theme.TvUp

/** Full-screen bottom sheet scaffold (spec §4: mobile pickers are sheets, never dropdowns). */
@Composable
fun SheetScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .background(TvPanel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TvText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = TvText,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onClose)
                    .padding(6.dp),
            )
        }
        content()
    }
}

@Composable
fun SymbolSheet(
    symbols: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = symbols.filter { it.contains(query.trim(), ignoreCase = true) }
    SheetScaffold("Symbols", onClose) {
        SearchField(query, { query = it }, "Search symbol")
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(filtered) { symbol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(symbol) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(symbol, color = TvText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (symbol == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = TvAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun TimeframeSheet(selected: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    SheetScaffold("Timeframe", onClose) {
        com.ferdausfs.tradeview.domain.model.Timeframe.entries.forEach { tf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(tf.label) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tf.label, color = TvText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (tf.label == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = TvAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Indicator registry + active list, TradingView-style. */
private val INDICATOR_CATALOG = listOf(
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
    onToggle: (IndicatorInstance) -> Unit,
    onEditParams: (IndicatorInstance) -> Unit,
    onOpenScriptEditor: () -> Unit,
    onAddScript: (SavedScript) -> Unit,
    onDeleteScript: (SavedScript) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    SheetScaffold("Indicators & Scripts", onClose) {
        SearchField(query, { query = it }, "Search indicators")
        LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
            if (instances.isNotEmpty()) {
                item { SectionLabel("On chart") }
                items(instances, key = { it.id }) { inst ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(Color(android.graphics.Color.parseColor(inst.color)), RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(inst.title, color = TvText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (inst.kind == "SCRIPT") "script" else "settings",
                            color = TvAccent,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { onEditParams(inst) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = TvText.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { onRemove(inst.id) }
                                .padding(7.dp),
                        )
                    }
                }
            }

            item { SectionLabel("Built-in") }
            val catalog = INDICATOR_CATALOG.filter {
                query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true)
            }
            items(catalog) { (kind, label, pane) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdd(kind) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = TvText, fontSize = 13.sp)
                        Text(pane, color = TvText.copy(alpha = 0.45f), fontSize = 10.sp)
                    }
                    Icon(Icons.Filled.Add, contentDescription = null, tint = TvAccent, modifier = Modifier.size(18.dp))
                }
            }

            item { SectionLabel("Saved scripts") }
            if (scripts.isEmpty()) {
                item {
                    Text(
                        "No scripts yet — open the editor to write one.",
                        color = TvText.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            items(scripts, key = { it.code }) { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddScript(saved) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = TvAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(saved.name, color = TvText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = TvDown,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onDeleteScript(saved) }
                            .padding(6.dp),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenScriptEditor)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = TvUp, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New indicator script (Pine-lite)", color = TvUp, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
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
    SheetScaffold("Indicator script", onClose) {
        Text(
            "Subset: plot(expr) · close/open/high/low/volume · + - * / · sma(x,n), ema(x,n), rsi(n), atr(n), vwap()",
            color = TvText.copy(alpha = 0.55f),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(TvBackground, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TvText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 200.dp),
            )
        }
        if (error != null) {
            Text(
                error,
                color = TvDown,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(TvGrid, RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreview)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Preview on chart", color = TvText, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(TvAccent, RoundedCornerShape(8.dp))
                    .clickable(onClick = onSave)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save & add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun IndicatorSettingsSheet(
    instance: IndicatorInstance,
    onApply: (IndicatorInstance) -> Unit,
    onClose: () -> Unit,
) {
    var periodText by remember(instance.id) { mutableStateOf(instance.period.toString()) }
    var colorText by remember(instance.id) { mutableStateOf(instance.color) }
    SheetScaffold("${instance.title} settings", onClose) {
        if (instance.kind != "MACD" && instance.kind != "VWAP" && instance.kind != "SCRIPT") {
            Text("Length", color = TvText.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(TvGrid, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = periodText,
                    onValueChange = { t -> periodText = t.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TvText, fontSize = 14.sp),
                )
            }
        }
        Text("Color", color = TvText.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
        Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("#2962FF", "#FF9800", "#AB47BC", "#26C6DA", "#EC407A", "#8BC34A", "#F0B90B").forEach { c ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(android.graphics.Color.parseColor(c)), RoundedCornerShape(50))
                        .clickable { colorText = c }
                        .then(
                            if (colorText.equals(c, true)) Modifier.padding(2.dp) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (colorText.equals(c, true)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(TvAccent, RoundedCornerShape(8.dp))
                .clickable {
                    val period = periodText.toIntOrNull() ?: instance.period
                    onApply(instance.copy(period = period.coerceIn(1, 300), color = colorText))
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Apply", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(6.dp))
    }
}