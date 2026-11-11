package com.deskcal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun DeskCalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeskCalBackground,
            surface = DeskCalSurface,
            surfaceVariant = DeskCalSurfaceVariant,
            primary = DeskCalPrimary,
            primaryContainer = DeskCalPrimaryContainer,
            onPrimaryContainer = DeskCalOnPrimaryContainer,
            onSurface = DeskCalOnSurface,
            onSurfaceVariant = DeskCalOnSurfaceVariant,
            error = DeskCalError,
        ),
        typography = DeskCalTypography,
        content = content,
    )
}
