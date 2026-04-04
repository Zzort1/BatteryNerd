package com.evagames.batterynerd.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = NerdGreen,
    secondary = ColorTokens.secondaryDark,
    tertiary = ColorTokens.tertiaryDark,
    background = DarkSurface,
    surface = DarkCard,
    primaryContainer = DeepTeal
)

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    secondary = ColorTokens.secondaryLight,
    tertiary = ColorTokens.tertiaryLight
)

@Composable
fun BatteryNerdTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val secondaryDark = androidx.compose.ui.graphics.Color(0xFF99CCFF)
    val tertiaryDark = androidx.compose.ui.graphics.Color(0xFFFFB870)
    val secondaryLight = androidx.compose.ui.graphics.Color(0xFF355C7D)
    val tertiaryLight = androidx.compose.ui.graphics.Color(0xFF9A3412)
}
