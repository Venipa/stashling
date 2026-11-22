package net.venipa.stashling.ds

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/** EdgeTop / EdgeBottom: full-width progressive blur bands over scrolling content, strongest at the screen edge. */
enum class GlassRole { Dock, Panel, Button, EdgeTop, EdgeBottom }

/** Wraps HazeState plus the runtime glass on/off switch; Glass.kt is the only file that imports Haze. */
class GlassState internal constructor() {
    internal val haze = HazeState()

    // Written by rememberGlassState; read inside the Haze style blocks, so a flip redraws without recomposition.
    internal var enabled by mutableStateOf(true)

    // Theme primary, written by rememberGlassState; the dock gets a minimal tint of it, the rest stays neutral.
    internal var accent by mutableStateOf(Color.Transparent)

    internal val darkStyles = GlassRole.entries.associateWith { role -> style(role, dark = true) }
    internal val lightStyles = GlassRole.entries.associateWith { role -> style(role, dark = false) }

    private fun style(
        role: GlassRole,
        dark: Boolean,
    ) = HazeBlurStyle {
        // Neutral #030303 scrim over the blur; alphas pass 7:1 (0.78) / 3:1 icons (0.62) on a white image.
        val (radius, scrim) =
            when (role) {
                // Keep the blur visible: 0.62 keeps white text ~6:1 (AA) even over a white image.
                GlassRole.Dock, GlassRole.Panel -> 24.dp to if (dark) 0.62f else 0.62f

                // Plain blur on the buttons, not glass (no noise, no hairline). 0.58 keeps a white icon over 3:1 on a white image.
                GlassRole.Button -> 24.dp to if (dark) 0.58f else 0.68f

                // Subtle: max 18dp blur and a very light dark wash for legibility.
                GlassRole.EdgeTop, GlassRole.EdgeBottom -> 18.dp to if (dark) 0.22f else 0.3f
            }
        if (role == GlassRole.EdgeTop) progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
        if (role == GlassRole.EdgeBottom) progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
        blurEnabled(enabled)
        blurRadius(radius)
        // App background is pure black: without an opaque base, blurred sparse content (text) is mostly transparent and the sharp original shows through.
        backgroundColor(if (dark) Color.Black else Color.White)
        noiseFactor(if (role == GlassRole.Dock || role == GlassRole.Panel) 0.04f else 0f)
        colorEffects(
            listOf(
                HazeColorEffect.colorFilter(ColorFilter.colorMatrix(frost(role, dark))),
                HazeColorEffect.tint((if (dark) Scrim else LightScrim).copy(alpha = scrim)),
            ) + if (role == GlassRole.Dock || role == GlassRole.Panel) listOf(HazeColorEffect.tint(accent.copy(alpha = 0.08f))) else emptyList(),
        )
        // Tonal fallback: surfaceContainer at 94% (buttons stay a flat scrim).
        fallbackColorEffect(
            HazeColorEffect.tint(
                if (role == GlassRole.Button) {
                    (if (dark) Scrim else LightScrim).copy(alpha = 0.78f)
                } else {
                    (if (dark) Tonal else LightTonal).copy(alpha = 0.94f)
                },
            ),
        )
    }
}

/**
 * Dock/Panel glass samples only the tiles (the page behind is no haze source), so over a tile the blur carried the tile's chroma and
 * luminance and over empty page nothing: a hard seam at the tile edge. Frosting drains chroma and pulls luminance 50% toward the page
 * colour before the scrim, so both halves land close together. Buttons and edge bands keep the plain 1.5x saturation.
 */
private fun frost(
    role: GlassRole,
    dark: Boolean,
): ColorMatrix {
    if (role != GlassRole.Dock && role != GlassRole.Panel) return ColorMatrix().apply { setToSaturation(1.5f) }
    val k = 0.5f
    val off = if (dark) 0f else (1 - k) * 255f
    val m = ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, off, 0f, k, 0f, 0f, off, 0f, 0f, k, 0f, off, 0f, 0f, 0f, 1f, 0f))
    m *= ColorMatrix().apply { setToSaturation(0.4f) } // saturation first, then the pull
    return m
}

private val Scrim = Color(0xFF030303)
private val Tonal = Color(0xFF211F26)

// Light glass: white scrim (dark text on top); alphas pass 4.5:1 for onSurface #1C1917 over a BLACK image.
private val LightScrim = Color(0xFFFFFFFF)
private val LightTonal = Color(0xFFF2ECF4)

@Composable
fun rememberGlassState(): GlassState {
    val context = LocalContext.current
    val state = remember { GlassState() }
    val accent = MaterialTheme.colorScheme.secondaryContainer
    SideEffect { state.accent = accent }
    DisposableEffect(context) {
        val power = context.getSystemService(PowerManager::class.java)
        val lowRam = context.getSystemService(ActivityManager::class.java).isLowRamDevice
        val update = {
            state.enabled = !lowRam && !power.isPowerSaveMode && power.currentThermalStatus < PowerManager.THERMAL_STATUS_MODERATE
        }
        update()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = update()
            }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        val thermal = PowerManager.OnThermalStatusChangedListener { update() }
        power.addThermalStatusListener(thermal)
        onDispose {
            context.unregisterReceiver(receiver)
            power.removeThermalStatusListener(thermal)
        }
    }
    return state
}

/** Content that floating glass samples. */
fun Modifier.glassSource(state: GlassState): Modifier = hazeSource(state.haze)

/** Outer radius of the dock and the glass sheets. */
val GlassCorner = 28.dp

@Composable
fun Modifier.glass(
    state: GlassState,
    role: GlassRole,
): Modifier {
    val shape: Shape =
        when (role) {
            GlassRole.Dock -> RoundedCornerShape(GlassCorner)
            GlassRole.Panel -> RoundedCornerShape(topStart = GlassCorner, topEnd = GlassCorner)
            GlassRole.Button -> CircleShape
            GlassRole.EdgeTop, GlassRole.EdgeBottom -> RectangleShape
        }
    val dark = LocalDarkTheme.current
    val styles = if (dark) state.darkStyles else state.lightStyles
    return clip(shape)
        .blockPointerPassThrough()
        .hazeBlur(HazeInput.Sources(state.haze), styles.getValue(role))
        .then(if (role != GlassRole.Dock) Modifier else Modifier.border(1.dp, (if (dark) Color.White else Color.Black).copy(alpha = 0.10f), shape))
}

/**
 * Makes a floating layer opaque to touch: being hit-tested stops taps, long-presses and drags from reaching siblings drawn under it,
 * while its own children still handle them first. Only the down is consumed (so ancestors' tap detectors skip it); consuming moves
 * would cancel the children's clicks and scrolls, whose detectors check for consumed moves. No semantics, so TalkBack is unaffected.
 */
fun Modifier.blockPointerPassThrough(): Modifier = pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }

/** The app's glass state for floating pieces that are not given one (loading pill); null = tonal fallback. */
val LocalGlass = androidx.compose.runtime.staticCompositionLocalOf<GlassState?> { null }
