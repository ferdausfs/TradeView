package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.Aggregator
import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * Alpha Vantage — free tier is small (25 requests/day) but official; it works as an extra
 * failover step in the forex chain when other sources are down.
 */
class AlphaVantageSource(private val client: okhttp3.OkHttpClient = Http.shared) {

    suspend fun candles(symbol: String, apiKey: String, tf: Timeframe, limit: Int): List<Candle> {
        val from = symbol.substringBefore('/')
        val to = symbol.substringAfter('/')
        // No 4h interval — request hourly and aggregate.
        val (fn, interval) = when (tf) {
            Timeframe.M1 -> "FX_INTRADAY" to "1min"
            Timeframe.M5 -> "FX_INTRADAY" to "5min"
            Timeframe.M15 -> "FX_INTRADAY" to "15min"
            Timeframe.H1, Timeframe.H4 -> "FX_INTRADAY" to "60min"
            Timeframe.D1 -> "FX_DAILY" to "daily"
        }
        val url = "https://www.alphavantage.co/query?function=$fn" +
            "&from_symbol=$from&to_symbol=$to&interval=$interval&outputsize=full&apikey=$apiKey"
        val body = get(url) ?: throw java.io.IOException("AlphaVantage: no response")
        val root = JSONObject(body)
        root.optString("Information").takeIf { it.isNotEmpty() }?.let { throw java.io.IOException("AlphaVantage: $it") }
        root.optString("Note").takeIf { it.isNotEmpty() }?.let { throw java.io.IOException("AlphaVantage: rate limited") }
        root.optString("Error Message").takeIf { it.isNotEmpty() }?.let { throw java.io.IOException("AlphaVantage: bad request") }
        val seriesKey = when (tf) {
            Timeframe.D1 -> "Time Series FX (Daily)"
            else -> "Time Series FX ($interval)"
        }
        val series = root.optJSONObject(seriesKey) ?: throw java.io.IOException("AlphaVantage: no series")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val out = series.keys().asSequence().mapNotNull { stamp ->
            val o = series.optJSONObject(stamp) ?: return@mapNotNull null
            val time = runCatching { LocalDateTime.parse(stamp, fmt) }
                .recoverCatching { java.time.LocalDate.parse(stamp).atStartOfDay() } // daily keys
                .map { it.toEpochSecond(ZoneOffset.UTC) }
                .getOrNull() ?: return@mapNotNull null
            Candle(
                time = time,
                open = o.optDouble("1. open", Double.NaN),
                high = o.optDouble("2. high", Double.NaN),
                low = o.optDouble("3. low", Double.NaN),
                close = o.optDouble("4. close", Double.NaN),
                volume = 0.0,
            )
        }.filter { !it.close.isNaN() }.sortedBy { it.time }.toList()
        if (out.isEmpty()) throw java.io.IOException("AlphaVantage: empty series")
        return if (tf == Timeframe.H4) Aggregator.aggregate(out, Timeframe.H4) else out
    }

    private suspend fun get(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
        }
}
