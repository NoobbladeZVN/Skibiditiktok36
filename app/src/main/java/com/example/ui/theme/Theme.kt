package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonSoftContainer,
    onPrimaryContainer = OnCrimsonSoftContainer,
    secondary = CrimsonRedDark,
    onSecondary = Color.White,
    secondaryContainer = WarmPaperVariant,
    onSecondaryContainer = InkPrimary,
    tertiary = WarmGold,
    onTertiary = Color.White,
    tertiaryContainer = WarmGoldContainer,
    onTertiaryContainer = OnWarmGoldContainer,
    background = WarmPaperBackground,
    onBackground = InkPrimary,
    surface = WarmPaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmPaperContainer,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLow = WarmPaperSurface,
    surfaceContainer = WarmPaperContainer,
    surfaceContainerHigh = WarmPaperVariant,
    outline = WarmOutline,
    outlineVariant = WarmOutline.copy(alpha = 0.6f)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCrimsonPrimary,
    onPrimary = Color(0xFF470007),
    primaryContainer = DarkCrimsonContainer,
    onPrimaryContainer = DarkOnCrimsonContainer,
    secondary = DarkCrimsonPrimary,
    onSecondary = Color(0xFF470007),
    secondaryContainer = DarkInkSurfaceHigh,
    onSecondaryContainer = DarkInkText,
    tertiary = DarkGoldTertiary,
    onTertiary = Color(0xFF3E2600),
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = Color(0xFFFFE08A),
    background = DarkInkBackground,
    onBackground = DarkInkText,
    surface = DarkInkSurface,
    onSurface = DarkInkText,
    surfaceVariant = DarkInkSurfaceHigh,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLow = DarkInkSurface,
    surfaceContainer = DarkInkSurfaceHigh,
    surfaceContainerHigh = Color(0xFF362A2A),
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.6f)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HocNaoToTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
