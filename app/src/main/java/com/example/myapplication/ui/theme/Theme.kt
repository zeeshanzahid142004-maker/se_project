package com.example.myapplication.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

public object SB {
    val bg         = Color(0xFF0D1117)   // near-black navy
    val surface    = Color(0xFF161B22)   // card surface
    val surfaceAlt = Color(0xFF1C2333)   // slightly lighter card
    val teal       = Color(0xFF2DD4BF)   // primary accent (teal/cyan)
    val tealDim    = Color(0xFF1A8A7A)
    val red        = Color(0xFFE53E3E)   // CTA red
    val redDark    = Color(0xFFC53030)
    val white      = Color(0xFFF0F6FC)
    val muted      = Color(0xFF8B949E)
    val border     = Color(0xFF30363D)
    val scanLine   = Color(0xFF2DD4BF)


    val boxBody      = Color(0xFFC88C43)
    val boxBodyShade = Color(0xFF8F5926)
    val boxFlap      = Color(0xFFD59B4C)
    val boxFlapUnder = Color(0xFF8B5A25)
    val boxBorder    = Color(0xFF764816)
}
