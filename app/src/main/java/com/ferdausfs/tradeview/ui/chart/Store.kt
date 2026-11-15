package com.ferdausfs.tradeview.ui.chart

import android.content.Context

/**
 * Tiny key-value persistence for chart preferences. Room arrives in P3 when there are real
 * entities (watchlists); until then SharedPreferences is honest and sufficient.
 */
object Store {
    private const val FILE = "tradeview_prefs"

    fun read(): Map<String, String>? = runCatching {
        val sp = AppContextHolder.get().getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.all.entries.associate { it.key to it.value.toString() }.ifEmpty { null }
    }.getOrNull()

    fun write(map: Map<String, String>) {
        runCatching {
            AppContextHolder.get()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            val sp = AppContextHolder.get().getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val editor = sp.edit()
            map.forEach { (k, v) -> editor.putString(k, v) }
            editor.apply()
        }
    }
}
