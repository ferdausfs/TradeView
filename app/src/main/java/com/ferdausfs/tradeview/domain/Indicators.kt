package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Indicator engine. Every calculation runs on the Kotlin side (spec §2: the WebView only
 * renders). Series are emitted as `Double?` aligned with the input candles; nulls are
 * dropped when serialised for the chart.
 */
object Indicators {

    private fun d(v: Double): String = if (v.isNaN() || v.isInfinite()) "0" else String.format(Locale.US, "%.6f", v)

    fun sma(values: List<Double?>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        var filled = 0
        val window = ArrayDeque<Double?>()
        for (v in values) {
            window.addLast(v)
            if (v != null) { sum += v; filled++ }
            if (window.size > period) {
                val removed = window.removeFirst()
                if (removed != null) { sum -= removed; filled-- }
            }
            out += if (window.size == period && filled == period) sum / period else null
        }
        return out
    }

    fun ema(values: List<Double?>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        val k = 2.0 / (period + 1)
        var prev: Double? = null
        var seedSum = 0.0
        var seedCount = 0
        for (v in values) {
            if (v == null) { out += prev; continue }
            prev = if (prev == null) {
                seedSum += v
                seedCount++
                if (seedCount == period) seedSum / period else null
            } else {
                v * k + prev * (1 - k)
            }
            out += prev
        }
        return out
    }

    fun rsi(candles: List<Candle>, period: Int = 14): List<Double?> {
        val out = ArrayList<Double?>(candles.size)
        var avgGain = 0.0
        var avgLoss = 0.0
        var seeded = false
        var gains = 0.0
        var losses = 0.0
        for (i in candles.indices) {
            if (i == 0) { out += null; continue }
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            if (!seeded) {
                gains += gain
                losses += loss
                if (i == period) {
                    avgGain = gains / period
                    avgLoss = losses / period
                    seeded = true
                    out += if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
                } else out += null
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period
                avgLoss = (avgLoss * (period - 1) + loss) / period
                out += if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
            }
        }
        return out
    }

    /** Returns macd, signal and histogram series. */
    fun macd(
        closes: List<Double?>,
        fast: Int = 12,
        slow: Int = 26,
        signalPeriod: Int = 9,
    ): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val fastEma = ema(closes, fast)
        val slowEma = ema(closes, slow)
        val macd = fastEma.indices.map { i ->
            val f = fastEma[i]
            val s = slowEma[i]
            if (f != null && s != null) f - s else null
        }
        val signal = ema(macd, signalPeriod)
        val hist = macd.indices.map { i ->
            val m = macd[i]
            val s = signal[i]
            if (m != null && s != null) m - s else null
        }
        return Triple(macd, signal, hist)
    }

    data class BollingerBands(val upper: List<Double?>, val middle: List<Double?>, val lower: List<Double?>)

    fun bollinger(candles: List<Candle>, period: Int = 20, mult: Double = 2.0): BollingerBands {
        val closes = candles.map { it.close }
        val middle = sma(closes, period)
        val upper = ArrayList<Double?>(candles.size)
        val lower = ArrayList<Double?>(candles.size)
        for (i in closes.indices) {
            val mid = middle[i]
            if (mid == null) { upper += null; lower += null; continue }
            var variance = 0.0
            for (j in i - period + 1..i) variance += (closes[j] - mid) * (closes[j] - mid)
            val sd = sqrt(variance / period)
            upper += mid + mult * sd
            lower += mid - mult * sd
        }
        return BollingerBands(upper, middle, lower)
    }

    fun vwap(candles: List<Candle>): List<Double?> {
        var pv = 0.0
        var vol = 0.0
        return candles.map { c ->
            val typical = (c.high + c.low + c.close) / 3.0
            pv += typical * c.volume
            vol += c.volume
            if (vol > 0) pv / vol else null
        }
    }

    fun atr(candles: List<Candle>, period: Int = 14): List<Double?> {
        val tr = candles.mapIndexed { i, c ->
            if (i == 0) c.high - c.low
            else maxOf(c.high - c.low, abs(c.high - candles[i - 1].close), abs(c.low - candles[i - 1].close))
        }
        return ema(tr.map { it as Double? }, period)
    }

    /** Serialises one indicator instance into the JSON contract consumed by chart.js. */
    fun seriesJson(candles: List<Candle>, series: List<Triple<String, String, List<Double?>>>): String {
        val sb = StringBuilder("[")
        series.forEachIndexed { sIdx, (kind, color, values) ->
            if (sIdx > 0) sb.append(',')
            sb.append("""{"kind":"$kind","color":"$color","width":2,"data":[""")
            var first = true
            values.forEachIndexed { i, v ->
                if (v == null || v.isNaN()) return@forEachIndexed
                if (!first) sb.append(',')
                first = false
                sb.append("""{"time":${candles[i].time},"value":${d(v)}}""")
            }
            sb.append("]}")
        }
        sb.append(']')
        return sb.toString()
    }

    fun macdHistogram(candles: List<Candle>, hist: List<Double?>): String {
        val sb = StringBuilder("[")
        var first = true
        hist.forEachIndexed { i, v ->
            if (v == null || v.isNaN()) return@forEachIndexed
            if (!first) sb.append(',')
            first = false
            val color = if (v >= 0) "#26A69A" else "#EF5350"
            sb.append("""{"time":${candles[i].time},"value":${d(v)},"color":"$color"}""")
        }
        sb.append(']')
        return sb.toString()
    }

    fun macdHistogramSeriesJson(candles: List<Candle>, hist: List<Double?>): String =
        """[{"kind":"histogram","color":"#26A69A","width":1,"data":${macdHistogram(candles, hist)}}]"""
}
