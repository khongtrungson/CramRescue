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

// Light, neutral color scheme — the default CramRescue study surface.
// Semantic ER Triage colors (CodeRed/CodeYellow/CodeGreen) are intentionally
// NOT used for primary/secondary here: they stay reserved for badges and
// priority indicators, not the app's everyday chrome.
private val LightColorScheme = lightColorScheme(
    primary = AccentPrimaryLight,
    onPrimary = OnAccentPrimaryLight,
    background = AppBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = AppSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = AppOutlineLight,
    outlineVariant = AppOutlineLight,
    secondaryContainer = AccentContainerLight,
    onSecondaryContainer = OnAccentContainerLight,
    tertiary = CodeGreen,
    onTertiary = OnCodeGreen,
    error = CodeRed,
    onError = OnCodeRed
)

// Dark scheme (requirements.md §5.4), used when the system is in dark mode. Mirrors the light
// scheme's rules: neutral accent for buttons/chips — triage colors stay reserved for meaning.
private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimaryDark,
    onPrimary = OnAccentPrimaryDark,
    tertiary = CodeGreen,
    onTertiary = OnCodeGreen,
    background = AppBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = AppSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = AppOutlineDark,
    outlineVariant = AppOutlineDark,
    secondaryContainer = AccentContainerDark,
    onSecondaryContainer = OnAccentContainerDark,
    error = ErrorOnDark,
    onError = OnAccentPrimaryDark
)

@Composable
fun CramRescueTheme(
    // M15: follows the system setting — dark for late-night study when the phone is in dark
    // mode (requirements.md §5.4), the light Anki-inspired surface otherwise.
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled for a consistent CramRescue palette.
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
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
