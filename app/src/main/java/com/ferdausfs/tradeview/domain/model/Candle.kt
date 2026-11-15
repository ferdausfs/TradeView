package com.ferdausfs.tradeview.domain.model

import java.util.Locale

/**
 * Canonical candle format (spec §2). `time` is UTC seconds.
 */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

private fun Double.fmt(): String = String.format(Locale.US, "%.4f", this)

fun Candle.toJson(): String =
    """{"time":$time,"open":${open.fmt()},"high":${high.fmt()},"low":${low.fmt()},"close":${close.fmt()},"volume":${volume.fmt()}}"""

/** Replay needs the cut point, so candles carry an index. */
data class IndexedCandle(val index: Int, val candle: Candle)

fun List<Candle>.toIndexed(): List<IndexedCandle> = mapIndexed { i, c -> IndexedCandle(i, c) }

fun List<Candle>.toJsonArray(): String = joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJson() }

fun priceText(value: Double): String = when {
    value >= 1000 -> String.format(Locale.US, "%,.2f", value)
    value >= 1 -> String.format(Locale.US, "%.2f", value)
    else -> String.format(Locale.US, "%.5f", value)
}

/** Precision-aware formatting: forex needs 5 decimals, BTC two. */
fun priceText(value: Double, decimals: Int): String =
    String.format(Locale.US, "%,.${decimals}f", value)

fun compactVolume(value: Double): String = when {
    value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
    value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
    else -> String.format(Locale.US, "%.0f", value)
}
