package com.ferdausfs.tradeview.data

import android.content.Context
import com.ferdausfs.tradeview.ui.chart.AppContextHolder

/**
 * App-level settings kept in SharedPreferences: watchlist ids, live-feed toggle and the optional
 * TwelveData key for forex. Chart-local state (indicators/scripts) stays in [Store].
 */
object Settings {
    private const val FILE = "tradeview_settings"

    private fun sp() = AppContextHolder.get().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var liveEnabled: Boolean
        get() = sp().getBoolean("live", true)
        set(value) = sp().edit().putBoolean("live", value).apply()

    // ---- data-provider API keys (optional; more keys = more failover depth) ----

    private fun key(name: String): String = sp().getString("key_$name", "").orEmpty()

    private fun setKey(name: String, value: String) =
        sp().edit().putString("key_$name", value.trim()).apply()

    /** Legacy single-field accessor kept for the TwelveData slot (reads the old pref name too). */
    fun twelveDataKey(): String = key("twelvedata").ifEmpty {
        sp().getString("td_key", "").orEmpty()
    }

    fun setTwelveDataKey(key: String) = setKey("twelvedata", key)

    fun traderMadeKey(): String = key("tradermade")

    fun setTraderMadeKey(key: String) = setKey("tradermade", key)

    fun alphaVantageKey(): String = key("alphavantage")

    fun setAlphaVantageKey(key: String) = setKey("alphavantage", key)

    /** All configured keys, provider id -> key, used to build the failover chain. */
    fun configuredKeys(): Map<String, String> = buildMap {
        twelveDataKey().takeIf { it.isNotBlank() }?.let { put("twelvedata", it) }
        traderMadeKey().takeIf { it.isNotBlank() }?.let { put("tradermade", it) }
        alphaVantageKey().takeIf { it.isNotBlank() }?.let { put("alphavantage", it) }
    }

    fun watchlist(): List<String> = sp().getString("watchlist", null)
        ?.split(',')?.filter { it.isNotBlank() } ?: defaultWatchlist

    fun setWatchlist(ids: List<String>) =
        sp().edit().putString("watchlist", ids.joinToString(",")).apply()

    fun isFavorite(id: String): Boolean = watchlist().contains(id)

    fun toggleFavorite(id: String): Boolean {
        val current = watchlist().toMutableList()
        val added = if (current.contains(id)) { current.remove(id); false } else { current.add(id); true }
        setWatchlist(current)
        return added
    }

    private val defaultWatchlist = listOf("crypto:BTCUSDT", "crypto:ETHUSDT", "crypto:SOLUSDT")
}
