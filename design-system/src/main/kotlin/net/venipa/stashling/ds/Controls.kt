package net.venipa.stashling.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    Box(modifier.fillMaxWidth().background(cs.surfaceContainerHigh, CircleShape).padding(4.dp)) {
        ButtonGroup(overflowIndicator = {}, Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val scope = this
            options.forEachIndexed { i, label ->
                customItem(
                    buttonGroupContent = {
                        val source = remember { MutableInteractionSource() }
                        ToggleButton(
                            checked = i == selected,
                            onCheckedChange = { if (it) onSelect(i) },
                            modifier = with(scope) { Modifier.weight(1f).animateWidth(source) },
                            colors = ToggleButtonDefaults.colors(containerColor = Color.Transparent, contentColor = cs.onSurfaceVariant),
                            interactionSource = source,
                        ) { Text(label, textAlign = TextAlign.Center) }
                    },
                    menuContent = {},
                )
            }
        }
    }
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
