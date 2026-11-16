package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Feed
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Market
import com.ferdausfs.tradeview.domain.model.Timeframe
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * REST history provider. Binance for crypto, TwelveData for forex when a key exists, and the
 * deterministic demo generator as the final fallback so the chart is never empty.
 */
class HistoryRepository(
    private val client: OkHttpClient = Http.shared,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws IOException when the network fails; callers decide whether to fall back to demo. */
    suspend fun fetch(instrument: Instrument, timeframe: Timeframe, limit: Int = 720): List<Candle> =
        withContext(Dispatchers.IO) {
            when (effectiveFeed(instrument)) {
                Feed.BINANCE -> fetchBinance(instrument.symbol, timeframe, limit)
                Feed.YAHOO -> yahoo.candles(instrument.symbol, timeframe, limit)
                Feed.TWELVEDATA -> fetchTwelveData(instrument.symbol, timeframe, limit)
                Feed.DEMO -> throw IOException("no live source configured")
            }
        }

    /**
     * Crypto is always Binance. Forex prefers Yahoo (free intraday, no key); the TwelveData key
     * is still honoured when set (some traders want it), otherwise Yahoo, then ECB daily, then demo.
     */
    fun effectiveFeed(instrument: Instrument): Feed = when {
        instrument.feed == Feed.BINANCE -> Feed.BINANCE
        instrument.feed == Feed.TWELVEDATA && Settings.twelveDataKey().isNotEmpty() -> Feed.TWELVEDATA
        instrument.market == Market.FOREX -> Feed.YAHOO
        else -> Feed.DEMO
    }

    private val frankfurter by lazy { FrankfurterSource(client) }
    private val yahoo by lazy { YahooSource(client) }

    /**
     * History with graceful degradation: live source -> cache -> demo. Never throws, so the chart
     * always has something to draw (important on a flaky mobile connection).
     */
    suspend fun load(
        instrument: Instrument,
        timeframe: Timeframe,
        limit: Int = 720,
        allowLive: Boolean = true,
    ): Source {
        // 1. live
        if (allowLive && effectiveFeed(instrument) != Feed.DEMO) {
            runCatching { fetch(instrument, timeframe, limit) }.getOrNull()?.let { candles ->
                if (candles.isNotEmpty()) return Source(candles, kind = liveKind(instrument))
            }
            // TwelveData paid key down? Yahoo is still free — try it before giving up.
            if (effectiveFeed(instrument) == Feed.TWELVEDATA) {
                runCatching { yahoo.candles(instrument.symbol, timeframe, limit) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return Source(it, kind = "Yahoo") }
            }
        }
        // 2. forex daily: free ECB reference rates (open/high/low are synthetic but real closes)
        if (allowLive && instrument.market == Market.FOREX && timeframe == Timeframe.D1) {
            runCatching { frankfurter.dailyCandles(instrument.symbol, limit) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return Source(it, kind = "ECB daily") }
        }
        // 3. demo
        return Source(demo(instrument, timeframe, limit), kind = "Demo data")
    }

    private fun liveKind(instrument: Instrument): String =
        when (effectiveFeed(instrument)) {
            Feed.BINANCE -> "Binance"
            Feed.YAHOO -> "Yahoo"
            Feed.TWELVEDATA -> "TwelveData"
            else -> "Demo"
        }

    private fun demo(instrument: Instrument, timeframe: Timeframe, count: Int): List<Candle> =
        com.ferdausfs.tradeview.domain.SampleSeries.generate(instrument, timeframe, count)

    data class Source(val candles: List<Candle>, val kind: String)

    // ---- Binance -------------------------------------------------------

    private fun fetchBinance(symbol: String, tf: Timeframe, limit: Int): List<Candle> {
        val url = "https://api.binance.com/api/v3/klines" +
            "?symbol=${symbol.uppercase()}&interval=${tf.binanceInterval}&limit=$limit"
        val body = get(url)
        val rows = json.parseToJsonElement(body).jsonArray
        return rows.mapNotNull { row ->
            val a = row.jsonArray
            if (a.size < 6) return@mapNotNull null
            Candle(
                time = a[0].jsonPrimitive.content.toLong() / 1000L,
                open = a[1].jsonPrimitive.content.toDouble(),
                high = a[2].jsonPrimitive.content.toDouble(),
                low = a[3].jsonPrimitive.content.toDouble(),
                close = a[4].jsonPrimitive.content.toDouble(),
                volume = a[5].jsonPrimitive.content.toDouble(),
            )
        }.distinctBy { it.time }.sortedBy { it.time }
    }

    // ---- TwelveData ----------------------------------------------------

    private fun fetchTwelveData(symbol: String, tf: Timeframe, limit: Int): List<Candle> {
        val interval = when (tf) {
            Timeframe.M1 -> "1min"
            Timeframe.M5 -> "5min"
            Timeframe.M15 -> "15min"
            Timeframe.H1 -> "1hour"
            Timeframe.H4 -> "1hour"   // aggregated below
            Timeframe.D1 -> "1day"
        }
        val outputSize = if (tf == Timeframe.H4) (limit * 4) else limit
        val url = "https://api.twelvedata.com/time_series" +
            "?symbol=${java.net.URLEncoder.encode(symbol, "UTF-8")}" +
            "&interval=$interval&outputsize=$outputSize&apikey=${Settings.twelveDataKey()}"
        val body = get(url)
        val root = json.parseToJsonElement(body).jsonObject
        root["status"]?.jsonPrimitive?.content?.let { if (it == "error") throw IOException(root["message"]?.jsonPrimitive?.content ?: "TwelveData error") }
        val values = root["values"]?.jsonArray ?: throw IOException("TwelveData: no values")
        val candles = values.mapNotNull { v ->
            val o = v.jsonObject
            fun f(key: String) = o[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val ts = o["datetime"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val epoch = parseIsoSeconds(ts) ?: return@mapNotNull null
            Candle(epoch, f("open"), f("high"), f("low"), f("close"), f("volume"))
        }.sortedBy { it.time }
        return if (tf == Timeframe.H4) {
            com.ferdausfs.tradeview.domain.Aggregator.aggregate(candles, Timeframe.H4)
        } else {
            candles.distinctBy { it.time }
        }
    }

    private fun parseIsoSeconds(text: String): Long? = runCatching {
        java.time.LocalDateTime.parse(text.replace(' ', 'T'))
            .toEpochSecond(java.time.ZoneOffset.UTC)
    }.getOrNull()

    // ---- http ----------------------------------------------------------

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "TradeView/0.2 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("empty body")
        }
    }
}
