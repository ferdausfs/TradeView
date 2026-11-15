# TradeView — Agent Log

> Continuity file. Entries are **prepended** (newest first) and never deleted.

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
