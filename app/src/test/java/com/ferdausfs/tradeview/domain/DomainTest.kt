package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    private fun candles(closes: List<Double>): List<Candle> = closes.mapIndexed { i, c ->
        Candle(time = 1_700_000_000L + i * 60, open = c, high = c + 1, low = c - 1, close = c, volume = 100.0)
    }

    @Test
    fun `sma matches hand computed values`() {
        val v = listOf<Double?>(1.0, 2.0, 3.0, 4.0, 5.0)
        val out = Indicators.sma(v, 3)
        assertNull(out[0]); assertNull(out[1])
        assertEquals(2.0, out[2]!!, 1e-9)
        assertEquals(3.0, out[3]!!, 1e-9)
        assertEquals(4.0, out[4]!!, 1e-9)
    }

    @Test
    fun `ema seeds with sma then applies k`() {
        val v = listOf<Double?>(10.0, 20.0, 30.0, 40.0)
        val out = Indicators.ema(v, 3)   // k = 0.5
        assertNull(out[1])
        assertEquals(20.0, out[2]!!, 1e-9)           // (10+20+30)/3
        assertEquals(30.0, out[3]!!, 1e-9)           // 40*0.5 + 20*0.5
    }

    @Test
    fun `rsi is 100 on a monotonic rise and 0 on a fall`() {
        val rising = candles((1..40).map { it.toDouble() })
        val rsiUp = Indicators.rsi(rising, 14)
        assertTrue(rsiUp.last()!!.let { it > 99.9 })

        val falling = candles((1..40).map { (60 - it).toDouble() })
        val rsiDown = Indicators.rsi(falling, 14)
        assertTrue(rsiDown.last()!!.let { it < 0.1 })
    }

    @Test
    fun `bollinger bands straddle the middle and widen with volatility`() {
        val flat = candles(List(60) { 100.0 })
        val bb = Indicators.bollinger(flat, 20, 2.0)
        assertEquals(100.0, bb.middle.last()!!, 1e-9)
        assertEquals(100.0, bb.upper.last()!!, 1e-9)

        val noisy = candles(List(60) { if (it % 2 == 0) 100.0 else 110.0 })
        val bb2 = Indicators.bollinger(noisy, 20, 2.0)
        assertTrue(bb2.upper.last()!! > bb2.middle.last()!!)
    }

    @Test
    fun `macd histogram equals macd minus signal`() {
        val data = candles((1..120).map { 100.0 + (it % 7) * 1.5 })
        val (macd, signal, hist) = Indicators.macd(data.map { it.close })
        val idx = 100
        assertNotNull(macd[idx]); assertNotNull(signal[idx])
        assertEquals(macd[idx]!! - signal[idx]!!, hist[idx]!!, 1e-9)
    }

    @Test
    fun `vwap is volume weighted typical price`() {
        val data = listOf(
            Candle(1, 10.0, 12.0, 8.0, 10.0, 100.0),
            Candle(2, 20.0, 22.0, 18.0, 20.0, 300.0),
        )
        val out = Indicators.vwap(data)
        val expected1 = (12 + 8 + 10) / 3.0
        val expected2 = ((12 + 8 + 10) / 3.0 * 100 + (22 + 18 + 20) / 3.0 * 300) / 400
        assertEquals(expected1, out[0]!!, 1e-9)
        assertEquals(expected2, out[1]!!, 1e-9)
    }
}

class AggregatorTest {

    @Test
    fun `aggregates five 1m candles into one 5m candle`() {
        val base = (0 until 5).map { i ->
            Candle(time = 300_000L + i * 60, open = 10.0 + i, high = 20.0 + i, low = 5.0 - i, close = 15.0 + i, volume = 10.0)
        }
        val out = Aggregator.aggregate(base, Timeframe.M5)
        assertEquals(1, out.size)
        assertEquals(10.0, out[0].open, 1e-9)
        assertEquals(24.0, out[0].high, 1e-9)
        assertEquals(1.0, out[0].low, 1e-9)
        assertEquals(19.0, out[0].close, 1e-9)
        assertEquals(50.0, out[0].volume, 1e-9)
    }

    @Test
    fun `keeps separate buckets apart`() {
        val base = (0 until 12).map { i ->
            Candle(time = 600_000L + i * 60, open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 1.0)
        }
        val out = Aggregator.aggregate(base, Timeframe.M5)
        assertEquals(3, out.size)
        assertEquals(5.0, out[0].volume, 1e-9)
    }
}

class ReplayEngineTest {

    private val history = (0 until 200).map { i ->
        Candle(time = i * 60L, open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 1.0)
    }

    @Test
    fun `arm limits visible candles and reports state`() {
        val engine = ReplayEngine()
        engine.arm(history, 120)
        assertEquals(120, engine.visible().size)
        assertTrue(engine.isActive)
    }

    @Test
    fun `step forward and back move one bar`() {
        val engine = ReplayEngine()
        engine.arm(history, 120)
        engine.stepForward()
        assertEquals(121, engine.visible().size)
        engine.stepBack()
        engine.stepBack()
        assertEquals(119, engine.visible().size)
    }

    @Test
    fun `tick advances while playing and pauses at the end`() {
        val engine = ReplayEngine()
        engine.arm(history, 198)
        engine.setSpeed(10.0)
        engine.play()
        engine.tick(1.0)
        assertTrue(engine.visible().size >= 199)
        engine.tick(1.0)
        assertEquals(200, engine.visible().size)
        assertTrue(!engine.isPlaying)
    }

    @Test
    fun `cannot step before the start window`() {
        val engine = ReplayEngine()
        engine.arm(history, 12)
        repeat(20) { engine.stepBack() }
        assertEquals(10, engine.visible().size)
    }
}

class PineLiteTest {

    private val candles = (0 until 120).map { i ->
        Candle(time = i * 60L, open = 100.0 + i, high = 101.0 + i, low = 99.0 + i, close = 100.0 + i, volume = 50.0)
    }

    @Test
    fun `compiles ema plot with values`() {
        val result = PineLite.compile("plot(ema(close, 20))", candles)
        assertTrue(result.isSuccess)
        val values = result.getOrNull()!!.values
        assertEquals(candles.size, values.size)
        assertNotNull(values.last())
        // EMA on a linear ramp lags by (n-1)/2 = 9.5, so 219.0 -> 209.5 exactly.
        assertEquals(209.5, values.last()!!, 0.5)
    }

    @Test
    fun `supports arithmetic between two series`() {
        val result = PineLite.compile("plot(sma(close, 5) - sma(close, 20))", candles)
        assertTrue(result.isSuccess)
        val values = result.getOrNull()!!.values
        val last = values.last()!!
        assertNotNull(last)
        // Fast SMA above slow SMA on a rising ramp by (20-5)/2 = 7.5.
        assertEquals(7.5, last, 0.5)
    }

    @Test
    fun `rejects unknown function`() {
        val result = PineLite.compile("plot(banana(close, 3))", candles)
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects unbalanced parentheses`() {
        val result = PineLite.compile("plot(ema(close, 20)", candles)
        assertTrue(result.isFailure)
    }

    @Test
    fun `supports rsi and atr`() {
        assertTrue(PineLite.compile("plot(rsi(14))", candles).isSuccess)
        assertTrue(PineLite.compile("plot(atr(14))", candles).isSuccess)
        assertTrue(PineLite.compile("plot(vwap())", candles).isSuccess)
    }
}