package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

private val EyeGuardDarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF38BDF8),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0F172A),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF0284C7),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE0F2FE),
    secondary = androidx.compose.ui.graphics.Color(0xFF34D399),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF064E3B),
    error = androidx.compose.ui.graphics.Color(0xFFEF4444),
    onError = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFF030712),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
    surface = androidx.compose.ui.graphics.Color(0xFF111827),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2937),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9CA3AF)
)

@Composable
fun Theme_EyeGuard(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EyeGuardDarkColorScheme,
        typography = Typography,
        content = content
    )
}

