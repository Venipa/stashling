package net.venipa.stashling.ds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.hypot

// Ported from medicatore ThemeReveal.kt.

/** Where the next theme change grows from, in root coordinates. Callers use [RevealAnchor] or [reveal]. */
class ThemeRevealState {
    internal var origin: Offset? = null

    /** Records [origin] (null = screen centre), then runs [change] (the settings write). */
    fun reveal(
        origin: Offset?,
        change: () -> Unit,
    ) {
        this.origin = origin
        change()
    }
}

val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealState() }

/** Put [modifier] on the control that changes the theme; wrap the write in [reveal]. */
class RevealAnchor internal constructor(
    private val state: ThemeRevealState,
) {
    /** The control's bounds in root coordinates (for a segmented control, pick the tapped segment from it). */
    var bounds: Rect = Rect.Zero
        private set

    val modifier: Modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }

    fun reveal(
        origin: Offset = bounds.center,
        change: () -> Unit,
    ) = state.reveal(origin, change)
}

@Composable
fun rememberRevealAnchor(): RevealAnchor {
    val state = LocalThemeReveal.current
    return remember(state) { RevealAnchor(state) }
}

/** What the screen actually looks like: OLED only counts in dark, so toggling it in light changes nothing. */
private data class Paint(
    val dark: Boolean,
    val oled: Boolean,
    val dynamic: Boolean,
)

/** The farthest corner of [size] from [origin]: the radius that covers the screen. */
internal fun revealRadius(
    origin: Offset,
    size: Size,
): Float = maxOf(
    hypot(origin.x, origin.y),
    hypot(size.width - origin.x, origin.y),
    hypot(origin.x, size.height - origin.y),
    hypot(size.width - origin.x, size.height - origin.y),
)

private const val REVEAL_MS = 450 // long1

/**
 * [StashlingTheme] that reveals a theme change with a circle. The theme drawn lags the settings: when the resolved look
 * changes (so an async DataStore write is simply waited for), the frame as last drawn is kept as a bitmap, the new
 * theme goes underneath, and the old frame is drawn outside a circle that grows from the control
 * ([ThemeRevealState.reveal]) to the farthest corner. System bar icons flip when it ends. Reduced motion swaps at once.
 * A second change mid-reveal finishes the first at once (about a frame of the middle theme), then grows again.
 */
@Composable
fun RevealingStashlingTheme(
    themeMode: ThemeMode,
    oledBlack: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val state = remember { ThemeRevealState() }
    val dark =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val target by rememberUpdatedState(Paint(dark, dark && oledBlack, dynamicColor))
    var shown by remember { mutableStateOf(target) }
    var barsDark by remember { mutableStateOf<Boolean?>(null) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(Size.Zero) }
    val progress = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalThemeReveal provides state) {
        StashlingTheme(
            themeMode = if (shown.dark) ThemeMode.DARK else ThemeMode.LIGHT,
            oledBlack = shown.oled,
            dynamicColor = shown.dynamic,
            systemBarsDark = barsDark,
        ) {
            val reduce = LocalReduceMotion.current
            LaunchedEffect(Unit) {
                // collectLatest: a newer change cancels the running reveal; its finally drops the old frame first.
                snapshotFlow { target }.collectLatest { next ->
                    val from = state.origin
                    state.origin = null
                    if (next == shown) return@collectLatest
                    if (reduce || size.width <= 0f || size.height <= 0f) {
                        shown = next
                        return@collectLatest
                    }
                    // The layer holds the content only, never the overlay: the old theme as last drawn.
                    val old =
                        try {
                            layer.toImageBitmap()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    if (old == null) {
                        shown = next
                        return@collectLatest
                    }
                    try {
                        origin = from ?: Offset(size.width / 2, size.height / 2)
                        radius = revealRadius(origin, size)
                        progress.snapTo(0f)
                        barsDark = shown.dark
                        snapshot = old
                        shown = next
                        progress.animateTo(1f, tween(REVEAL_MS, easing = EmphasizedDecelerate))
                    } finally {
                        snapshot = null
                        barsDark = null
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().onSizeChanged { size = it.toSize() }.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    val old = snapshot ?: return@drawWithContent
                    val circle = Path().apply { addOval(Rect(origin, progress.value * radius)) }
                    clipPath(circle, ClipOp.Difference) { drawImage(old) }
                },
            ) { content() }
        }
    }
}
