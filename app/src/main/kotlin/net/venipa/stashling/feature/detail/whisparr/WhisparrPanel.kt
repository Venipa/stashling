package net.venipa.stashling.feature.detail.whisparr

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.core.whisparr.QueueItem
import net.venipa.stashling.core.whisparr.Release
import net.venipa.stashling.core.whisparr.WhisparrScene
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.HeroCardRadius
import net.venipa.stashling.ds.Loading
import net.venipa.stashling.ds.PillTone
import net.venipa.stashling.ds.SheetRadius
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.StatusPill
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.groupItemShape

/** Inline tonal card under the scene hero. Renders nothing while Whisparr is not connected. */
@Composable
fun WhisparrPanel(
    stashId: String,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: WhisparrPanelViewModel = viewModel(key = "whisparr-$stashId") { WhisparrPanelViewModel(stashId, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val searchOnAdd by vm.searchOnAdd.collectAsStateWithLifecycle()
    val noRelease by vm.noRelease.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Polls only while visible.
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.poll() } }

    val s = state
    if (s is PanelState.Unavailable) return
    // hero: 28dp radius, 20dp padding, so nested buttons get a concentric 8dp (a 16/16 card would leave 0).
    TonalCard(modifier, tone = if (s is PanelState.Failed || s is PanelState.Error) CardTone.Error else CardTone.Plain, hero = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.whisparr_title), style = MaterialTheme.typography.titleMedium)
            Pill(s)
        }
        when (s) {
            PanelState.Unavailable -> Unit

            PanelState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Loading(Modifier.size(48.dp)) }

            is PanelState.NotInLibrary -> {
                if (s.excluded) Text(stringResource(R.string.whisparr_excluded), style = MaterialTheme.typography.bodyMedium)
                val add = PanelButton(stringResource(R.string.whisparr_add), { vm.add(AddMode.REQUEST) }, tonal = searchOnAdd)
                val addSearch = PanelButton(stringResource(R.string.whisparr_add_and_search), { vm.add(AddMode.QUEUE) }, tonal = !searchOnAdd, icon = StashlingIcons.Search)
                val pick = PanelButton(stringResource(R.string.whisparr_interactive_search), { vm.add(AddMode.PICK) }, tonal = true)
                // Settings "search on add" decides which one is the filled primary; both always shown.
                ButtonGroup(if (searchOnAdd) listOf(addSearch, add, pick) else listOf(add, addSearch, pick), enabled = !busy)
            }

            is PanelState.Missing -> {
                if (noRelease) Text(stringResource(R.string.whisparr_no_release), style = MaterialTheme.typography.bodyMedium)
                MonitoredRow(s.scene, enabled = !busy) { vm.setMonitored(s.scene, it) }
                SearchActions(s.scene, busy, vm)
            }

            is PanelState.Searching -> {
                Text(stringResource(R.string.whisparr_searching_body), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            is PanelState.InQueue -> QueueProgress(s.item)

            is PanelState.Failed -> {
                s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                SearchActions(s.scene, busy, vm)
            }

            is PanelState.Downloaded -> {
                val context = LocalContext.current
                val file = s.scene.movieFile
                val size = file?.size ?: s.scene.sizeOnDisk
                val line = listOfNotNull(file?.quality?.quality?.name, size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
            }

            is PanelState.Error -> {
                Text(s.message.ifBlank { stringResource(R.string.whisparr_error) }, style = MaterialTheme.typography.bodyMedium)
                ButtonGroup(listOf(PanelButton(stringResource(R.string.whisparr_retry), vm::retry, tonal = true)), enabled = !busy)
            }
        }
    }
    releases?.let { ReleaseSheet(it, onGrab = vm::grab, onDismiss = vm::closeReleases) }
}

@Composable
private fun Pill(s: PanelState) {
    val (text, tone) =
        when (s) {
            is PanelState.NotInLibrary -> stringResource(R.string.whisparr_not_in_library) to PillTone.Neutral
            is PanelState.Missing -> stringResource(if (s.scene.monitored) R.string.whisparr_missing else R.string.whisparr_unmonitored) to PillTone.Warn
            is PanelState.Searching -> stringResource(R.string.whisparr_searching) to PillTone.Neutral
            is PanelState.InQueue -> queueLabel(s.item) to if (s.item.trackedDownloadStatus == "warning") PillTone.Warn else PillTone.Ok
            is PanelState.Failed -> stringResource(R.string.whisparr_failed) to PillTone.Error
            is PanelState.Downloaded -> stringResource(R.string.whisparr_downloaded) to PillTone.Ok
            is PanelState.Error -> stringResource(R.string.whisparr_error) to PillTone.Error
            else -> return
        }
    StatusPill(text, tone)
}

/** Whisparr's own queue words, capitalised ("importPending" → "Import pending"). */
internal fun queueLabel(item: QueueItem): String {
    val raw = item.trackedDownloadState?.takeIf { it != "downloading" } ?: item.status
    return raw.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
internal fun QueueProgress(item: QueueItem) {
    val context = LocalContext.current
    LinearProgressIndicator({ item.progress.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
    val done = (item.size - item.sizeleft).toLong().coerceAtLeast(0)
    val line =
        stringResource(
            R.string.whisparr_progress,
            (item.progress * 100).toInt(),
            Formatter.formatShortFileSize(context, done),
            Formatter.formatShortFileSize(context, item.size.toLong()),
        )
    val eta =
        item.estimatedCompletionTime?.let {
            DateUtils.getRelativeTimeSpanString(it.toEpochMilliseconds(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        }
    Text(listOfNotNull(line, eta).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    item.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MonitoredRow(
    scene: WhisparrScene,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.whisparr_monitored), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    Switch(scene.monitored, onChange, enabled = enabled)
}

@Composable
private fun SearchActions(
    scene: WhisparrScene,
    busy: Boolean,
    vm: WhisparrPanelViewModel,
) {
    ButtonGroup(
        listOf(
            PanelButton(stringResource(R.string.whisparr_search), { vm.search(scene) }, icon = StashlingIcons.Search),
            PanelButton(stringResource(R.string.whisparr_interactive_search), { vm.openReleases(scene) }, tonal = true),
        ),
        enabled = !busy,
    )
}

private class PanelButton(
    val text: String,
    val onClick: () -> Unit,
    val tonal: Boolean = false,
    val icon: ImageVector? = null,
)

/** Connected button stack inside the hero card: 20dp in from a 28dp corner, joins 2dp apart (concentric). */
@Composable
private fun ButtonGroup(
    buttons: List<PanelButton>,
    enabled: Boolean,
) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    buttons.forEachIndexed { i, b ->
        ActionButton(b.text, b.onClick, enabled = enabled, tonal = b.tonal, icon = b.icon, shape = groupItemShape(i, buttons.size, outer = concentric(HeroCardRadius, 20.dp)))
    }
}

@Composable
private fun ReleaseSheet(
    ui: ReleasesUi,
    onGrab: (Release) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    Column(Modifier.padding(horizontal = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.whisparr_releases_title), style = MaterialTheme.typography.titleLarge)
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (ui.expired) Text(stringResource(R.string.whisparr_releases_expired), style = MaterialTheme.typography.bodyMedium)
        when {
            ui.loading ->
                Column(Modifier.fillMaxWidth().padding(vertical = Space.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.M)) {
                    Loading()
                    Text(stringResource(R.string.whisparr_releases_loading), style = MaterialTheme.typography.bodyMedium)
                }

            ui.items.isEmpty() && ui.error == null -> EmptyState(StashlingIcons.Search, stringResource(R.string.whisparr_releases_empty))

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = Space.L)) {
                    itemsIndexed(ui.items, key = { _, r -> "${r.indexerId}_${r.guid}" }) { i, r ->
                        // Sheet content sits Space.L inside the 28dp sheet: the group takes the concentric radius.
                        val shape = groupItemShape(i, ui.items.size, outer = concentric(SheetRadius, Space.L))
                        ReleaseRow(r, shape, grabbing = ui.grabbing == r.guid, enabled = ui.grabbing == null) { onGrab(r) }
                    }
                }
        }
    }
}

@Composable
private fun ReleaseRow(
    r: Release,
    shape: Shape,
    grabbing: Boolean,
    enabled: Boolean,
    onGrab: () -> Unit,
) {
    val context = LocalContext.current
    TonalCard(shape = shape, onClick = if (enabled) onGrab else null) {
        Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val age = if (r.ageHours < 48) stringResource(R.string.whisparr_age_hours, r.ageHours.toInt()) else stringResource(R.string.whisparr_age_days, (r.ageHours / 24).toInt())
        val meta = stringResource(R.string.whisparr_release_meta, r.quality.quality.name, Formatter.formatShortFileSize(context, r.size), age, r.indexer)
        val seeders = r.seeders?.let { stringResource(R.string.whisparr_release_seeders, it) }
        Text(listOfNotNull(meta, seeders).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r.rejections.isNotEmpty()) {
            Text(stringResource(R.string.whisparr_release_rejected, r.rejections.joinToString("; ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (grabbing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
