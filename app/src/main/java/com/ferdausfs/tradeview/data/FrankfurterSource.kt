package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Instrument
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ECB reference rates via frankfurter.dev — free, no API key, daily closes only.
 * Used for forex D1 history; intraday forex stays on TwelveData/demo.
 */
class FrankfurterSource(private val client: okhttp3.OkHttpClient = Http.shared) {

    private val json = Json { ignoreUnknownKeys = true }

    /** @param pair e.g. "EUR/USD" -> base EUR, quote USD. */
    suspend fun dailyCandles(pair: String, days: Int): List<Candle> {
        val (base, quote) = pair.split('/').let { it[0].uppercase() to it[1].uppercase() }
        val end = LocalDate.now(java.time.ZoneOffset.UTC)
        val start = end.minusDays((days + 40).toLong())
        val url = "https://api.frankfurter.dev/v1/$start..$end?base=$base&symbols=$quote"
        val body = runCatching {
            client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string()
            }
        }.getOrThrow() ?: throw IOException("empty")

        val rates = json.parseToJsonElement(body).jsonObject["rates"]?.jsonObject
            ?: throw IOException("no rates")
        val out = ArrayList<Candle>()
        var prev: Double? = null
        for (day in rates.keys.sorted()) {
            val rate = runCatching {
                rates[day]!!.jsonObject[quote]!!.jsonPrimitive.content.toDouble()
            }.getOrNull() ?: continue
            val epoch = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val open = prev ?: rate
            out += Candle(
                time = epoch,
                open = open,
                high = maxOf(open, rate),
                low = minOf(open, rate),
                close = rate,
                volume = 0.0,
            )
            prev = rate
        }
        return out
    }
}
