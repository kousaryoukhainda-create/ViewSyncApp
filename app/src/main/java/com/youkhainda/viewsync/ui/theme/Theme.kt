package com.youkhainda.viewsync.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),          // Indigo
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F46E5), // Darker indigo
    onPrimaryContainer = Color(0xFFE0E7FF),
    
    secondary = Color(0xFF8B5CF6),        // Violet
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF7C3AED),
    onSecondaryContainer = Color(0xFFEDE9FE),
    
    tertiary = Color(0xFF06B6D4),         // Cyan
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF0891B2),
    onTertiaryContainer = Color(0xFFCFFAFE),
    
    error = Color(0xFFFF6B6B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFEF5350),
    onErrorContainer = Color(0xFFFFEBEE),
    
    background = Color(0xFF0F172A),       // Very dark slate
    onBackground = Color(0xFFF1F5F9),
    
    surface = Color(0xFF1E293B),          // Dark slate
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6366F1),          // Indigo
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    
    secondary = Color(0xFF8B5CF6),        // Violet
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
    
    tertiary = Color(0xFF06B6D4),         // Cyan
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF164E63),
    
    error = Color(0xFFFF6B6B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFF8B0000),
    
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1F2937),
    
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
)

@Composable
fun ViewSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
