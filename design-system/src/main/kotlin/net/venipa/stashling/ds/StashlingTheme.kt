package net.venipa.stashling.ds

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Mirrors core's Settings ThemeMode; the app maps one to the other (design-system does not depend on :core). */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** True while the dark scheme is active (glass and scrims read it). */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** True when the system animator scale is 0: motion tokens drop slides and scales, keep fades. */
val LocalReduceMotion = staticCompositionLocalOf { false }

// Seed: logo violet #2A2470 to #6A45C8, amber accent (the logo's middle card).
private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF6A45C8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF220A5C),
        secondary = Color(0xFF625B7D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6DEFF),
        onSecondaryContainer = Color(0xFF1E1836),
        tertiary = Color(0xFF7A5900),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDEA3),
        onTertiaryContainer = Color(0xFF261900),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454E),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F1FA),
        surfaceContainer = Color(0xFFF2ECF4),
        surfaceContainerHigh = Color(0xFFECE6EE),
        surfaceContainerHighest = Color(0xFFE6E0E9),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCBC4CF),
    )

private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFFCFBDFF),
        onPrimary = Color(0xFF331A7A),
        primaryContainer = Color(0xFF4B2DA6),
        onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2E6),
        onSecondary = Color(0xFF332D4C),
        secondaryContainer = Color(0xFF4A4465),
        onSecondaryContainer = Color(0xFFE6DEFF),
        tertiary = Color(0xFFF5BF4E),
        onTertiary = Color(0xFF402D00),
        tertiaryContainer = Color(0xFF5C4300),
        onTertiaryContainer = Color(0xFFFFDEA3),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454E),
        onSurfaceVariant = Color(0xFFCBC4CF),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF948F99),
        outlineVariant = Color(0xFF49454E),
    )

/**
 * M3 Expressive theme: the Stashling violet/amber scheme, or Material You when [dynamicColor] is on.
 * [oledBlack] turns background and surfaces pure black whenever the dark scheme is active, system dark included.
 */
@Composable
fun StashlingTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    oledBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val context = LocalContext.current
    val base =
        when {
            dynamicColor && dark -> dynamicDarkColorScheme(context)
            dynamicColor -> dynamicLightColorScheme(context)
            dark -> DarkScheme
            else -> LightScheme
        }
    val scheme = if (dark && oledBlack) base.oled() else base
    val reduce = remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    // System bar icons follow the app's theme, not the system's.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) {
        // MaterialTheme does not reset the content colour; without this plain Text outside a Surface keeps the wrong ink.
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalDarkTheme provides dark,
            LocalReduceMotion provides reduce,
            content = content,
        )
    }
}

private fun ColorScheme.oled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLow = Color(0xFF0B0A0E),
    surfaceContainer = Color(0xFF131116),
)
