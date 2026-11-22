package net.venipa.stashling.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one segmented control of the app (credential choice, theme, Queue|History): M3 Expressive ButtonGroup of ToggleButtons
 * (press-width morph) in a surfaceContainerHigh pill track with 4dp padding; the buttons are pills, so the track is too
 * (concentric: track radius = button radius + 4dp). Labels wrap at large font scale: heights are minimums only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Fixed equal widths and one shared height (no width morph, no per-button height): selecting never shifts the layout.
    // Concentric: track corner = button corner + pad.
    val buttonShape = RoundedCornerShape(SegmentRadius)
    val shapes = ToggleButtonShapes(buttonShape, RoundedCornerShape(SegmentRadius - 4.dp), buttonShape)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(cs.surfaceContainerHigh, RoundedCornerShape(SegmentRadius + SegmentPad))
            .padding(SegmentPad)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { if (it) onSelect(i) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                shapes = shapes,
                colors = ToggleButtonDefaults.colors(containerColor = Color.Transparent, contentColor = cs.onSurfaceVariant),
            ) { Text(label, textAlign = TextAlign.Center) }
        }
    }
}

private val SegmentRadius = 16.dp
private val SegmentPad = 4.dp

/** Cards / rows switch for list top bars; the caller persists the mode (Settings.sceneListMode). */
@Composable
fun ViewModeToggle(
    rows: Boolean,
    onChange: (rows: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = IconButton({ onChange(!rows) }, modifier) {
    Icon(
        if (rows) StashlingIcons.ViewCards else StashlingIcons.ViewList,
        stringResource(if (rows) R.string.ds_view_cards else R.string.ds_view_rows),
    )
}

/** Loading: M3 Expressive indicators (experimental; the opt-in stays inside the design-system). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) {
    if (contained) ContainedLoadingIndicator(modifier) else LoadingIndicator(modifier)
}
