package net.venipa.stashling.ds

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    // Equal fixed widths and one shared height: selecting never shifts the layout. The selection is one indicator
    // that slides on the expressive spatial spring; buttons stay transparent on top. Concentric: track = button + pad.
    val buttonShape = RoundedCornerShape(SegmentRadius)
    val shapes = ToggleButtonShapes(buttonShape, RoundedCornerShape(SegmentRadius - 4.dp), buttonShape)
    val position by animateFloatAsState(selected.toFloat(), MaterialTheme.motionScheme.fastSpatialSpec(), label = "segment")
    val count = options.size.coerceAtLeast(1)
    Box(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(cs.surfaceContainerHigh, RoundedCornerShape(SegmentRadius + SegmentPad))
            .padding(SegmentPad),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val gap = SegmentGap.roundToPx()
                    val w = (constraints.maxWidth - gap * (count - 1)) / count
                    val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative((position * (w + gap)).roundToInt(), 0)
                    }
                }.background(cs.primary, buttonShape),
        )
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            options.forEachIndexed { i, label ->
                ToggleButton(
                    checked = i == selected,
                    onCheckedChange = { if (it) onSelect(i) },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                    shapes = shapes,
                    colors =
                    ToggleButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = cs.onSurfaceVariant,
                        checkedContainerColor = Color.Transparent,
                        checkedContentColor = cs.onPrimary,
                    ),
                ) { Text(label, textAlign = TextAlign.Center) }
            }
        }
    }
}

private val SegmentRadius = 16.dp
private val SegmentPad = 4.dp
private val SegmentGap = 2.dp

/** Filled text fields in the expressive theme: shapes.large on every corner, no underline (it can't follow round corners);
 * focus and error show through the container colour and the label. Use with every TextField: `shape = fieldShape(), colors = fieldColors()`. */
@Composable
fun fieldShape(): Shape = MaterialTheme.shapes.large

@Composable
fun fieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceContainerHighest,
        unfocusedContainerColor = cs.surfaceContainerHigh,
        disabledContainerColor = cs.surfaceContainerHigh,
        errorContainerColor = cs.errorContainer,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}

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
