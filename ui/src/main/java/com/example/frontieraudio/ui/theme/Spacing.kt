package com.example.frontieraudio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FrontierSpacing(
    val tiny: Dp = 2.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val section: Dp = 32.dp
)

val LocalFrontierSpacing = staticCompositionLocalOf { FrontierSpacing() }

object FrontierTheme {
    val spacing: FrontierSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalFrontierSpacing.current
}

