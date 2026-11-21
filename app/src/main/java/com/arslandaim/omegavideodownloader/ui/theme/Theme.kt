/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * Curated duo-tone theme color combinations.
 * Each option pairs a Primary color with a complementary Secondary color.
 */
enum class ThemeColorPreset(
    val id: String,
    val displayName: String,
    val colorPairLabel: String,
    val primary: Color,
    val secondary: Color
) {
    ELECTRIC_OCEAN(
        id = "ELECTRIC_OCEAN",
        displayName = "Electric Ocean",
        colorPairLabel = "Electric Blue + Cyan",
        primary = Color(0xFF2979FF),
        secondary = Color(0xFF00E5FF)
    ),
    SUNSET_EMBER(
        id = "SUNSET_EMBER",
        displayName = "Sunset Ember",
        colorPairLabel = "Crimson + Amber Gold",
        primary = Color(0xFFFF3D57),
        secondary = Color(0xFFFFB300)
    ),
    EMERALD_MINT(
        id = "EMERALD_MINT",
        displayName = "Emerald Mint",
        colorPairLabel = "Emerald + Aqua Teal",
        primary = Color(0xFF00C853),
        secondary = Color(0xFF1DE9B6)
    ),
    ROYAL_AMETHYST(
        id = "ROYAL_AMETHYST",
        displayName = "Royal Amethyst",
        colorPairLabel = "Violet + Rose Pink",
        primary = Color(0xFF7C4DFF),
        secondary = Color(0xFFFF4081)
    ),
    COBALT_GOLD(
        id = "COBALT_GOLD",
        displayName = "Cobalt Gold",
        colorPairLabel = "Cobalt Blue + Warm Gold",
        primary = Color(0xFF1877F2),
        secondary = Color(0xFFFFC107)
    ),
    INDIGO_CORAL(
        id = "INDIGO_CORAL",
        displayName = "Indigo Coral",
        colorPairLabel = "Indigo + Coral Orange",
        primary = Color(0xFF536DFE),
        secondary = Color(0xFFFF6E40)
    );

    companion object {
        fun fromId(id: String?): ThemeColorPreset =
            entries.firstOrNull { it.id == id } ?: ELECTRIC_OCEAN
    }
}

@Immutable
data class OmegaPalette(
    val isDark: Boolean,
    val preset: ThemeColorPreset,
    val primary: Color,
    val secondary: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val card: Color,
    val cardElevated: Color,
    val border: Color,
    val borderBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gold: Color,
    val success: Color,
    val danger: Color,
    val watchlistRose: Color
) {
    val accentBrush: Brush
        get() = Brush.linearGradient(listOf(primary, secondary))

    val backgroundBrush: Brush
        get() = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
}

private fun omegaPaletteFor(isDark: Boolean, preset: ThemeColorPreset): OmegaPalette {
    return if (isDark) {
        OmegaPalette(
            isDark = true,
            preset = preset,
            primary = preset.primary,
            secondary = preset.secondary,
            backgroundTop = Color(0xFF0A0D16),
            backgroundBottom = Color(0xFF04050A),
            surface = Color(0xFF0D111C),
            card = Color(0xFF121827),
            cardElevated = Color(0xFF192034),
            border = Color.White.copy(alpha = 0.09f),
            borderBright = Color.White.copy(alpha = 0.16f),
            textPrimary = Color(0xFFF5F7FF),
            textSecondary = Color(0xFF9AA4C4),
            gold = Color(0xFFFFB300),
            success = Color(0xFF00E676),
            danger = Color(0xFFFF5252),
            watchlistRose = Color(0xFFFF2D55)
        )
    } else {
        OmegaPalette(
            isDark = false,
            preset = preset,
            primary = preset.primary,
            secondary = preset.secondary,
            backgroundTop = Color(0xFFF3F6FB),
            backgroundBottom = Color(0xFFE7ECF4),
            surface = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            cardElevated = Color(0xFFF0F4FA),
            border = Color.Black.copy(alpha = 0.08f),
            borderBright = Color.Black.copy(alpha = 0.15f),
            textPrimary = Color(0xFF111524),
            textSecondary = Color(0xFF5A637D),
            gold = Color(0xFFE69D00),
            success = Color(0xFF00A86B),
            danger = Color(0xFFD32F2F),
            watchlistRose = Color(0xFFE91E63)
        )
    }
}

val LocalOmegaColors = staticCompositionLocalOf {
    omegaPaletteFor(isDark = true, preset = ThemeColorPreset.ELECTRIC_OCEAN)
}

object OmegaTheme {
    val colors: OmegaPalette
        @Composable
        get() = LocalOmegaColors.current
}

/** Solid, fully opaque surface for top/bottom navigation bars and cards. */
val ColorScheme.glassBackground: Color
    @Composable
    get() = if (surface.luminance() < 0.5f) Color(0xFF0D111C) else Color(0xFFFFFFFF)

/** Slightly elevated solid surface for docked mini-players above the bottom bar. */
val ColorScheme.miniPlayerBackground: Color
    @Composable
    get() = if (surface.luminance() < 0.5f) Color(0xFF161C2E) else Color(0xFFFFFFFF)

val ColorScheme.glassBorder: Color
    @Composable
    get() = if (surface.luminance() < 0.5f) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.09f)

@Composable
fun OmegaVideoDownloaderTheme(
    themeMode: String = "System",
    themeColorId: String = ThemeColorPreset.ELECTRIC_OCEAN.id,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val preset = ThemeColorPreset.fromId(themeColorId)
    val palette = omegaPaletteFor(isDark = darkTheme, preset = preset)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = preset.primary,
            secondary = preset.secondary,
            tertiary = preset.secondary,
            background = palette.backgroundBottom,
            surface = palette.surface,
            surfaceVariant = palette.cardElevated,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary
        )
        else -> lightColorScheme(
            primary = preset.primary,
            secondary = preset.secondary,
            tertiary = preset.secondary,
            background = palette.backgroundTop,
            surface = palette.surface,
            surfaceVariant = palette.cardElevated,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary
        )
    }

    CompositionLocalProvider(LocalOmegaColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
