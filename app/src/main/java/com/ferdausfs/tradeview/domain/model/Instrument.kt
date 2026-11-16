package com.ferdausfs.tradeview.domain.model

/**
 * A tradable instrument and where its data comes from. One model for crypto and forex so the
 * browser, the chart and the live feed all speak the same language.
 */
enum class Market(val label: String) {
    CRYPTO("Crypto"),
    FOREX("Forex"),
}

/** Where candles come from. */
enum class Feed {
    /** Binance spot REST + WebSocket (crypto only). */
    BINANCE,

    /** Yahoo Finance chart API — free intraday forex, no key (forex default). */
    YAHOO,

    /** TwelveData REST when an API key is configured (optional forex upgrade). */
    TWELVEDATA,

    /** Deterministic seeded generator; used until real data is available. */
    DEMO,
}

data class Instrument(
    val id: String,          // stable key, e.g. "crypto:BTCUSDT", "fx:EURUSD"
    val symbol: String,      // display + upstream symbol, e.g. "BTCUSDT", "EUR/USD"
    val name: String,        // human label, e.g. "Bitcoin", "Euro / US Dollar"
    val market: Market,
    val feed: Feed,
    val basePrice: Double,   // seed price for the demo feed
    val volatility: Double,  // relative roughness for the demo feed
    val decimals: Int,       // display precision
) {
    val isCrypto: Boolean get() = market == Market.CRYPTO
}

fun Instrument.priceDecimals(): Int = decimals

fun formatPrice(value: Double, decimals: Int): String =
    String.format(java.util.Locale.US, "%,.${decimals}f", value)
