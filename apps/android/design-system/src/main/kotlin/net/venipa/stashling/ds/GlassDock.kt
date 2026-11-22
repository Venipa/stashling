package net.venipa.stashling.ds

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlin.math.PI
import kotlin.math.sin

data class DockTab(
    val label: String,
    val icon: ImageVector,
)

private val DockInset = 8.dp

/** Items on a dock row sit 8dp inside the 28dp dock: concentric 20dp, same as the tab items. */
val DockItemShape = concentricShape(GlassCorner, DockInset)
private val DockMargin = 16.dp
private val ShapeSize = 40.dp

// The icon shape sits 4dp below the item top in both states, so the indicator centre never moves vertically.
private val ShapeCenterY = 4.dp + ShapeSize / 2

/**
 * One nested-scroll connection for every tab root (on the NavDisplay). Scrolling down makes the dock compact
 * (icon-only tabs, one context line), any scroll up or a pull at the top expands it; it never hides.
 */
@Stable
class DockScrollState {
    var compact by mutableStateOf(false)

    val connection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < -4f) {
                    compact = true
                } else if (consumed.y > 4f || available.y > 4f) {
                    compact = false
                }
                return Offset.Zero
            }
        }
}

/**
 * Floating glass pill with 3 or 4 tabs. The active icon sits in an Expressive cookie shape that rolls to the new tab
 * on the spatial spring, morphing to a circle mid-way. [top] = rows attached above the tabs on the same glass (context controls,
 * downloading); the dock grows upward and rides above the IME. [onHeightChanged] reports the target footprint (nav bar included,
 * IME not), so list padding changes once per state instead of every frame of the size animation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassDock(
    glass: GlassState,
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onHeightChanged: (Int) -> Unit = {},
    top: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val haptic = LocalStashHaptics.current
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val margin = with(density) { DockMargin.roundToPx() }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                .padding(horizontal = DockMargin)
                .padding(bottom = DockMargin)
                .glass(glass, GlassRole.Dock)
                .animateContentSize(MaterialTheme.motionScheme.defaultEffectsSpec(), Alignment.BottomCenter)
                .onSizeChanged { onHeightChanged(it.height + margin + navBottom) }
                .selectableGroup(),
        ) {
            if (top != null) {
                // Same 8dp inset as the tab row, so the rows' concentric radius matches the tab items.
                Column(Modifier.fillMaxWidth().padding(start = DockInset, end = DockInset, top = DockInset), verticalArrangement = Arrangement.spacedBy(4.dp), content = top)
                Box(Modifier.fillMaxWidth().padding(top = DockInset).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)))
            }
            val spec = if (reduce) snap() else MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val pos = remember { Animatable(selected.toFloat()) }
            var from by remember { mutableFloatStateOf(selected.toFloat()) }
            LaunchedEffect(selected) {
                from = pos.value
                pos.animateTo(selected.toFloat(), spec)
            }
            val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
            val path = remember { Path() }
            val fill = MaterialTheme.colorScheme.secondaryContainer
            val last = (tabs.size - 1).toFloat()
            Row(
                Modifier
                    .padding(DockInset)
                    .drawBehind {
                        val gap = 4.dp.toPx()
                        val w = (size.width - gap * (tabs.size - 1)) / tabs.size
                        // The spring overshoots: clamp the position to the tab range and the travel fraction to 0..1.
                        val p = pos.value.coerceIn(0f, last)
                        val travel = selected - from
                        val frac = if (travel == 0f) 1f else ((p - from) / travel).coerceIn(0f, 1f)
                        val x = p * (w + gap) + w / 2
                        val cx = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                        val s = ShapeSize.toPx()
                        morph.toPath((1f - sin(frac * PI).toFloat()).coerceIn(0f, 1f), path)
                        // 9-sided cookie: 40 degrees per tab, so it rests in the same pose on every tab and visibly rolls between them.
                        translate(cx - s / 2, ShapeCenterY.toPx() - s / 2) {
                            rotate(p * 40f, Offset(s / 2, s / 2)) {
                                scale(s, s, Offset.Zero) { drawPath(path, fill) }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { i, tab ->
                    DockTabItem(tab, i == selected, compact, Modifier.weight(1f)) {
                        if (i != selected) haptic.tick()
                        onSelect(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTabItem(
    tab: DockTab,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = if (selected) cs.onSecondaryContainer else cs.onSurface.copy(alpha = 0.88f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dockPress")
    Column(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }.clip(RoundedCornerShape(concentric(GlassCorner, DockInset)))
            .selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Labels stay at full size in the compact dock too; only the context rows above the tabs collapse.
        Box(Modifier.size(ShapeSize), contentAlignment = Alignment.Center) { Icon(tab.icon, null, Modifier.height(22.dp), tint = color) }
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
