# TradeView — Agent Log

> Continuity file. Entries are **prepended** (newest first) and never deleted.

## Session — 2026-11-15 — P3/P4/P5/P8-lite: TradingView-style shell, timeframes, bar replay, indicators, Pine-lite
- Agent/Tool: OpenCode · branch `main`
- Feature: the four things the user asked for, built as one coherent chart-core release:
  timeframe switching, bar replay, built-in indicators, custom indicator scripts, and a
  TradingView-like chrome around the canvas.
- **Architecture kept:** all logic in Kotlin (`domain/`), WebView renders only. JS API is now
  `setData / update / setIndicators / setLogScale / setTheme / fitContent`.
- What changed:
  1. **domain/model/Candle.kt, Timeframe.kt** — canonical candle + `toJson`, 6 timeframes
     (1m/5m/15m/1h/4h/1D).
  2. **domain/SampleSeries.kt** — seeded LCG generator, 5 symbols × 900 1m bars, deterministic
     across devices (P2 swaps this for Binance; the generator is the demo feed until then).
  3. **domain/Indicators.kt** — SMA, EMA, RSI (Wilder), MACD (+signal+histogram), Bollinger,
     VWAP, ATR. Series stay `Double?`-aligned; NaN dropped at serialisation.
  4. **domain/ReplayEngine.kt** — arm/play/pause/step±1/speed/exit, visible-slice contract, plus
     `Aggregator` (1m→higher TF bucketing) used by the chart and by replay.
  5. **domain/PineLite.kt** — real recursive-descent parser for a documented Pine subset:
     `plot(expr)`, numbers, `+ - * / ( )`, unary minus, `close/open/high/low/volume`,
     `sma(x,n) ema(x,n) rsi(n) atr(n) vwap()`. Errors are surfaced to the UI, never swallowed.
  6. **ui/chart/ChartViewModel.kt** — single state holder: symbol, TF, indicators (persisted),
     saved scripts (persisted), replay state, crosshair candle; serialises candle + indicator
     JSON for the WebView; replay ticks on a 100 ms coroutine loop with dt-based advance.
  7. **ui/chart/ChartBars.kt / ChartSheets.kt / ChartScreen.kt** — TradingView-like chrome:
     top bar (symbol ▾ · TF chip · replay · ƒx indicators · scale), OHLC legend, replay bar
     (⏸/▶ ⏮ ⏭ 1x-2x-5x-10x ✕), TF row, bottom bar (chart / indicators / replay / LOG-LIN / FIT),
     and bottom sheets for symbols, timeframes, indicator catalog + saved scripts, script editor,
     and per-indicator settings (length + color).
  8. **assets/chart/chart.js** — panes support (`chart.addPane()` + `addSeries(..., paneIndex)`),
     fixed 0..100 range for RSI via `autoscaleInfoProvider`, indicator rebuild on theme/TF change,
     log-scale switch, crosshair events back to Kotlin.
  9. **bridge/** — `ChartWebView` queues JS until the page reports ready (nothing is lost if Kotlin
     pushes early); `JsBridge` is the only JS→Kotlin surface.
- Verification: VPS `./gradlew :app:testDebugUnitTest :app:assembleDebug` → **17 unit tests pass**
  (indicators hand-computed, aggregation buckets, replay stepping/pausing, Pine-lite parse + eval +
  rejection cases), APK built. CI now also runs the unit tests before assembling.
- Touched files: build.gradle.kts, app/build.gradle.kts, .github/workflows/android.yml,
  app/src/main/AndroidManifest.xml, app/src/main/java/com/ferdausfs/tradeview/** (MainActivity,
  TradeViewApp, domain/*, ui/chart/*, ui/theme/*, bridge/*),
  app/src/main/assets/chart/chart.js, app/src/test/java/**/DomainTest.kt (new), AGENT_LOG.md
- Commit hash: (this commit)
- Pending items: P2 live Binance data (replaces SampleSeries), P6 drawing tools, P7 forex, P9 polish.
- Notes for next agent: Pane API is `chart.addPane().paneIndex()` (NOT `getIndex()` — verified against
  the v5.3.0 typings). RSI-style panes use `autoscaleInfoProvider` for the fixed 0..100 window.
  Replay drives the chart by pushing a sliced `setData` (never `update` past the cursor).
