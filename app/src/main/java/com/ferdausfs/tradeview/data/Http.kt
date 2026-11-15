package com.ferdausfs.tradeview.data

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** One shared client: connection reuse matters on mobile radios. */
object Http {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
