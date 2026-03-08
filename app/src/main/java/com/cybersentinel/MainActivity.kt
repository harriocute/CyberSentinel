package com.cybersentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cybersentinel.ui.navigation.CyberSentinelNavHost
import com.cybersentinel.ui.theme.BackgroundDark
import com.cybersentinel.ui.theme.CyberSentinelTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draws content edge-to-edge (status bar / navigation bar become transparent)
        enableEdgeToEdge()

        setContent {
            CyberSentinelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = BackgroundDark
                ) {
                    CyberSentinelNavHost()
                }
            }
        }
    }
}
