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

    fun twelveDataKey(): String = sp().getString("td_key", "").orEmpty()

    fun setTwelveDataKey(key: String) = sp().edit().putString("td_key", key.trim()).apply()

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
