package com.ferdausfs.tradeview.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import com.ferdausfs.tradeview.domain.model.priceText
import com.ferdausfs.tradeview.ui.theme.TvAccent
import com.ferdausfs.tradeview.ui.theme.TvDown
import com.ferdausfs.tradeview.ui.theme.TvGrid
import com.ferdausfs.tradeview.ui.theme.TvPanel
import com.ferdausfs.tradeview.ui.theme.TvText
import com.ferdausfs.tradeview.ui.theme.TvUp

/** Top bar: symbol · timeframe · replay · indicators · settings (spec §4). */
@Composable
fun ChartTopBar(
    symbol: String,
    timeframe: Timeframe,
    last: Candle?,
    replayActive: Boolean,
    onSymbolClick: () -> Unit,
    onTimeframeClick: () -> Unit,
    onReplayClick: () -> Unit,
    onIndicatorsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvPanel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSymbolClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(symbol, color = TvText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change symbol", tint = TvText, modifier = Modifier.size(20.dp))
            }

            TopChip("${timeframe.label} \u25BE", onTimeframeClick)
            Spacer(Modifier.width(6.dp))
            TopIcon(Icons.Filled.PlayArrow, "Replay", replayActive) { onReplayClick() }
            TopIcon(Icons.Filled.Functions, "Indicators") { onIndicatorsClick() }
            TopIcon(Icons.Filled.Settings, "Settings") { onSettingsClick() }
        }

        PriceLegend(last)
    }
}

@Composable
private fun TopChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(TvGrid, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = TvText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (active) TvAccent else TvText,
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick)
            .padding(7.dp),
    )
}

/** OHLC legend overlay (top-left of the chart, spec §4). */
@Composable
fun PriceLegend(candle: Candle?) {
    if (candle == null) return
    val up = candle.close >= candle.open
    val color = if (up) TvUp else TvDown
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvPanel.copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendItem("O", candle.open, color)
        LegendItem("H", candle.high, color)
        LegendItem("L", candle.low, color)
        LegendItem("C", candle.close, color)
    }
}

@Composable
private fun LegendItem(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TvText.copy(alpha = 0.6f), fontSize = 10.sp)
        Spacer(Modifier.width(3.dp))
        Text(priceText(value), color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Timeframe row shown under the chart (quick switch). */
@Composable
fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvPanel)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TT", color = TvText.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Timeframe.entries.forEach { tf ->
            val active = tf == selected
            Box(
                modifier = Modifier
                    .background(if (active) TvAccent else TvGrid, RoundedCornerShape(5.dp))
                    .clickable { onSelect(tf) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    tf.label,
                    color = if (active) androidx.compose.ui.graphics.Color.White else TvText,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Bottom toolbar: chart type, indicators, replay, fullscreen-ish actions. */
@Composable
fun ChartBottomBar(
    logScale: Boolean,
    onLogScale: () -> Unit,
    onIndicators: () -> Unit,
    onReplay: () -> Unit,
    onFit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvPanel)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomAction(Icons.Filled.CandlestickChart, "Chart")
        BottomAction(Icons.Filled.Functions, "Indicators", onClick = onIndicators)
        BottomAction(Icons.Filled.PlayArrow, "Replay", onClick = onReplay)
        BottomText(if (logScale) "LOG" else "LIN", onClick = onLogScale)
        BottomText("FIT", onClick = onFit)
        BottomText("\u22EF", onClick = onFit)
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = TvText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = TvText, fontSize = 12.sp)
    }
}

@Composable
private fun BottomText(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, color = TvText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Replay control bar (spec §5): play/pause, step, speed, exit. */
@Composable
fun ReplayBar(
    playing: Boolean,
    index: Int,
    total: Int,
    speed: Double,
    onPlayPause: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onSpeed: (Double) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvPanel)
            .border(1.dp, TvAccent.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReplayButton(if (playing) "\u23F8" else "\u25B6", TvAccent, onPlayPause)
            ReplayButton("\u23EE", TvText, onStepBack)
            ReplayButton("\u23ED", TvText, onStepForward)
            Spacer(Modifier.width(8.dp))
            listOf(1.0, 2.0, 5.0, 10.0).forEach { s ->
                val active = s == speed
                Box(
                    modifier = Modifier
                        .background(if (active) TvAccent else TvGrid, RoundedCornerShape(4.dp))
                        .clickable { onSpeed(s) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text("${s.toInt()}x", color = if (active) androidx.compose.ui.graphics.Color.White else TvText, fontSize = 11.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clickable(onClick = onExit)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text("\u2715 Exit", color = TvDown, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Replay  ${index.coerceAtMost(total)} / $total bars",
            color = TvText.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ReplayButton(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(TvGrid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = tint, fontSize = 13.sp)
    }
}

/** Small empty-state / loading block reused by sheets. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = TvText.copy(alpha = 0.5f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(TvGrid, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = TvText.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = TvText, fontSize = 14.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = TvText.copy(alpha = 0.45f), fontSize = 14.sp)
                inner()
            },
        )
    }
}