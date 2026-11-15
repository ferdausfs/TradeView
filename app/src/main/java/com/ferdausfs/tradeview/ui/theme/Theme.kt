package com.ferdausfs.tradeview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Chart-token palette from the TradeView spec (§4). The chart canvas itself is rendered
// by lightweight-charts, so these stay in sync with assets/chart/chart.js.
val TvBackground = Color(0xFF131722)
val TvPanel = Color(0xFF1E222D)
val TvGrid = Color(0xFF2A2E39)
val TvText = Color(0xFFD1D4DC)
val TvUp = Color(0xFF26A69A)
val TvDown = Color(0xFFEF5350)
val TvAccent = Color(0xFF2962FF)

private val DarkColors = darkColorScheme(
    primary = TvAccent,
    onPrimary = Color.White,
    secondary = TvUp,
    background = TvBackground,
    onBackground = TvText,
    surface = TvPanel,
    onSurface = TvText,
    surfaceVariant = TvGrid,
    onSurfaceVariant = TvText,
    error = TvDown,
)

private val LightColors = lightColorScheme(
    primary = TvAccent,
    secondary = TvUp,
    error = TvDown,
)

@Composable
fun TradeViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dark is the default product theme; light mode is opt-in for system-light users.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
