package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * TraderMade — forex specialist. Free tier is generous for a keyed source and intraday bars are
 * true OHLC (better than synthetic). Timeseries limits: minute data max 2 days per call,
 * hourly max ~8 years (1 month/call on low plans), daily up to 20 years (1 year/call).
 */
class TraderMadeSource(private val client: okhttp3.OkHttpClient = Http.shared) {

    suspend fun candles(symbol: String, apiKey: String, tf: Timeframe, limit: Int): List<Candle> {
        val pair = symbol.replace("/", "")   // "EUR/USD" -> "EURUSD"
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val start: LocalDateTime = when (tf) {
            Timeframe.M1 -> maxOf(now.minusMinutes(limit + 30L), now.minusDays(2))   // 2-day max window
            Timeframe.M5 -> maxOf(now.minusMinutes(limit * 5L + 120), now.minusDays(2))
            Timeframe.M15 -> maxOf(now.minusMinutes(limit * 15L + 240), now.minusDays(2))
            Timeframe.H1 -> now.minusHours(limit.toLong() + 2)
            Timeframe.H4 -> now.minusHours(limit * 4L + 8)
            Timeframe.D1 -> now.minusDays(limit.toLong() + 2)
        }
        val (interval, period) = when (tf) {
            Timeframe.M1 -> "minute" to 1
            Timeframe.M5 -> "minute" to 5
            Timeframe.M15 -> "minute" to 15
            Timeframe.H1 -> "hourly" to 1
            Timeframe.H4 -> "hourly" to 4
            Timeframe.D1 -> "daily" to 1
        }
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm")
        val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startStr = if (tf == Timeframe.D1) start.format(dayFmt) else start.format(dateFmt)
        val endStr = if (tf == Timeframe.D1) now.format(dayFmt) else now.format(dateFmt)

        val url = "https://marketdata.tradermade.com/api/v1/timeseries" +
            "?currency=$pair&api_key=$apiKey&interval=$interval&period=$period" +
            "&start_date=$startStr&end_date=$endStr&format=records"
        val body = get(url) ?: throw java.io.IOException("TraderMade: no response")
        val root = JSONObject(body)
        root.optJSONArray("errors")?.let { if (it.length() > 0) throw java.io.IOException("TraderMade: ${it.optJSONObject(0)}") }
        root.optString("message")?.let { if (it.contains("validation", true)) throw java.io.IOException("TraderMade: $it") }
        val quotes = root.optJSONArray("quotes") ?: throw java.io.IOException("TraderMade: no quotes")
        val out = ArrayList<Candle>(quotes.length())
        for (i in 0 until quotes.length()) {
            val q = quotes.getJSONObject(i)
            val stamp = parseStamp(q.optString("date")) ?: continue
            out += Candle(
                time = stamp,
                open = q.optDouble("open", Double.NaN),
                high = q.optDouble("high", Double.NaN),
                low = q.optDouble("low", Double.NaN),
                close = q.optDouble("close", Double.NaN),
                volume = 0.0,
            )
        }
        val clean = out.filter { !it.close.isNaN() && !it.open.isNaN() }
        if (clean.isEmpty()) throw java.io.IOException("TraderMade: empty series")
        return clean
    }

    /** Parses "2019-10-01", "2025-04-10 12:00:00" and "2025-04-10-12:00" as UTC. */
    private fun parseStamp(raw: String): Long? {
        val text = raw.trim().replaceFirst('-', 'T').replace(" ", "T")
        return runCatching {
            when (text.length) {
                10 -> LocalDateTime.parse(text + "T00:00:00")
                16 -> LocalDateTime.parse(text + ":00")
                else -> LocalDateTime.parse(text)
            }.toEpochSecond(ZoneOffset.UTC)
        }.getOrNull()
    }

    private suspend fun get(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
        }
}
