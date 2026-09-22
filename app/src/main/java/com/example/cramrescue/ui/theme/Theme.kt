package com.example.cramrescue.ui.theme

import android.app.Activity
import android.os.Build
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

// Dark "Emergency Cockpit" scheme, preserved from the original design for
// future dark-mode support (see requirements.md §5.4). Not the default.
private val DarkColorScheme = darkColorScheme(
    primary = CodeRed,
    onPrimary = OnCodeRed,
    secondary = CodeYellow,
    onSecondary = OnCodeYellow,
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
    error = CodeRed,
    onError = OnCodeRed
)

@Composable
fun CramRescueTheme(
    // Light is the primary MVP visual direction. Dark mode is preserved
    // (see DarkColorScheme above) but is not the default while the product
    // is still on its light, Anki-inspired study surface.
    darkTheme: Boolean = false,
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
