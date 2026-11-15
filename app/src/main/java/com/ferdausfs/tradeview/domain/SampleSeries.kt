package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Timeframe
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic demo series — the fallback feed when no live source is reachable, and for forex
 * intraday until a key is configured. Seeded per instrument so every device shows the same chart.
 */
object SampleSeries {

    fun generate(instrument: Instrument, timeframe: Timeframe, count: Int = 600): List<Candle> {
        var state = seedFor(instrument.id, timeframe)
        fun rnd(): Double {
            state = (state * 1_664_525L + 1_013_904_223L) % 4_294_967_296L
            return state.toDouble() / 4_294_967_296.0
        }

        val step = timeframe.seconds
        val nowBucket = (System.currentTimeMillis() / 1000L) / step * step
        val start = nowBucket - count * step

        val vol = instrument.volatility
        val candle = instrument.basePrice * (0.018 * vol)
        var price = instrument.basePrice

        val out = ArrayList<Candle>(count)
        for (i in 0 until count) {
            val drift = (rnd() - 0.485) * candle
            val open = price
            val close = (open + drift).coerceAtLeast(instrument.basePrice * 0.2)
            val wick = candle * (0.15 + rnd() * 0.55)
            val high = maxOf(open, close) + wick * rnd()
            val low = minOf(open, close) - wick * rnd()
            val volume = (120 + rnd() * 900) * vol
            out += Candle(start + i * step, open, high, low, close, volume.roundToInt().toDouble())
            price = close
        }
        return out
    }

    private fun seedFor(id: String, tf: Timeframe): Long {
        var h = 1125899906842597L
        for (ch in id) h = 31 * h + ch.code
        return abs(h * 31 + tf.ordinal * 7919L) % 2_147_483_647L
    }
}
