package com.ferdausfs.tradeview.data

import com.ferdausfs.tradeview.domain.model.Candle
import com.ferdausfs.tradeview.domain.model.Feed
import com.ferdausfs.tradeview.domain.model.Instrument
import com.ferdausfs.tradeview.domain.model.Market
import com.ferdausfs.tradeview.domain.model.Timeframe

/**
 * The built-in instrument list. Crypto rows are real Binance spot pairs; forex rows are the
 * majors and liquid crosses (served by TwelveData when a key is set, demo otherwise).
 */
object Instruments {

    val crypto = listOf(
        Instrument("crypto:BTCUSDT", "BTCUSDT", "Bitcoin / Tether", Market.CRYPTO, Feed.BINANCE, 96_250.0, 1.0, 2),
        Instrument("crypto:ETHUSDT", "ETHUSDT", "Ethereum / Tether", Market.CRYPTO, Feed.BINANCE, 3_180.0, 1.35, 2),
        Instrument("crypto:SOLUSDT", "SOLUSDT", "Solana / Tether", Market.CRYPTO, Feed.BINANCE, 184.5, 1.9, 3),
        Instrument("crypto:BNBUSDT", "BNBUSDT", "BNB / Tether", Market.CRYPTO, Feed.BINANCE, 612.0, 1.2, 2),
        Instrument("crypto:XRPUSDT", "XRPUSDT", "XRP / Tether", Market.CRYPTO, Feed.BINANCE, 2.41, 2.1, 4),
        Instrument("crypto:DOGEUSDT", "DOGEUSDT", "Dogecoin / Tether", Market.CRYPTO, Feed.BINANCE, 0.162, 2.6, 5),
        Instrument("crypto:ADAUSDT", "ADAUSDT", "Cardano / Tether", Market.CRYPTO, Feed.BINANCE, 0.52, 2.2, 4),
        Instrument("crypto:AVAXUSDT", "AVAXUSDT", "Avalanche / Tether", Market.CRYPTO, Feed.BINANCE, 24.8, 2.3, 3),
        Instrument("crypto:LINKUSDT", "LINKUSDT", "Chainlink / Tether", Market.CRYPTO, Feed.BINANCE, 14.2, 2.0, 3),
        Instrument("crypto:LTCUSDT", "LTCUSDT", "Litecoin / Tether", Market.CRYPTO, Feed.BINANCE, 71.5, 1.6, 2),
        Instrument("crypto:TONUSDT", "TONUSDT", "Toncoin / Tether", Market.CRYPTO, Feed.BINANCE, 3.1, 2.4, 4),
        Instrument("crypto:TRXUSDT", "TRXUSDT", "TRON / Tether", Market.CRYPTO, Feed.BINANCE, 0.155, 2.0, 5),
    )

    val forex = listOf(
        Instrument("fx:EURUSD", "EUR/USD", "Euro / US Dollar", Market.FOREX, Feed.YAHOO, 1.0865, 0.35, 5),
        Instrument("fx:GBPUSD", "GBP/USD", "Pound / US Dollar", Market.FOREX, Feed.YAHOO, 1.2640, 0.40, 5),
        Instrument("fx:USDJPY", "USD/JPY", "US Dollar / Yen", Market.FOREX, Feed.YAHOO, 154.20, 0.45, 3),
        Instrument("fx:AUDUSD", "AUD/USD", "Aussie / US Dollar", Market.FOREX, Feed.YAHOO, 0.6585, 0.50, 5),
        Instrument("fx:USDCAD", "USD/CAD", "US Dollar / Loonie", Market.FOREX, Feed.YAHOO, 1.4020, 0.40, 5),
        Instrument("fx:USDCHF", "USD/CHF", "US Dollar / Franc", Market.FOREX, Feed.YAHOO, 0.8810, 0.40, 5),
        Instrument("fx:NZDUSD", "NZD/USD", "Kiwi / US Dollar", Market.FOREX, Feed.YAHOO, 0.5920, 0.55, 5),
        Instrument("fx:EURGBP", "EUR/GBP", "Euro / Pound", Market.FOREX, Feed.YAHOO, 0.8595, 0.30, 5),
        Instrument("fx:EURJPY", "EUR/JPY", "Euro / Yen", Market.FOREX, Feed.YAHOO, 167.50, 0.50, 3),
        Instrument("fx:GBPJPY", "GBP/JPY", "Pound / Yen", Market.FOREX, Feed.YAHOO, 194.90, 0.65, 3),
    )

    val all: List<Instrument> get() = crypto + forex

    fun find(id: String): Instrument? = all.firstOrNull { it.id == id }

    fun default(): Instrument = crypto.first()
}
