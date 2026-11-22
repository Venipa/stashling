package net.venipa.stashling.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Hidden
import net.venipa.stashling.core.data.HiddenKind
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.nav.LocalNavigator
import net.venipa.stashling.nav.PerformerDetailRoute
import net.venipa.stashling.nav.SceneDetailRoute

/** Settings → Hidden: scenes and performers swiped away on Home, each with Unhide. */
@Composable
internal fun HiddenPage(
    hidden: List<Hidden>,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val nav = LocalNavigator.current
    SettingsPage(stringResource(R.string.settings_hidden), onBack) {
        if (hidden.isEmpty()) {
            EmptyState(BrowseIcons.VisibilityOff, stringResource(R.string.hidden_empty), body = stringResource(R.string.hidden_empty_body))
        }
        listOf(HiddenKind.SCENE to R.string.hidden_scenes, HiddenKind.PERFORMER to R.string.hidden_performers).forEach { (kind, title) ->
            val rows = hidden.filter { it.kind == kind }
            if (rows.isNotEmpty()) {
                SectionHeader(stringResource(title), Gutter)
                TonalGroup(Gutter) {
                    rows.forEach { h ->
                        HiddenRow(h, { nav.push(if (h.kind == HiddenKind.SCENE) SceneDetailRoute(h.stashId) else PerformerDetailRoute(h.stashId)) }) { scope.launch { container.hidden.unhide(h.kind, h.stashId) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenRow(
    h: Hidden,
    onOpen: () -> Unit,
    onUnhide: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scene = h.kind == HiddenKind.SCENE
    ListItem(
        headlineContent = { Text(h.name.ifEmpty { stringResource(R.string.browse_untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        // Group corners come from TonalGroup's clip; rows join at 4 dp like GroupRow.
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onOpen),
        leadingContent = {
            // Scenes 16:9, performers 2:3 portrait, as everywhere else in the app.
            StashImage(h.imageUrl, Modifier.width(if (scene) 72.dp else 40.dp).aspectRatio(if (scene) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(8.dp)), h.name)
        },
        trailingContent = { TextButton(onUnhide) { Text(stringResource(R.string.hidden_unhide)) } },
        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer),
    )
}
