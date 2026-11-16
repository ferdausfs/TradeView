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
                Feed.TRADERMADE -> traderMade.candles(instrument.symbol, Settings.traderMadeKey(), timeframe, limit)
                Feed.ALPHAVANTAGE -> alphaVantage.candles(instrument.symbol, Settings.alphaVantageKey(), timeframe, limit)
                Feed.DEMO -> throw IOException("no live source configured")
            }
        }

    /**
     * Crypto is always Binance. Forex walks the configured chain: TwelveData -> TraderMade ->
     * Alpha Vantage (keys, best first) -> Yahoo (no key) -> ECB daily -> demo. Used by the live
     * poll to know which source to hit; the full chain is re-tried inside load().
     */
    fun effectiveFeed(instrument: Instrument): Feed = when {
        instrument.feed == Feed.BINANCE -> Feed.BINANCE
        instrument.market != Market.FOREX -> Feed.DEMO
        else -> primaryForexFeed()
    }

    private fun primaryForexFeed(): Feed = when {
        Settings.twelveDataKey().isNotEmpty() -> Feed.TWELVEDATA
        Settings.traderMadeKey().isNotEmpty() -> Feed.TRADERMADE
        Settings.alphaVantageKey().isNotEmpty() -> Feed.ALPHAVANTAGE
        else -> Feed.YAHOO
    }

    private val frankfurter by lazy { FrankfurterSource(client) }
    private val yahoo by lazy { YahooSource(client) }
    private val traderMade by lazy { TraderMadeSource(client) }
    private val alphaVantage by lazy { AlphaVantageSource(client) }

    /**
     * History with graceful degradation. Forex walks every configured source in order (keys first,
     * then the free Yahoo), then ECB daily for D1, then demo. Never throws, so the chart always
     * has something to draw (important on a flaky mobile connection).
     */
    suspend fun load(
        instrument: Instrument,
        timeframe: Timeframe,
        limit: Int = 720,
        allowLive: Boolean = true,
    ): Source {
        // 1. live: crypto = Binance; forex = every configured source until one answers.
        if (allowLive && instrument.market == Market.CRYPTO && instrument.feed == Feed.BINANCE) {
            runCatching { fetchBinance(instrument.symbol, timeframe, limit) }.getOrNull()?.let {
                if (it.isNotEmpty()) return Source(it, kind = "Binance")
            }
        } else if (allowLive && instrument.market == Market.FOREX) {
            forexChain(instrument, timeframe, limit).forEach { (kind, attempt) ->
                runCatching { attempt() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return Source(it, kind = kind) }
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

    /** Configured forex sources in priority order; Yahoo last because it needs no key. */
    private fun forexChain(
        instrument: Instrument,
        timeframe: Timeframe,
        limit: Int,
    ): List<Pair<String, suspend () -> List<Candle>>> = buildList {
        Settings.twelveDataKey().takeIf { it.isNotEmpty() }?.let {
            add("TwelveData" to suspend { fetchTwelveData(instrument.symbol, timeframe, limit) })
        }
        Settings.traderMadeKey().takeIf { it.isNotEmpty() }?.let {
            add("TraderMade" to suspend { traderMade.candles(instrument.symbol, it, timeframe, limit) })
        }
        Settings.alphaVantageKey().takeIf { it.isNotEmpty() }?.let {
            add("AlphaVantage" to suspend { alphaVantage.candles(instrument.symbol, it, timeframe, limit) })
        }
        add("Yahoo" to suspend { yahoo.candles(instrument.symbol, timeframe, limit) })
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
