package com.qiell.doomscrollblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Biru tenang = boleh dipakai, hijau = aman, kuning = perhatian, merah = terkunci.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7F7),
    onPrimaryContainer = Color(0xFF0B2A44),
    secondary = Color(0xFF4F7A4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBD7),
    onSecondaryContainer = Color(0xFF12290F),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6BF),
    onTertiaryContainer = Color(0xFF2D1A00),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE3E8EE),
    onSurfaceVariant = Color(0xFF42494F),
    errorContainer = Color(0xFFFBE0DC),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3E5),
    onPrimary = Color(0xFF0B2A44),
    primaryContainer = Color(0xFF1F4466),
    onPrimaryContainer = Color(0xFFD7E7F7),
    secondary = Color(0xFFA8C69F),
    onSecondary = Color(0xFF12290F),
    secondaryContainer = Color(0xFF2F4A2B),
    onSecondaryContainer = Color(0xFFDCEBD7),
    tertiary = Color(0xFFF2BE63),
    onTertiary = Color(0xFF2D1A00),
    tertiaryContainer = Color(0xFF5E3D00),
    onTertiaryContainer = Color(0xFFFFE6BF),
    background = Color(0xFF10222E),
    onBackground = Color(0xFFE3E8EC),
    surface = Color(0xFF1B303E),
    onSurface = Color(0xFFE3E8EC),
    surfaceVariant = Color(0xFF2A404F),
    onSurfaceVariant = Color(0xFFBFC9D1),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DoomscrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
