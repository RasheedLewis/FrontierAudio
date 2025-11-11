package com.example.frontieraudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val FrontierDarkColorScheme = darkColorScheme(
    primary = FrontierOrange,
    onPrimary = Color.White,
    primaryContainer = FrontierOrangePressed,
    onPrimaryContainer = Color.White,
    secondary = FrontierAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = FrontierSurfaceLight,
    onSecondaryContainer = FrontierTextPrimary,
    tertiary = FrontierSuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = FrontierSurfaceLight,
    onTertiaryContainer = FrontierTextPrimary,
    background = FrontierSurfaceDark,
    onBackground = FrontierTextPrimary,
    surface = FrontierSurfaceMedium,
    onSurface = FrontierTextPrimary,
    surfaceVariant = FrontierSurfaceLight,
    onSurfaceVariant = FrontierTextSecondary,
    outline = FrontierBorder,
    outlineVariant = FrontierBorder,
    inverseSurface = FrontierTextPrimary,
    inverseOnSurface = FrontierSurfaceDark,
    error = FrontierErrorRed,
    onError = Color.White,
    errorContainer = FrontierErrorRed,
    scrim = Color.Black
)

@Composable
fun FrontierAudioTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFrontierSpacing provides FrontierSpacing()) {
        MaterialTheme(
            colorScheme = FrontierDarkColorScheme,
            typography = FrontierTypography,
            shapes = FrontierShapes,
            content = content
        )
    }
}