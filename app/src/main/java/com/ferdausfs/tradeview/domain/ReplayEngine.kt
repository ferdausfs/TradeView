package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Timeframe
import java.util.Locale

/**
 * Bar replay engine (spec §5). The full history stays in memory here; the chart only ever
 * receives the slice the user is allowed to see.
 */
class ReplayEngine {

    enum class State { IDLE, ARMED, PLAYING, PAUSED }

    data class Snapshot(
        val state: State,
        val visibleCount: Int,
        val total: Int,
        val playing: Boolean,
    )

    private var full: List<Candle> = emptyList()
    private var cutoff: Int = 0          // number of candles the user may currently see
    private var state: State = State.IDLE
    private var speedPerSecond: Double = 2.0

    val isActive: Boolean get() = state != State.IDLE
    val isPlaying: Boolean get() = state == State.PLAYING

    fun arm(history: List<Candle>, startIndex: Int) {
        require(history.isNotEmpty())
        full = history
        cutoff = startIndex.coerceIn(10, history.size)
        state = State.ARMED
    }

    fun play() {
        if (state == State.ARMED || state == State.PAUSED) state = State.PLAYING
    }

    fun pause() {
        if (state == State.PLAYING) state = State.PAUSED
    }

    fun exit() {
        state = State.IDLE
        full = emptyList()
        cutoff = 0
    }

    fun stepForward(): Snapshot? = if (cutoff < full.size) { cutoff++; snapshot() } else snapshot()

    fun stepBack(): Snapshot? = if (cutoff > 10) { cutoff--; snapshot() } else snapshot()

    fun setSpeed(perSecond: Double) {
        speedPerSecond = perSecond.coerceIn(0.5, 30.0)
    }

    fun tick(dtSeconds: Double): Snapshot? {
        if (state != State.PLAYING) return snapshot()
        val advance = (speedPerSecond * dtSeconds)
        var advanced = advance.toInt()
        val frac = advance - advanced
        if (frac > 0 && (frac * 100).toInt() % 10 == 0) advanced += 1
        cutoff = (cutoff + maxOf(advanced, if (advance > 0) 1 else 0)).coerceAtMost(full.size)
        if (cutoff >= full.size) state = State.PAUSED
        return snapshot()
    }

    /** Candles the chart may currently display. */
    fun visible(): List<Candle> = if (state == State.IDLE) full else full.take(cutoff)

    fun currentIndicatorBase(): List<Candle> = visible()

    fun snapshot(): Snapshot = Snapshot(state, cutoff, full.size, state == State.PLAYING)
}

/** Aggregates 1-minute candles into higher timeframes (demo source is 1m). */
object Aggregator {
    fun aggregate(candles: List<Candle>, target: Timeframe): List<Candle> {
        if (target == Timeframe.M1) return candles
        val out = ArrayList<Candle>()
        var bucketTime = -1L
        var o = 0.0; var h = -Double.MAX_VALUE; var l = Double.MAX_VALUE; var c = 0.0; var v = 0.0
        fun flush() {
            if (bucketTime >= 0) out += Candle(bucketTime, o, h, l, c, v)
        }
        for (candle in candles) {
            val bucket = candle.time / target.seconds * target.seconds
            if (bucket != bucketTime) {
                flush()
                bucketTime = bucket
                o = candle.open; h = candle.high; l = candle.low; c = candle.close; v = candle.volume
            } else {
                h = maxOf(h, candle.high)
                l = minOf(l, candle.low)
                c = candle.close
                v += candle.volume
            }
        }
        flush()
        return out
    }

    fun timeframeLabel(seconds: Long): String {
        val tf = Timeframe.entries.firstOrNull { it.seconds == seconds } ?: return "${seconds}s"
        return tf.label
    }

    fun formatTime(timeSec: Long, tf: Timeframe): String {
        val sdf = java.text.SimpleDateFormat(
            if (tf.seconds >= 86_400) "dd MMM" else if (tf.seconds >= 3_600) "dd MMM HH:mm" else "HH:mm",
            Locale.US,
        )
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(timeSec * 1000)
    }
}
