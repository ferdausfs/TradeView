package com.ferdausfs.tradeview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TradingView-derived tokens. The chart canvas is painted by lightweight-charts, so these values
 * are kept in sync with the THEMES object in assets/chart/chart.js.
 */
@Immutable
data class TvTokens(
    val background: Color,
    val panel: Color,
    val panelElevated: Color,
    val stroke: Color,
    val text: Color,
    val textMuted: Color,
    val up: Color,
    val down: Color,
    val accent: Color,
    val accentSoft: Color,
    val amber: Color,
)

val DarkTokens = TvTokens(
    background = Color(0xFF131722),
    panel = Color(0xFF1E222D),
    panelElevated = Color(0xFF262B38),
    stroke = Color(0xFF2A2E39),
    text = Color(0xFFD1D4DC),
    textMuted = Color(0xFF787B87),
    up = Color(0xFF26A69A),
    down = Color(0xFFEF5350),
    accent = Color(0xFF2962FF),
    accentSoft = Color(0x332962FF),
    amber = Color(0xFFF0B90B),
)

val LightTokens = TvTokens(
    background = Color(0xFFFFFFFF),
    panel = Color(0xFFF8F9FD),
    panelElevated = Color(0xFFFFFFFF),
    stroke = Color(0xFFE0E3EB),
    text = Color(0xFF131722),
    textMuted = Color(0xFF6A6D78),
    up = Color(0xFF26A69A),
    down = Color(0xFFEF5350),
    accent = Color(0xFF2962FF),
    accentSoft = Color(0x1A2962FF),
    amber = Color(0xFFF0B90B),
)

val LocalTokens = staticCompositionLocalOf { DarkTokens }

// Backwards-compatible aliases used by chart widgets.
val TvBackground get() = DarkTokens.background
val TvPanel get() = DarkTokens.panel
val TvGrid get() = DarkTokens.stroke
val TvText get() = DarkTokens.text
val TvUp get() = DarkTokens.up
val TvDown get() = DarkTokens.down
val TvAccent get() = DarkTokens.accent

private val TvTypography = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium),
)

private val TvShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun TradeViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            secondary = tokens.up,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.panel,
            onSurface = tokens.text,
            surfaceVariant = tokens.panelElevated,
            onSurfaceVariant = tokens.textMuted,
            outline = tokens.stroke,
            error = tokens.down,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            secondary = tokens.up,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.panel,
            onSurface = tokens.text,
            surfaceVariant = tokens.panelElevated,
            onSurfaceVariant = tokens.textMuted,
            outline = tokens.stroke,
            error = tokens.down,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = TvTypography, shapes = TvShapes, content = content)
    }
}

/** Numeric data should not jitter: tabular figures everywhere prices are shown. */
val MonoNumber = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
