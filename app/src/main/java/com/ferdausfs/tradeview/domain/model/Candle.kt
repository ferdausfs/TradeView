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

fun List<Candle>.toJsonArray(): String = joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJson() }

fun priceText(value: Double): String = when {
    value >= 1000 -> String.format(Locale.US, "%,.2f", value)
    value >= 1 -> String.format(Locale.US, "%.2f", value)
    else -> String.format(Locale.US, "%.5f", value)
}
