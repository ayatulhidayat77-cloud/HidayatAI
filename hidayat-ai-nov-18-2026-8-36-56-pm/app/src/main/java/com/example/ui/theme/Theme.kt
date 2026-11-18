package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyanLight,
    onPrimary = SapphireDark,
    primaryContainer = SapphireNavy,
    onPrimaryContainer = Color.White,
    secondary = AmberGold,
    onSecondary = SapphireDark,
    secondaryContainer = Color(0xFF3B2F13),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldSuccess,
    onTertiary = SapphireDark,
    background = SapphireDeepSurface,
    onBackground = Color(0xFFF8FAFC),
    surface = SapphireCardDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = RoseAlert,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SapphireNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = SapphireDark,
    secondary = ElectricCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = SapphireNavy,
    tertiary = AmberGold,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = RoseAlert,
    onError = Color.White
)

@Composable
fun HidayatAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
