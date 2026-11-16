package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.Aggregator
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Timeframe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yahoo Finance chart API — the best free forex source: intraday candles (1m..1h) plus daily,
 * no API key, generous rate limits. Also serves as a universal backup for any instrument.
 * Requires a browser-like User-Agent or Yahoo throttles the request.
 */
class YahooSource(private val client: okhttp3.OkHttpClient = Http.shared) {

    // ---------------------------------------------------------------- candles

    suspend fun candles(symbol: String, tf: Timeframe, limit: Int): List<Candle> {
        // Yahoo has no 4h interval, so H4 is built by aggregating hourly bars.
        val yInterval = when (tf) {
            Timeframe.M1 -> "1m" to "5d"
            Timeframe.M5 -> "5m" to "1mo"
            Timeframe.M15 -> "15m" to "1mo"
            Timeframe.H1 -> "1h" to "2y"
            Timeframe.H4 -> "1h" to "2y"
            Timeframe.D1 -> "1d" to "5y"
        }
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/${yahooSymbol(symbol)}" +
            "?interval=${yInterval.first}&range=${yInterval.second}" +
            "&includePrePost=false&events=div%2Csplit"
        val body = get(url) ?: throw java.io.IOException("Yahoo: no response")
        val raw = parseChart(body, yInterval.first)
        if (raw.isEmpty()) throw java.io.IOException("Yahoo: empty chart")
        // Newest bar may be an in-progress candle — fine, the chart shows it like TradingView.
        return if (tf == Timeframe.H4) Aggregator.aggregate(raw, Timeframe.H4) else raw
    }

    /** Parses the v8 chart envelope: timestamps + OHLCV arrays with nulls on halted bars. */
    private fun parseChart(body: String, requestedInterval: String): List<Candle> {
        val root = JSONObject(body)
        val result = root.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
            ?: throw java.io.IOException("Yahoo: bad envelope ${root.optJSONObject("chart")?.optString("error")}")
        val stamps = result.optJSONArray("timestamp") ?: throw java.io.IOException("Yahoo: no timestamps")
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            ?: throw java.io.IOException("Yahoo: no quote block")
        val open = quote.optJSONArray("open")
        val high = quote.optJSONArray("high")
        val low = quote.optJSONArray("low")
        val close = quote.optJSONArray("close")
        val volume = quote.optJSONArray("volume")
        val out = ArrayList<Candle>(stamps.length())
        for (i in 0 until stamps.length()) {
            val c = close?.optDouble(i) ?: Double.NaN
            val o = open?.optDouble(i) ?: Double.NaN
            val h = high?.optDouble(i) ?: Double.NaN
            val l = low?.optDouble(i) ?: Double.NaN
            if (c.isNaN() || o.isNaN() || h.isNaN() || l.isNaN()) continue // halted/missing bar
            val v = volume?.optDouble(i)?.takeIf { !it.isNaN() } ?: 0.0
            // Yahoo chart timestamps are aligned to the exchange day start for daily bars; both
            // cases are already UTC seconds, which is what the chart contract wants.
            out += Candle(stamps.optLong(i), o, h, l, c, v)
        }
        return out
    }

    // ---------------------------------------------------------------- quotes

    data class Quote(
        val last: Double,
        val changePct: Double,
        val high: Double,
        val low: Double,
        val volume: Double,
    )

    /**
     * One-shot snapshot for any number of instruments via the spark endpoint (single request,
     * no key). Falls back to the chart endpoint's meta price for the batch on partial failures.
     */
    suspend fun quotes(instruments: List<Instrument>): Map<String, Quote> {
        if (instruments.isEmpty()) return emptyMap()
        val syms = instruments.map { yahooSymbol(it.symbol) }.distinct()
        val url = "https://query1.finance.yahoo.com/v7/finance/spark" +
            "?symbols=${syms.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }}" +
            "&range=1d&interval=15m"
        val body = get(url) ?: return emptyMap()
        return runCatching {
            val spark = JSONObject(body).optJSONObject("spark") ?: return emptyMap()
            val out = HashMap<String, Quote>()
            val arr = spark.optJSONArray("result") ?: return emptyMap()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val sym = item.optString("symbol")
                val resp = item.optJSONArray("response")?.optJSONObject(0) ?: continue
                val meta = resp.optJSONObject("meta") ?: continue
                val last = meta.optDouble("regularMarketPrice", 0.0)
                if (last == 0.0) continue
                // 24h change from the series itself (spark has no direct change field).
                val closes = resp.optJSONObject("indicators")?.optJSONArray("quote")
                    ?.optJSONObject(0)?.optJSONArray("close")
                var first = Double.NaN
                if (closes != null) {
                    for (j in 0 until closes.length()) {
                        val v = closes.optDouble(j, Double.NaN)
                        if (!v.isNaN()) { first = v; break }
                    }
                }
                val changePct = if (!first.isNaN() && first != 0.0) (last - first) / first * 100.0 else 0.0
                val id = instruments.firstOrNull { yahooSymbol(it.symbol) == sym }?.id
                    ?: "fx:${meta.optString("symbol")}"
                out[id] = Quote(
                    last = last,
                    changePct = changePct,
                    high = meta.optDouble("regularMarketDayHigh", 0.0),
                    low = meta.optDouble("regularMarketDayLow", 0.0),
                    volume = 0.0,
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    // ---------------------------------------------------------------- plumbing

    private fun yahooSymbol(symbol: String): String =
        symbol.replace("/", "") + "=X"   // "EUR/USD" -> "EURUSD=X"

    private suspend fun get(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                }
            }.getOrNull()
        }
}

/** ChartViewModel keeps one quote type; map Yahoo quotes over to it. */
fun YahooSource.Quote.toTicker(): TickerStream.Quote =
    TickerStream.Quote(last, changePct, high, low, volume)

