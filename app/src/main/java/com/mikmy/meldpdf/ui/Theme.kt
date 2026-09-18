package com.mikmy.meldpdf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2563EB)
private val BrandDark = Color(0xFF3B82F6)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE7FF),
    onPrimaryContainer = Color(0xFF0B203F),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF1F6),
)

private val Dark = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A66),
    onPrimaryContainer = Color(0xFFDBE7FF),
    background = Color(0xFF0B0B14),
    surface = Color(0xFF14141F),
    surfaceVariant = Color(0xFF20202C),
)

@Composable
fun MeldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
