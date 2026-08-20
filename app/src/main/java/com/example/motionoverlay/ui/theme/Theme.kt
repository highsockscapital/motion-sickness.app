package com.example.motionoverlay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent, // #FF9E43 Soft Light Orange
    onPrimary = DarkCharcoal,
    primaryContainer = PrimaryAccent,
    onPrimaryContainer = DarkCharcoal,
    secondary = DarkCharcoal,
    onSecondary = CardSurface,
    secondaryContainer = SurfaceVariantWarm,
    onSecondaryContainer = DarkCharcoal,
    tertiary = PrimaryAccentDark,
    onTertiary = CardSurface,
    tertiaryContainer = SurfaceVariantWarm,
    onTertiaryContainer = DarkCharcoal,
    background = WindowBackground, // #FFFFF0 Ivory
    onBackground = DarkCharcoal, // #161610
    surface = CardSurface, // #FFFFFF Card / Dialog
    onSurface = DarkCharcoal,
    surfaceVariant = SurfaceVariantWarm, // #F5F5E6 item cards
    onSurfaceVariant = DarkCharcoal,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = CardSurface,
    surfaceContainerHighest = CardSurface,
    outline = StrokeBorder, // #161610 borders
    outlineVariant = StrokeBorder,
    error = DarkCharcoal,
    onError = CardSurface,
    scrim = DarkCharcoal
)

@Composable
fun MotionOverlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Force light theme per spec (ivory background) — darkTheme ignored
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = StatusBarColor.toArgb() // #FFFFF0 matches window
            window.navigationBarColor = WindowBackground.toArgb()
            // dark icons on light status bar
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
