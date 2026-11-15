package com.ferdausfs.tradeview.ui.chart

import kotlinx.serialization.Serializable

/**
 * One indicator instance on the chart, persisted as JSON in [Store].
 */
@Serializable
data class IndicatorInstance(
    val id: String,
    val kind: String,          // EMA | SMA | BB | VWAP | RSI | MACD | ATR | SCRIPT
    val period: Int = 14,
    val color: String,
    val script: String? = null,
    val scriptLabel: String? = null,
) {
    val isOverlay: Boolean
        get() = kind == "EMA" || kind == "SMA" || kind == "BB" || kind == "VWAP"

    val title: String
        get() = when (kind) {
            "EMA" -> "EMA $period"
            "SMA" -> "SMA $period"
            "BB" -> "BB $period"
            "VWAP" -> "VWAP"
            "RSI" -> "RSI $period"
            "MACD" -> "MACD"
            "ATR" -> "ATR $period"
            "SCRIPT" -> scriptLabel ?: "Script"
            else -> kind
        }
}

@Serializable
data class SavedScript(val name: String, val code: String)
