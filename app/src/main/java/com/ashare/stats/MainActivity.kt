package com.ashare.stats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ashare.stats.ui.MarketStatsRoute
import com.ashare.stats.ui.theme.AShareStatsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AShareStatsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MarketStatsRoute()
                }
            }
        }
    }
}
