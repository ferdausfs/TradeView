package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic demo series. Until the Binance adapter lands (P2) the chart needs believable
 * data that is identical on every device, so this uses a seeded LCG rather than Math.random.
 */
object SampleSeries {

    data class SymbolInfo(val symbol: String, val basePrice: Double, val volatility: Double)

    val symbols = listOf(
        SymbolInfo("BTCUSDT", 96_250.0, 1.0),
        SymbolInfo("ETHUSDT", 3_180.0, 1.35),
        SymbolInfo("SOLUSDT", 184.5, 1.9),
        SymbolInfo("BNBUSDT", 612.0, 1.2),
        SymbolInfo("XRPUSDT", 2.41, 2.1),
    )

    private fun seedFor(symbol: String, tf: Timeframe): Long {
        var h = 1125899906842597L
        for (ch in symbol) h = 31 * h + ch.code
        return abs(h * 31 + tf.ordinal * 7919L) % 2_147_483_647L
    }

    fun generate(symbol: String, timeframe: Timeframe, count: Int = 600): List<Candle> {
        val info = symbols.firstOrNull { it.symbol == symbol } ?: symbols.first()
        var state = seedFor(symbol, timeframe)
        fun rnd(): Double {
            state = (state * 1_664_525L + 1_013_904_223L) % 4_294_967_296L
            return state.toDouble() / 4_294_967_296.0
        }

        val step = timeframe.seconds
        val nowBucket = (System.currentTimeMillis() / 1000L) / step * step
        val start = nowBucket - count * step

        val vol = info.volatility
        val candle = info.basePrice * (0.018 * vol) // one-bar drift scale
        var price = info.basePrice

        val out = ArrayList<Candle>(count)
        for (i in 0 until count) {
            val drift = (rnd() - 0.485) * candle
            val open = price
            val close = (open + drift).coerceAtLeast(info.basePrice * 0.2)
            val wick = candle * (0.15 + rnd() * 0.55)
            val high = maxOf(open, close) + wick * rnd()
            val low = minOf(open, close) - wick * rnd()
            val volume = (120 + rnd() * 900) * vol
            out += Candle(start + i * step, open, high, low, close, volume.roundToInt().toDouble())
            price = close
        }
        return out
    }
}
