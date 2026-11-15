# TradeView

Native Android charting app (crypto + forex). Kotlin + Jetpack Compose shell, chart rendered by
`lightweight-charts` (Apache-2.0) inside a WebView loaded from bundled `assets/`.

## Build
Pushes to `main` and `v*` branches trigger the `Android APK` GitHub Action, which produces a
`tradeview-debug-apk` artifact (unsigned debug APK).

## Attribution
This product includes software developed by TradingView (lightweight-charts, Apache License 2.0).
See `NOTICE` and `app/src/main/assets/chart/NOTICE`.
