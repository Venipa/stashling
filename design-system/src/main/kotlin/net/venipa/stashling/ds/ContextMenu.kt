package net.venipa.stashling.ds

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One row of a context menu; [destructive] rows are drawn in the error colour (put them last). */
data class ContextMenuItem(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

/** Where an item's bounds and drawing are captured for the lifted copy; see [Modifier.contextMenuAnchor]. */
@Stable
class ContextMenuAnchor(internal val layer: GraphicsLayer) {
    internal var bounds by mutableStateOf(Rect.Zero)

    /** X of the last press inside the item (item coordinates); picks the menu's aligned edge and its scale origin. */
    internal var pressX = 0f

    /** Set by the host while this item's menu is open: the same press then drags onto a row (root coordinates). */
    internal var open = false
    internal var drag by mutableStateOf<Offset?>(null)

    /** Bumped when that press is released; [releasedAt] = where (null = no position). */
    internal var releaseTick by mutableIntStateOf(0)
    internal var releasedAt: Offset? = null
}

@Composable
fun rememberContextMenuAnchor(): ContextMenuAnchor {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ContextMenuAnchor(layer) }
}

/** Records the item's drawing into [anchor] (and draws it as usual) and tracks its bounds in the root. */
fun Modifier.contextMenuAnchor(anchor: ContextMenuAnchor): Modifier = onGloballyPositioned { anchor.bounds = it.boundsInRoot() }
    // Watches the press position on the initial pass without consuming, so clicks, long-press and swipe work as before.
    .pointerInput(anchor) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            anchor.pressX = down.position.x
            // Press-and-hold, drag onto a row, release to run it; the item keeps the pointer for the whole gesture.
            while (true) {
                val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                val root = anchor.bounds.topLeft + ch.position
                if (anchor.open) anchor.drag = root
                if (!ch.pressed) {
                    if (anchor.open) {
                        anchor.releasedAt = root
                        anchor.releaseTick++
                    }
                    break
                }
            }
            anchor.drag = null
        }
    }
    .drawWithContent {
        anchor.layer.record { this@drawWithContent.drawContent() }
        drawLayer(anchor.layer)
    }

/** An open menu: [beside] = start/end of the item (performer portraits), else above/below (scene cards and rows). */
class ContextMenuRequest(
    val anchor: ContextMenuAnchor,
    val shape: Shape,
    val beside: Boolean,
    val items: @Composable () -> List<ContextMenuItem>,
)

@Stable
class ContextMenuState {
    var request by mutableStateOf<ContextMenuRequest?>(null)
        internal set

    internal var closing by mutableStateOf(false)

    fun open(r: ContextMenuRequest) {
        closing = false
        request = r
    }

    /** Runs the reverse animation, then clears. */
    fun close() {
        if (request != null) closing = true
    }
}

val LocalContextMenu = staticCompositionLocalOf<ContextMenuState?> { null }

private val MenuWidth = 240.dp
private val MenuRadius = 20.dp
private val MenuPad = 4.dp
private val Gap = 8.dp

/**
 * Root host: [content] blurs (Android 12+) under a dim scrim while a menu is open, the item is redrawn lifted
 * (scale 1.03 + shadow) above it, and the tonal menu opens from the item's edge, above/below or beside, always on screen.
 * Tap outside or (predictive) back closes it with the reverse animation.
 */
@Composable
fun ContextMenuHost(
    state: ContextMenuState,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val req = state.request
    val haptic = LocalStashHaptics.current
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val scope = rememberCoroutineScope()
    LaunchedEffect(req, state.closing) {
        if (req == null) return@LaunchedEffect
        if (state.closing) {
            progress.animateTo(0f, effects)
            state.request = null
            state.closing = false
        } else {
            haptic.longPress()
            progress.animateTo(1f, spatial)
        }
    }
    // Clamp the spring: alpha, blur and dim never leave 0..1; the scale may overshoot a little.
    val p = progress.value.coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (req != null && p > 0f) Modifier.blur((16 * p).dp) else Modifier)) { content() }
        if (req != null) {
            PredictiveBackHandler(enabled = !state.closing) { events ->
                try {
                    events.collect { e -> progress.snapTo(1f - e.progress * 0.5f) }
                    state.close()
                } catch (e: CancellationException) {
                    scope.launch { progress.animateTo(1f, effects) }
                    throw e
                }
            }
            val cs = MaterialTheme.colorScheme
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = p }
                    .background(cs.scrim.copy(alpha = 0.32f))
                    .clickable(remember { MutableInteractionSource() }, null) { state.close() },
            )
            val b = req.anchor.bounds
            val density = LocalDensity.current
            // The lifted item: the recorded drawing at its own bounds, over the blur.
            Canvas(
                Modifier
                    .graphicsLayer {
                        translationX = b.left
                        translationY = b.top
                        val s = 1f + 0.03f * progress.value
                        scaleX = s
                        scaleY = s
                        shadowElevation = 12.dp.toPx() * p
                        shape = req.shape
                        clip = true
                    }
                    .then(with(density) { Modifier.size(b.width.toDp(), b.height.toDp()) }),
            ) { drawLayer(req.anchor.layer) }
            DisposableEffect(req) {
                req.anchor.open = true
                onDispose {
                    req.anchor.open = false
                    req.anchor.drag = null
                }
            }
            MenuLayout(req, b, p, progress.value) { state.close() }
        }
    }
}

@Composable
private fun MenuLayout(
    req: ContextMenuRequest,
    b: Rect,
    alpha: Float,
    scale: Float,
    onDone: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val gap = with(density) { Gap.roundToPx() }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    Layout(
        content = {
            Menu(
                req.items(),
                req.anchor,
                Modifier.graphicsLayer {
                    this.alpha = alpha
                    val s = 0.85f + 0.15f * scale
                    scaleX = s
                    scaleY = s
                    transformOrigin = origin
                },
                onDone,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, c ->
        val m = measurables.first().measure(Constraints(maxWidth = MenuWidth.roundToPx().coerceAtMost(c.maxWidth), maxHeight = c.maxHeight))
        val top = insets.getTop(this)
        val bottom = c.maxHeight - insets.getBottom(this)
        val left = insets.getLeft(this, layoutDirection) + gap
        val right = c.maxWidth - insets.getRight(this, layoutDirection) - gap
        layout(c.maxWidth, c.maxHeight) {
            var x: Int
            var y: Int
            val besideFits = req.beside && (b.right + gap + m.width <= right || b.left - gap - m.width >= left)
            if (besideFits) {
                // Beside: the end side first (start in RTL is the other screen side), else the other one.
                val endX = if (rtl) b.left.roundToInt() - gap - m.width else b.right.roundToInt() + gap
                val startX = if (rtl) b.right.roundToInt() + gap else b.left.roundToInt() - gap - m.width
                x = if (endX >= left && endX + m.width <= right) endX else startX
                y = b.top.roundToInt()
                origin = TransformOrigin(if (x > b.left) 0f else 1f, 0f)
            } else {
                // Above or below: whichever side has room (below first). A press on the start half aligns the menu to the
                // item's start edge, the end half to its end edge (RTL mirrors); the scale origin sits at the press x.
                val startHalf = if (rtl) req.anchor.pressX > b.width / 2 else req.anchor.pressX < b.width / 2
                val alignLeft = startHalf != rtl
                x = if (alignLeft) b.left.roundToInt() else b.right.roundToInt() - m.width
                val below = b.bottom.roundToInt() + gap
                val above = b.top.roundToInt() - gap - m.height
                y = if (below + m.height <= bottom || above < top) below else above
            }
            x = x.coerceIn(left, (right - m.width).coerceAtLeast(left))
            y = y.coerceIn(top + gap, (bottom - gap - m.height).coerceAtLeast(top + gap))
            if (!besideFits) origin = TransformOrigin(((b.left + req.anchor.pressX - x) / m.width).coerceIn(0f, 1f), if (y >= b.bottom) 0f else 1f)
            m.place(x, y)
        }
    }
}

@Composable
private fun Menu(
    items: List<ContextMenuItem>,
    anchor: ContextMenuAnchor,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalStashHaptics.current
    val rows = remember { mutableStateMapOf<Int, Rect>() }
    val drag = anchor.drag
    val hover = drag?.let { d -> rows.entries.firstOrNull { it.value.contains(d) }?.key }
    LaunchedEffect(hover) { if (hover != null) haptic.tick() }
    val tick = anchor.releaseTick
    val startTick = remember { tick }
    LaunchedEffect(tick) {
        if (tick == startTick) return@LaunchedEffect
        // Released over a row: run it; elsewhere the menu stays open for taps.
        val at = anchor.releasedAt ?: return@LaunchedEffect
        val i = rows.entries.firstOrNull { it.value.contains(at) }?.key ?: return@LaunchedEffect
        items.getOrNull(i)?.let {
            haptic.confirm()
            onDone()
            it.onClick()
        }
    }
    Column(
        modifier.width(MenuWidth).background(cs.surfaceContainerHigh, RoundedCornerShape(MenuRadius)).padding(MenuPad),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Rows concentric to the menu: outer radius minus the padding at the ends, 4 dp joins.
        val outer = concentric(MenuRadius, MenuPad)
        items.forEachIndexed { i, a ->
            val top = if (i == 0) outer else 4.dp
            val bottom = if (i == items.lastIndex) outer else 4.dp
            val color = if (a.destructive) cs.error else cs.onSurface
            Row(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rows[i] = it.boundsInRoot() }
                    .clip(RoundedCornerShape(top, top, bottom, bottom))
                    .background(if (hover == i) cs.onSurface.copy(alpha = 0.1f) else cs.onSurface.copy(alpha = 0f))
                    .clickable {
                        haptic.confirm()
                        onDone()
                        a.onClick()
                    }.padding(horizontal = Space.M, vertical = Space.M),
                horizontalArrangement = Arrangement.spacedBy(Space.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(a.icon, null, tint = color)
                // A label can change once the state loads (Add → Search again): crossfade in the same one-line slot.
                Crossfade(a.label, Modifier.weight(1f), label = "menuLabel") { t ->
                    Text(t, color = color, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
