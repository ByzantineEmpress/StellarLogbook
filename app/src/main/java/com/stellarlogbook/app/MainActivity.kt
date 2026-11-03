package com.stellarlogbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stellarlogbook.app.core.container
import com.stellarlogbook.app.ui.nav.AppNavHost
import com.stellarlogbook.app.ui.theme.StellarLogbookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StellarLogbookTheme {
                AppNavHost(container())
            }
        }
    }
}
