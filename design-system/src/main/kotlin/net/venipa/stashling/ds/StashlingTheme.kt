package net.venipa.stashling.ds

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Mirrors core's Settings ThemeMode; the app maps one to the other (design-system does not depend on :core). */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Stub: M3 Expressive theme with dynamic colour (minSdk 31 always has it) or the baseline schemes.
 * [oledBlack] turns background and surfaces pure black whenever the dark scheme is active, system dark included.
 */
@Composable
fun StashlingTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    oledBlack: Boolean = false,
    dynamicColor: Boolean = true,
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
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    val scheme = if (dark && oledBlack) base.oled() else base
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
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

private fun ColorScheme.oled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
)
