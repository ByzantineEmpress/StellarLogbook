package com.stellarlogbook.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Elite-ish cockpit palette; Material You dynamic color takes over on Android 12+.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    secondary = Color(0xFF4FC3F7),
    tertiary = Color(0xFFAED581)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFEF6C00),
    secondary = Color(0xFF0277BD),
    tertiary = Color(0xFF558B2F)
)

@Composable
fun StellarLogbookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
