package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Feed
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Timeframe
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live candle updates. Binance kline streams for crypto; forex has no free stream here, so the
 * caller keeps polling REST (see ChartViewModel). The flow completes only when cancelled.
 */
class BinanceKlineStream(
    private val client: okhttp3.OkHttpClient = Http.shared,
) {

    /** Emits the in-progress candle repeatedly and a finalised candle when the bucket closes. */
    fun candles(instrument: Instrument, timeframe: Timeframe): Flow<KlineUpdate> = callbackFlow {
        val stream = instrument.symbol.lowercase() + "@kline_" + timeframe.binanceInterval
        val request = Request.Builder().url("wss://stream.binance.com:9443/ws/$stream").build()

        var socket: WebSocket? = null
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val update = parse(text) ?: return
                trySend(update)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })

        awaitClose { socket?.close(1000, "screen gone") }
    }

    private fun parse(raw: String): KlineUpdate? = runCatching {
        val k = JSONObject(raw).getJSONObject("k")
        Candle(
            time = k.getLong("t") / 1000L,
            open = k.getString("o").toDouble(),
            high = k.getString("h").toDouble(),
            low = k.getString("l").toDouble(),
            close = k.getString("c").toDouble(),
            volume = k.getString("v").toDouble(),
        ).let { KlineUpdate(it, closed = k.getBoolean("x")) }
    }.getOrNull()
}

data class KlineUpdate(val candle: Candle, val closed: Boolean)

/** Best bid/ask + last price for the quote header (crypto only today). */
class TickerStream(private val client: okhttp3.OkHttpClient = Http.shared) {

    data class Quote(val last: Double, val changePct: Double, val high24h: Double, val low24h: Double, val volume: Double)

    fun quotes(instruments: List<Instrument>): Flow<Pair<String, Quote>> = callbackFlow {
        val symbols = instruments.filter { it.feed == Feed.BINANCE }.map { it.symbol.lowercase() }
        if (symbols.isEmpty()) { awaitClose { }; return@callbackFlow }
        val combined = symbols.joinToString("/") { "${it}@ticker" }
        val request = Request.Builder().url("wss://stream.binance.com:9443/stream?streams=$combined").build()

        var socket: WebSocket? = null
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val payload = runCatching { JSONObject(text).getJSONObject("data") }.getOrNull() ?: return
                val sym = payload.optString("s").uppercase()
                val id = "crypto:$sym"
                val quote = Quote(
                    last = payload.optDouble("c", 0.0),
                    changePct = payload.optDouble("P", 0.0),
                    high24h = payload.optDouble("h", 0.0),
                    low24h = payload.optDouble("l", 0.0),
                    volume = payload.optDouble("v", 0.0),
                )
                trySend(id to quote)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit
        })
        awaitClose { socket?.close(1000, "gone") }
    }

    /** One-shot snapshot used by the browser list. */
    suspend fun snapshot(instruments: List<Instrument>): Map<String, Quote> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val symbols = instruments.filter { it.feed == Feed.BINANCE }.map { "\"${it.symbol.uppercase()}\"" }
            if (symbols.isEmpty()) return@withContext emptyMap()
            val url = "https://api.binance.com/api/v3/ticker/24hr?symbols=[${symbols.joinToString(",")}]"
            val body = runCatching {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull() ?: return@withContext emptyMap()
            val arr = JSONArray(body)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sym = o.getString("symbol").uppercase()
                    put(
                        "crypto:$sym",
                        Quote(
                            last = o.optDouble("lastPrice", 0.0),
                            changePct = o.optDouble("priceChangePercent", 0.0),
                            high24h = o.optDouble("highPrice", 0.0),
                            low24h = o.optDouble("lowPrice", 0.0),
                            volume = o.optDouble("volume", 0.0),
                        ),
                    )
                }
            }
        }
}
