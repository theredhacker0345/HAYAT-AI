package com.hayat.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = White,
    secondary = Gray,
    tertiary = LightGray,
    background = Black,
    surface = DarkGray,
    onPrimary = Black,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = White,
    onSurface = White,
    error = Red
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    secondary = Gray,
    tertiary = DarkGray,
    background = White,
    surface = LightGray,
    onPrimary = White,
    onSecondary = Black,
    onTertiary = White,
    onBackground = Black,
    onSurface = Black,
    error = Red
)

@Composable
fun HayatTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentLocale = getCurrentLocale()
    val isRtl = currentLocale.language == "ar"
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
    }

    val fontFamily = if (isRtl) CairoFont else FontFamily.Default
    val rtlTypography = Typography.toRtlTypography(fontFamily)
    val finalTypography = if (isRtl) rtlTypography else Typography
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = finalTypography,
            content = content
        )
    }
}

@Composable
fun getCurrentLocale(): java.util.Locale {
    return LocalView.current.context.resources.configuration.locales.get(0)
}
