# TradeView — Agent Log

> Continuity file. Entries are **prepended** (newest first) and never deleted.

## Session — 2026-11-15 — P3/P4/P5/P8-lite shipped: CI green (43746448959), APK delivered as v0.2.0
- Agent/Tool: OpenCode · branch `main`
- **NOT a new feature — a delivery/log entry** for the previous P3/P4/P5/P8-lite work. (Correction of
  a mistake made moments ago: this entry was first written *inside* the previous session's entry,
  which violates prepend-only; reverted, then written properly as its own entry here.)
- Verification: CI run **43746448959 → success** on commit `b6a2da9` — steps: Set up job / Checkout /
  JDK 17 / Android SDK / Gradle / **Run unit tests** / **Build debug APK** / Upload test report /
  **Upload APK**, all green. Unit tests: **17 pass** (same result re-verified on VPS).
- Shipped APK: downloaded CI artifact `tradeview-debug-apk` and copied to the phone as
  `/storage/emulated/0/Download/TradeView-v0.2.0-debug.apk` (19,223,616 bytes debug build).
  `gh run download` from the phone is slow (~2–4 min) — a 300 s foreground shell timed out once
  at the very end; rerun in background to completion, then verified size after copy.
- Docs-only commits skip CI (`paths-ignore` covers `**.md` and `NOTICE`).
- Pending items: user on-device confirmation of the v0.2.0 APK, then P2 (live Binance REST +
  WebSocket replacing `SampleSeries`), then P6 drawings, P7 forex, P9 polish.
- Notes for next agent: nothing new — all technical notes in the entry below still apply.

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

## Session — 2026-11-15 — P1 sample candles visible on launch
- Agent/Tool: OpenCode · branch `main` · commit 5d0e629 · CI run **43738440855 → success**
- Feature (one only): make the chart actually show candles on first launch (P1 acceptance)
- What changed: (1) `app/src/main/assets/chart/sample-candles.json` — 480 deterministic 1-minute
  candles (seeded PRNG, BTC-USD-like ~96k prices, 2026-11-10 00:00 UTC start) so every build/device
  shows the identical series; (2) `chart.js` fetches that file and calls `chartApi.setData` on load
  (fetch works inside the WebViewAssetLoader origin; P2 will replace this with Kotlin-pushed data);
  (3) `ChartScreen` restructured to a Column: top bar + chart `Box` with `navigationBarsPadding`,
  so the chart no longer extends under the gesture bar.
- Verification: VPS `./gradlew :app:assembleDebug` → SUCCESS; GitHub Actions run 43738440855 →
  success; artifact downloaded and unzipped — `assets/chart/sample-candles.json` (46,327 B) present.
  APK installed-ready at `/storage/emulated/0/Download/TradeView-v0.1.0-debug.apk` (12,025,866 B).
- Touched files: app/src/main/assets/chart/sample-candles.json (new), app/src/main/assets/chart/chart.js,
  app/src/main/java/com/ferdausfs/tradeview/ui/screen/ChartScreen.kt
- Pending items: P2 — Binance REST history + WebSocket live candle through a Kotlin adapter.
- Notes for next agent: the JS `fetch('sample-candles.json')` is P1-only scaffolding; in P2 the Kotlin
  side must call `setData`/`update` and the fetch block should be removed. Docs-only commits no longer
  trigger CI (paths-ignore).


## Session — 2026-11-15 — CI fix (APK artifact live)
- Agent/Tool: OpenCode · branch `main` · commit b3d4291
- Feature (one only): CI-only fix so the APK actually builds on GitHub Actions
- What changed: `.github/workflows/android.yml` — `android-actions/setup-android@v3` was failing with
  `Failed to find package 'tools'` + exit 1. The action's default `packages` input still requests the
  retired `tools` package. Now pinned explicitly:
  `platform-tools platforms;android-36 build-tools;36.0.0`, licenses accepted, license log silenced.
- Verification: GitHub Actions run **43737177428 → success** (every step green, including "Upload APK").
  Artifact `tradeview-debug-apk` downloaded (11,996,784 bytes, sha256 f39441ae…61c38) and unzipped:
  contains AndroidManifest, classes.dex, assets/chart/{index.html, chart.js, lightweight-charts 5.3.0, NOTICE}.
  APK copied to /storage/emulated/0/Download/TradeView-v0.1.0-debug.apk on the phone.
- Touched files: .github/workflows/android.yml, AGENT_LOG.md
- Pending items: P2 crypto data (Binance REST history + WebSocket live candle).
- Notes for next agent: CI is proven end-to-end; push to `main` and watch `gh run list`.

## Session — 2026-11-15 — P0 + P1 (skeleton + CI + chart in WebView)
- Agent/Tool: OpenCode · branch `main`
- Feature (one only): P0 skeleton & CI, plus P1 chart-in-WebView (built together so the first
  APK is actually inspectable on device rather than an empty screen)
- What changed:
  - Gradle/AGP: AGP 8.13.2, Kotlin 2.4.21, Gradle 8.14.7 (wrapper committed), JDK 17, compile/target SDK 36, minSdk 26.
  - Compose BOM pinned to **2026.03.01** (ui 1.10.6 / material3 1.4.0). Reason (verified, not guessed):
    BOM 2026.09.00 pulls `ui-android:1.12.1` which declares `minAgpVersion=9.1.0` + requires compileSdk 37.
    Verified each AAR's `aar-metadata.properties`: ui 1.10.6 → minCompileSdk 35; core-ktx 1.17.0 → 36;
    activity-compose 1.12.4 → 36; lifecycle 2.10.0 → 35; webkit 1.18.0 → 33. No dependency demands AGP 9.
  - `ChartWebView` loads bundled assets only via `WebViewAssetLoader` on
    `https://appassets.androidplatform.net/assets/chart/index.html` — no CDN at runtime, file access off,
    external navigation blocked.
  - lightweight-charts **5.3.0** (standalone production build, 196 KB) committed into
    `app/src/main/assets/chart/`, with `NOTICE` + `LICENSE-lightweight-charts.txt` (Apache-2.0 attribution).
  - `JsBridge` is the only JS→Kotlin surface (onChartReady / onCrosshairMove / onVisibleRangeChange).
  - `chart.js` exposes `window.chartApi` = setData / update / setTheme / fitContent, with volume histogram
    on its own scale and dark+light token sets matching the spec palette.
  - CI: `.github/workflows/android.yml` — JDK 17 temurin + setup-android + gradle/actions, builds
    `:app:assembleDebug`, uploads `tradeview-debug-apk` artifact.
- Verification: real clean build on the VPS (`./gradlew clean :app:assembleDebug`) → BUILD SUCCESSFUL,
  `app-debug.apk` 11,996,784 bytes. Build environment there mirrors CI (no `local.properties`, SDK from env).
- Touched files: settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradle/wrapper/*,
  app/build.gradle.kts, app/proguard-rules.pro, app/src/main/AndroidManifest.xml,
  app/src/main/java/com/ferdausfs/tradeview/{MainActivity.kt,bridge/*.kt,ui/theme/Theme.kt,ui/screen/ChartScreen.kt},
  app/src/main/res/** (strings, colors, themes, adaptive icon),
  app/src/main/assets/chart/{index.html,chart.js,lightweight-charts-5.3.0.standalone.production.js,NOTICE,LICENSE-lightweight-charts.txt},
  .github/workflows/android.yml, AGENT_LOG.md
- Commit hash: (this commit)
- Pending items: P2 crypto data (Binance REST + WS), then P3 symbols/timeframes/watchlist.
- Notes for next agent: Hilt/Room are deliberately NOT wired yet — they arrive with P2/P3 when there is
  something to persist. Do not bump Compose BOM past 2026.03.01 without raising AGP to 9.x and compileSdk 37.
