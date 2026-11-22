package net.venipa.stashling.ds

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DockTab(
    val label: String,
    val icon: ImageVector,
)

private val DockInset = 8.dp

/**
 * Floating glass pill with 3 or 4 tabs (wallcrad GlassDock without the search panel). One selection pill slides under the
 * active tab; place it last in a full-screen Box. [onHeightChanged] reports the full footprint for a scroll-blur band.
 * [top] = rows attached above the tabs on the same glass (wallcrad context row: Home controls, downloading); the dock grows upward.
 */
@Composable
fun GlassDock(
    glass: GlassState,
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {},
    top: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .onSizeChanged { onHeightChanged(it.height) }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .glass(glass, GlassRole.Dock)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec(), Alignment.BottomCenter)
                .selectableGroup(),
        ) {
            if (top != null) {
                // Same 8dp inset as the tab row, so the rows' concentric radius matches the tab pills.
                Column(Modifier.fillMaxWidth().padding(start = DockInset, end = DockInset, top = DockInset), verticalArrangement = Arrangement.spacedBy(4.dp), content = top)
                Box(Modifier.fillMaxWidth().padding(top = DockInset).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)))
            }
            // One indicator pill: its two edges chase the selected tab on different springs, so it stretches while it slides.
            val spatial = if (reduce) snap() else spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            val slow = if (reduce) snap() else spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            var last by remember { mutableIntStateOf(selected) }
            SideEffect { last = selected }
            val forward = selected > last
            val leading by animateFloatAsState(selected.toFloat(), if (forward) slow else spatial, label = "dockLead")
            val trailing by animateFloatAsState(selected + 1f, if (forward) spatial else slow, label = "dockTrail")
            val fill = MaterialTheme.colorScheme.secondaryContainer
            Row(
                Modifier
                    .padding(DockInset)
                    .drawBehind {
                        val gap = 4.dp.toPx()
                        val step = (size.width - gap * (tabs.size - 1)) / tabs.size + gap
                        val l = leading * step
                        drawRoundRect(fill, Offset(l, 0f), Size(trailing * step - gap - l, size.height), CornerRadius(concentric(GlassCorner, DockInset).toPx()))
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { i, tab ->
                    DockTabItem(tab, i == selected, Modifier.weight(1f)) {
                        if (i != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.88f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dockPress")
    Column(
        modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }.clip(RoundedCornerShape(concentric(GlassCorner, DockInset)))
            .selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, null, Modifier.height(22.dp), tint = color)
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
