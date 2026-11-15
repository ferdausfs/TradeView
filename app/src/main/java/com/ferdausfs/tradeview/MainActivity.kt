package com.ferdausfs.tradeview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ferdausfs.tradeview.ui.chart.ChartScreen
import com.ferdausfs.tradeview.ui.theme.TradeViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TradeViewTheme {
                ChartScreen()
            }
        }
    }
}