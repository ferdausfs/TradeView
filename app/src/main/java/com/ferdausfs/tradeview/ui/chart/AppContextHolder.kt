package com.ferdausfs.tradeview.ui.chart

import android.content.Context

object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context = appContext ?: error("AppContextHolder.init(context) must be called in Application")
}
