# TradeView — Agent Log

> Continuity file. Entries are **prepended** (newest first) and never deleted.

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
