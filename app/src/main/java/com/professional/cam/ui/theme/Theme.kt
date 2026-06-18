package com.professional.cam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ProCamColorScheme = darkColorScheme(
    primary = ProfessionalOrange,
    onPrimary = TextPrimary,
    primaryContainer = ProfessionalOrangeDark,
    onPrimaryContainer = TextPrimary,
    secondary = ProfessionalOrangeLight,
    onSecondary = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorColor,
    onError = TextPrimary,
    outline = ControlInactive
)

@Composable
fun ProCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProCamColorScheme,
        content = content
    )
}
