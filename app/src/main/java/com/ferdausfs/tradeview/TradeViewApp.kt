package com.ferdausfs.tradeview

import android.app.Application
import com.ferdausfs.tradeview.ui.chart.AppContextHolder

class TradeViewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
    }
}
