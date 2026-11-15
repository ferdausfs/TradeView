package com.ferdausfs.tradeview.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdausfs.tradeview.ui.theme.LocalTokens

/** Press feedback without the heavy Material ripple — closer to a native trading app. */
@Composable
fun tvPress(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    return source to scale
}

/** Segmented control used for timeframes, tools and speeds. */
@Composable
fun TvSegmented(
    options: List<String>,
    selectedIndex: Int,
    compact: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .background(t.panelElevated, RoundedCornerShape(if (compact) 7.dp else 9.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(if (compact) 7.dp else 9.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .alpha(if (pressed) 0.75f else 1f)
                    .background(if (active) t.accent else Color.Transparent, RoundedCornerShape(if (compact) 5.dp else 7.dp))
                    .clickable(interactionSource = source, indication = null) { onSelect(i) }
                    .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 5.dp else 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else t.textMuted,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Icon button in the toolbars: subtle filled square when active, like TradingView's rail. */
@Composable
fun RowScope.TvToolButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    tint: Color = if (active) LocalTokens.current.accent else LocalTokens.current.text,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val (source, _) = tvPress()
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(if (active) t.accentSoft else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun TvTextButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    val t = LocalTokens.current
    val (source, _) = tvPress()
    Text(
        label,
        color = if (active) t.accent else t.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
fun TvPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalTokens.current
    val (source, scale) = tvPress()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .scale(scale)
            .background(if (enabled) t.accent else t.stroke, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TvSecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    val (source, scale) = tvPress()
    Box(
        modifier = modifier
            .scale(scale)
            .background(t.panelElevated, RoundedCornerShape(10.dp))
            .border(1.dp, t.stroke, RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Scrim(onClick: () -> Unit, content: @Composable () -> Unit) {    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}

@Composable
fun SpacerHeight(dp: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(dp))
