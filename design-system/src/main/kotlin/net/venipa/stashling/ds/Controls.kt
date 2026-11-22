package net.venipa.stashling.ds

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** The one segmented control of the app (credential choice, theme, Queue|History); the style lives here only. */
@Composable
fun StashSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
    options.forEachIndexed { i, label ->
        SegmentedButton(
            selected = i == selected,
            onClick = { onSelect(i) },
            shape = SegmentedButtonDefaults.itemShape(i, options.size),
        ) { Text(label, maxLines = 1) }
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
