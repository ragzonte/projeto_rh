package com.ragzonte.satelitecheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ragzonte.satelitecheck.ui.SatelliteScreen
import com.ragzonte.satelitecheck.ui.theme.SateliteCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SateliteCheckTheme {
                SatelliteScreen()
            }
        }
    }
}
