package com.ferdausfs.tradeview.domain.model

/**
 * Timeframes offered by the chart. `seconds` drives bucketing, axis formatting and
 * (later) candle aggregation from lower timeframes.
 */
enum class Timeframe(val label: String, val seconds: Long) {
    M1("1m", 60L),
    M5("5m", 300L),
    M15("15m", 900L),
    H1("1h", 3_600L),
    H4("4h", 14_400L),
    D1("1D", 86_400L);

    val shortLabel: String get() = label
}
