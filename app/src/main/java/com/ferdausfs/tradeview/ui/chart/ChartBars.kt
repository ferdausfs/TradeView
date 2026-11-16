package com.ferdausfs.tradeview.ui.chart

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineStyle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdausfs.tradeview.data.TickerStream
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.DrawKind
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Market
import com.ferdausfs.tradeview.domain.model.Timeframe
import com.ferdausfs.tradeview.domain.model.compactVolume
import com.ferdausfs.tradeview.domain.model.priceText
import com.ferdausfs.tradeview.ui.common.TvSegmented
import com.ferdausfs.tradeview.ui.common.TvToolButton
import com.ferdausfs.tradeview.ui.theme.LocalTokens

/**
 * Top chrome: instrument identity + live price on the left, tool rail on the right.
 * Everything is one tap away and nothing overlaps the canvas.
 */
@Composable
fun ChartTopBar(
    instrument: Instrument,
    timeframe: Timeframe,
    last: Candle?,
    quote: TickerStream.Quote?,
    sourceLabel: String,
    live: Boolean,
    loading: Boolean,
    replayActive: Boolean,
    tool: DrawKind,
    onSymbolClick: () -> Unit,
    onLiveToggle: () -> Unit,
    onIndicatorsClick: () -> Unit,
    onReplayClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val t = LocalTokens.current
    Column(modifier = Modifier.background(t.panel)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Identity block
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSymbolClick,
                    )
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TradingView-style round symbol avatar.
                val badgeColor = if (instrument.market == Market.CRYPTO) t.amber else t.accent
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(badgeColor.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, badgeColor.copy(alpha = 0.55f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSymbolClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        instrument.baseCode(),
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            instrument.symbol,
                            color = t.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change symbol", tint = t.textMuted, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        instrument.name,
                        color = t.textMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Price + change
            val shown = quote?.last?.takeIf { it > 0 } ?: last?.close
            if (shown != null) {
                val up = (quote?.changePct ?: run {
                    if (last != null && last.open != 0.0) (last.close - last.open) / last.open * 100 else 0.0
                }) >= 0
                Column {
                    Text(
                        priceText(shown, instrument.decimals),
                        color = if (up) t.up else t.down,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(if (live && !loading) 6.dp else 0.dp)
                                .background(if (up) t.up else t.down, CircleShape),
                        )
                        SourceChip(if (loading) "loading" else sourceLabel, live && !loading)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            TvToolButton(Icons.Filled.PlayArrow, "Bar replay", active = replayActive, onClick = onReplayClick)
            TvToolButton(Icons.Filled.Functions, "Indicators", active = false, onClick = onIndicatorsClick)
            TvToolButton(Icons.Filled.Settings, "Settings", active = false, onClick = onSettingsClick)
        }

        PriceLegend(last, instrument)
    }
}

@Composable
private fun SourceChip(label: String, streaming: Boolean) {
    val t = LocalTokens.current
    Spacer(Modifier.width(6.dp))
    Box(
        modifier = Modifier
            .background(if (streaming) t.accentSoft else t.stroke, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            label.uppercase(),
            color = if (streaming) t.accent else t.textMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

/** OHLC readout; follows the crosshair while the finger is down. */
@Composable
fun PriceLegend(candle: Candle?, instrument: Instrument) {
    if (candle == null) return
    val t = LocalTokens.current
    val up = candle.close >= candle.open
    val color = if (up) t.up else t.down
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("O", candle.open, color, instrument.decimals)
        LegendItem("H", candle.high, color, instrument.decimals)
        LegendItem("L", candle.low, color, instrument.decimals)
        LegendItem("C", candle.close, color, instrument.decimals)
        Spacer(Modifier.weight(1f))
        if (candle.volume > 0) {
            Text("V ${compactVolume(candle.volume)}", color = t.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LegendItem(label: String, value: Double, color: Color, decimals: Int) {
    val t = LocalTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(3.dp))
        Text(priceText(value, decimals), color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Quick timeframe switcher — segmented, TradingView style. */
@Composable
fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    val options = Timeframe.entries.map { it.label }
    TvSegmented(options, Timeframe.entries.indexOf(selected).coerceAtLeast(0), compact = true, onSelect = { onSelect(Timeframe.entries[it]) })
}

/** TradingView-style floating drawing rail — vertical, semi-transparent, hugging the chart's left edge. */
@Composable
fun DrawingRail(
    tool: DrawKind,
    hasSelection: Boolean,
    hasDrawings: Boolean,
    onTool: (DrawKind) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier = modifier
            .background(t.panel.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailIcon(Icons.AutoMirrored.Filled.TrendingUp, "Cursor", tool == DrawKind.Cursor) { onTool(DrawKind.Cursor) }
        RailIcon(Icons.Filled.LineStyle, "Trend line", tool == DrawKind.TrendLine) { onTool(DrawKind.TrendLine) }
        RailIcon(Icons.Filled.Add, "Horizontal line", tool == DrawKind.Horizontal) { onTool(DrawKind.Horizontal) }
        RailIcon(Icons.Filled.SquareFoot, "Box", tool == DrawKind.Rectangle) { onTool(DrawKind.Rectangle) }
        if (hasSelection) RailIcon(Icons.Filled.Delete, "Delete selected", danger = true, onClick = onDeleteSelected)
        if (hasDrawings) RailIcon(Icons.Filled.Close, "Remove all drawings", danger = true, onClick = onClearAll)
    }
}

@Composable
private fun RailIcon(icon: ImageVector, description: String, active: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val t = LocalTokens.current
    val tint = when {
        danger -> t.down
        active -> t.accent
        else -> t.text
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                if (active) t.accent.copy(alpha = 0.22f) else Color.Transparent,
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Bottom action strip: replay state, indicators count, fit. */
@Composable
fun ChartBottomBar(
    logScale: Boolean,
    indicatorCount: Int,
    onLogScale: () -> Unit,
    onIndicators: () -> Unit,
    onFit: () -> Unit,
    onBrowser: () -> Unit,
) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.panel)
            .border(1.dp, t.stroke)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomPill(Icons.Filled.Search, "Browse", onClick = onBrowser)
        Spacer(Modifier.width(6.dp))
        BottomPill(
            Icons.Filled.Layers,
            if (indicatorCount == 0) "Indicators" else "Indicators $indicatorCount",
            active = indicatorCount > 0,
            onClick = onIndicators,
        )
        Spacer(Modifier.weight(1f))
        BottomText(if (logScale) "LOG" else "LIN", active = logScale, onClick = onLogScale)
        Spacer(Modifier.width(4.dp))
        BottomText("FIT", onClick = onFit)
    }
}

@Composable
private fun BottomPill(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .background(if (active) t.accentSoft else Color.Transparent, RoundedCornerShape(7.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) t.accent else t.textMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (active) t.accent else t.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomText(label: String, active: Boolean = false, onClick: () -> Unit) {
    val t = LocalTokens.current
    Text(
        label,
        color = if (active) t.accent else t.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/** Replay transport (spec §5). Slides in so the chart never jumps. */
@Composable
fun ReplayBar(
    visible: Boolean,
    playing: Boolean,
    index: Int,
    total: Int,
    speed: Double,
    barTime: Long? = null,
    onPlayPause: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onSpeed: (Double) -> Unit,
    onExit: () -> Unit,
    onGoToDate: (String) -> Unit = {},
) {
    val t = LocalTokens.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.panel)
                .border(1.dp, t.accent.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransportKey("\u23EE", onClick = onStepBack)
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(t.accent, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPause,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (playing) "\u23F8" else "\u25B6", color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.width(5.dp))
                TransportKey("\u23ED", onClick = onStepForward)
                Spacer(Modifier.width(12.dp))
                val speeds = listOf(1.0, 2.0, 5.0, 10.0)
                TvSegmented(speeds.map { "${it.toInt()}x" }, speeds.indexOf(speed).coerceAtLeast(0), compact = true, onSelect = { onSpeed(speeds[it]) })
                Spacer(Modifier.weight(1f))
                Text(
                    "bar ${index.coerceAtMost(total)}/$total",
                    color = t.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "EXIT",
                    color = t.down,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExit,
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            // Date jump — TradingView-style: start replay from a specific day (YYYY-MM-DD).
            var dayText by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FROM",
                    color = t.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.width(7.dp))
                BasicTextField(
                    value = dayText,
                    onValueChange = { if (it.length <= 10) dayText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = t.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier
                        .weight(1f)
                        .background(t.panelElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, t.stroke, RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    decorationBox = { inner ->
                        if (dayText.isEmpty()) {
                            Text("YYYY-MM-DD", color = t.textMuted.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier
                        .background(t.accent, RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (dayText.isNotBlank()) onGoToDate(dayText) },
                        )
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text("GO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (barTime != null) {
                    Spacer(Modifier.width(9.dp))
                    Text(
                        java.time.Instant.ofEpochSecond(barTime)
                            .atZone(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        color = t.textMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportKey(label: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    Box(
        modifier = Modifier
            .background(t.panelElevated, RoundedCornerShape(6.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = t.text, fontSize = 12.sp)
    }
}

/** Toast used for tool hints and save confirmations. */
@Composable
fun TvToast(message: String?) {
    if (message == null) return
    val t = LocalTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .alpha(0.96f)
                .background(t.panelElevated, RoundedCornerShape(9.dp))
                .border(1.dp, t.stroke, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = t.up, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(message, color = t.text, fontSize = 12.sp)
        }
    }
}

/** Two-letter avatar code for the round symbol button ("BTCUSDT" -> "BT"). */
private fun Instrument.baseCode(): String = symbol.take(2).uppercase(Locale.US)
