package com.example.cramrescue.ui.theme

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

// Emergency Cockpit Dark Color Scheme (Default)
private val DarkColorScheme = darkColorScheme(
    primary = AlertRed,
    onPrimary = EmergencyWhite,
    secondary = CautionAmber,
    onSecondary = DarkSlate,
    tertiary = SurvivalGreen,
    onTertiary = DarkSlate,
    background = DarkSlate,
    onBackground = EmergencyWhite,
    surface = DarkSurface,
    onSurface = EmergencyWhite,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = SubduedGray,
    error = AlertRed,
    onError = EmergencyWhite
)

// Light scheme (for system compatibility, but dark is default)
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CramRescueTheme(
    darkTheme: Boolean = true,  // Default to dark theme
    // Dynamic color disabled for consistent Emergency Cockpit aesthetic
    dynamicColor: Boolean = false,
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}