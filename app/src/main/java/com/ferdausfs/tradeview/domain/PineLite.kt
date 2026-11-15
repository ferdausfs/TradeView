package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Candle

/**
 * Pine-lite — a deliberately small subset of Pine Script v5 for user indicators (spec §8 is
 * honest about this being a subset). Supported today:
 *
 *   plot(<expr>)
 *   numbers, + - * / ( ), unary minus
 *   close, open, high, low, volume
 *   sma(x, n), ema(x, n), rsi(n), atr(n), vwap()
 *
 * Example: plot(ema(close, 20) - ema(close, 50))
 */
object PineLite {

    data class Compiled(val label: String, val values: List<Double?>)

    private sealed interface Node
    private data class Num(val v: Double) : Node
    private data class Var(val name: String) : Node
    private data class Unary(val a: Node) : Node
    private data class Binary(val op: Char, val a: Node, val b: Node) : Node
    private data class Call(val name: String, val args: List<Node>) : Node

    private const val MAX_LENGTH = 400

    fun compile(script: String, candles: List<Candle>): Result<Compiled> {
        val text = script.trim()
        if (text.isEmpty()) return Result.failure(IllegalArgumentException("empty script"))
        if (text.length > MAX_LENGTH) return Result.failure(IllegalArgumentException("script too long"))
        if (candles.size < 3) return Result.failure(IllegalArgumentException("not enough candles"))

        val exprText = when {
            text.startsWith("plot") -> {
                val open = text.indexOf('(')
                if (open < 0 || !text.endsWith(")")) return Result.failure(IllegalArgumentException("use plot(<expr>)"))
                text.substring(open + 1, text.length - 1)
            }
            else -> text
        }

        return try {
            val tokens = tokenize(exprText)
            val parser = Parser(tokens)
            val ast = parser.parseExpression()
            if (!parser.atEnd()) return Result.failure(IllegalArgumentException("unexpected token near end"))
            val series = Evaluator(candles).eval(ast)
            Compiled(
                label = "S: " + exprText.trim().take(28),
                values = series.map { if (it.isNaN() || it.isInfinite()) null else it },
            ).let { Result.success(it) }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "parse error"))
        }
    }

    private data class Token(val kind: Int, val text: String, val value: Double = 0.0)

    private const val T_NUM = 0
    private const val T_IDENT = 1
    private const val T_PUNCT = 2

    private fun tokenize(src: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    val raw = src.substring(start, i)
                    out += Token(T_NUM, raw, raw.toDouble())
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    out += Token(T_IDENT, src.substring(start, i))
                }
                c in "+-*/()," -> { out += Token(T_PUNCT, c.toString()); i++ }
                else -> throw IllegalArgumentException("bad character '$c'")
            }
        }
        return out
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun atEnd() = pos >= tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun expectPunct(p: String) {
            val t = peek() ?: throw IllegalArgumentException("expected '$p'")
            if (t.kind != T_PUNCT || t.text != p) throw IllegalArgumentException("expected '$p'")
            pos++
        }

        fun parseExpression(): Node = parseAdd()

        private fun parseAdd(): Node {
            var left = parseMul()
            while (true) {
                val t = peek() ?: return left
                if (t.kind == T_PUNCT && (t.text == "+" || t.text == "-")) {
                    pos++
                    left = Binary(t.text[0], left, parseMul())
                } else return left
            }
        }

        private fun parseMul(): Node {
            var left = parseUnary()
            while (true) {
                val t = peek() ?: return left
                if (t.kind == T_PUNCT && (t.text == "*" || t.text == "/")) {
                    pos++
                    left = Binary(t.text[0], left, parseUnary())
                } else return left
            }
        }

        private fun parseUnary(): Node {
            val t = peek() ?: throw IllegalArgumentException("unexpected end")
            if (t.kind == T_PUNCT && t.text == "-") { pos++; return Unary(parseUnary()) }
            return parsePrimary()
        }

        private fun parsePrimary(): Node {
            val t = peek() ?: throw IllegalArgumentException("unexpected end")
            when (t.kind) {
                T_NUM -> { pos++; return Num(t.value) }
                T_IDENT -> {
                    pos++
                    val next = peek()
                    if (next != null && next.kind == T_PUNCT && next.text == "(") {
                        pos++
                        val args = ArrayList<Node>()
                        if (peek()?.text != ")") {
                            args += parseExpression()
                            while (peek()?.text == ",") { pos++; args += parseExpression() }
                        }
                        expectPunct(")")
                        return Call(t.text.lowercase(), args)
                    }
                    return Var(t.text.lowercase())
                }
                T_PUNCT -> if (t.text == "(") {
                    pos++
                    val inner = parseExpression()
                    expectPunct(")")
                    return inner
                } else throw IllegalArgumentException("unexpected '${t.text}'")
            }
            throw IllegalArgumentException("unexpected token")
        }
    }

    private class Evaluator(private val candles: List<Candle>) {

        private val closes = candles.map { it.close }
        private val opens = candles.map { it.open }
        private val highs = candles.map { it.high }
        private val lows = candles.map { it.low }
        private val volumes = candles.map { it.volume }

        fun eval(node: Node): DoubleArray = when (node) {
            is Num -> DoubleArray(candles.size) { node.v }
            is Var -> when (node.name) {
                "close", "c" -> closes.toDoubleArray()
                "open", "o" -> opens.toDoubleArray()
                "high", "h" -> highs.toDoubleArray()
                "low", "l" -> lows.toDoubleArray()
                "volume", "v" -> volumes.toDoubleArray()
                else -> throw IllegalArgumentException("unknown series '${node.name}'")
            }
            is Unary -> eval(node.a).map { -it }.toDoubleArray()
            is Binary -> {
                val a = eval(node.a)
                val b = eval(node.b)
                DoubleArray(candles.size) { i ->
                    when (node.op) {
                        '+' -> a[i] + b[i]
                        '-' -> a[i] - b[i]
                        '*' -> a[i] * b[i]
                        '/' -> if (b[i] == 0.0) Double.NaN else a[i] / b[i]
                        else -> Double.NaN
                    }
                }
            }
            is Call -> call(node)
        }

        private fun intArg(node: Node, default: Int): Int = when (node) {
            is Num -> node.v.toInt()
            else -> default
        }

        private fun call(node: Call): DoubleArray {
            val n = candles.size
            return when (node.name) {
                "sma" -> {
                    require(node.args.size == 2) { "sma(series, length)" }
                    val src = eval(node.args[0])
                    val len = intArg(node.args[1], 14).coerceIn(1, n)
                    rollingMean(src, len)
                }
                "ema" -> {
                    require(node.args.size == 2) { "ema(series, length)" }
                    val src = eval(node.args[0])
                    val len = intArg(node.args[1], 14).coerceIn(1, n)
                    exponential(src, len)
                }
                "rsi" -> {
                    val len = intArg(node.args.firstOrNull() ?: Num(14.0), 14).coerceIn(2, n - 1)
                    rsi(len)
                }
                "atr" -> {
                    val len = intArg(node.args.firstOrNull() ?: Num(14.0), 14).coerceIn(1, n)
                    atr(len)
                }
                "vwap" -> vwap()
                else -> throw IllegalArgumentException("unknown function '${node.name}'")
            }
        }

        private fun rollingMean(src: DoubleArray, len: Int): DoubleArray {
            val out = DoubleArray(src.size) { Double.NaN }
            var sum = 0.0
            var valid = 0
            for (i in src.indices) {
                if (!src[i].isNaN()) { sum += src[i]; valid++ }
                if (i >= len) {
                    val old = src[i - len]
                    if (!old.isNaN()) { sum -= old; valid-- }
                }
                if (i >= len - 1 && valid == len) out[i] = sum / len
            }
            return out
        }

        private fun exponential(src: DoubleArray, len: Int): DoubleArray {
            val out = DoubleArray(src.size) { Double.NaN }
            val k = 2.0 / (len + 1)
            var prev = Double.NaN
            var seed = 0.0
            var count = 0
            for (i in src.indices) {
                if (src[i].isNaN()) { out[i] = prev; continue }
                prev = if (prev.isNaN()) {
                    seed += src[i]; count++
                    if (count >= len) seed / len else Double.NaN
                } else src[i] * k + prev * (1 - k)
                out[i] = prev
            }
            return out
        }

        private fun rsi(len: Int): DoubleArray {
            val out = DoubleArray(candles.size) { Double.NaN }
            var avgGain = 0.0
            var avgLoss = 0.0
            var seeded = false
            var gains = 0.0
            var losses = 0.0
            for (i in 1 until candles.size) {
                val change = closes[i] - closes[i - 1]
                val gain = if (change > 0) change else 0.0
                val loss = if (change < 0) -change else 0.0
                if (!seeded) {
                    gains += gain; losses += loss
                    if (i == len) {
                        avgGain = gains / len; avgLoss = losses / len; seeded = true
                        out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
                    }
                } else {
                    avgGain = (avgGain * (len - 1) + gain) / len
                    avgLoss = (avgLoss * (len - 1) + loss) / len
                    out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
                }
            }
            return out
        }

        private fun atr(len: Int): DoubleArray {
            val tr = DoubleArray(candles.size)
            for (i in candles.indices) {
                tr[i] = if (i == 0) highs[0] - lows[0] else maxOf(
                    highs[i] - lows[i],
                    kotlin.math.abs(highs[i] - closes[i - 1]),
                    kotlin.math.abs(lows[i] - closes[i - 1]),
                )
            }
            return exponential(tr, len)
        }

        private fun vwap(): DoubleArray {
            val out = DoubleArray(candles.size) { Double.NaN }
            var pv = 0.0
            var vol = 0.0
            for (i in candles.indices) {
                val typical = (highs[i] + lows[i] + closes[i]) / 3.0
                pv += typical * volumes[i]
                vol += volumes[i]
                if (vol > 0) out[i] = pv / vol
            }
            return out
        }
    }
}
