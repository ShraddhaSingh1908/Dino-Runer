package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DinoDarkColorScheme =
  darkColorScheme(
    primary = JungleJadeBright,
    onPrimary = CanopyNight,
    primaryContainer = JungleJade,
    onPrimaryContainer = Sandstone,
    secondary = TreasureGold,
    onSecondary = CanopyNight,
    secondaryContainer = Color(0xFF47370A),
    onSecondaryContainer = TreasureGoldSoft,
    tertiary = ShieldCyan,
    onTertiary = CanopyNight,
    background = CanopyNight,
    onBackground = Sandstone,
    surface = CanopySurface,
    onSurface = Sandstone,
    surfaceVariant = CanopyCard,
    onSurfaceVariant = SandstoneMuted,
    error = MagmaCoral,
    onError = Color.White,
  )

@Composable
fun DinoEscapeTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = DinoDarkColorScheme, typography = DinoTypography, content = content)
}
