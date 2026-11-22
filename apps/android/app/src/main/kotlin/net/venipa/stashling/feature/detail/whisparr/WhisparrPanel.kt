package net.venipa.stashling.feature.detail.whisparr

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.GlassRole
import net.venipa.stashling.ds.GlassState
import net.venipa.stashling.ds.HeroCardRadius
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.LongOperationCard
import net.venipa.stashling.ds.PillTone
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.SheetRadius
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.StatusPill
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.blockPointerPassThrough
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.glass
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.ds.sheetTitle
import net.venipa.stashling.feature.common.LocalNetworkNotice
import net.venipa.stashling.feature.common.localNetworkMissing
import net.venipa.stashling.feature.common.openLink

/**
 * Whisparr as a floating bar in the dock's slot of the scene page. Status (and the preset chip) on the start, the
 * primary action on the end, ⋮ for the rest; downloading shows a progress ring. Glass while it floats over imagery
 * ([overImage]), tonal over text. Renders nothing (height 0) while Whisparr is not connected. [onHeight] reports the footprint.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisparrBar(
    stashId: String,
    glass: GlassState,
    overImage: Boolean,
    modifier: Modifier = Modifier,
    onHeight: (Int) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: WhisparrPanelViewModel = viewModel(key = "whisparr-$stashId") { WhisparrPanelViewModel(stashId, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val searchOnAdd by vm.searchOnAdd.collectAsStateWithLifecycle()
    val noRelease by vm.noRelease.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val releases by vm.releases.collectAsStateWithLifecycle()
    val preset by vm.preset.collectAsStateWithLifecycle()
    var presetSheet by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Polls only while visible.
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.poll() } }

    val s = state
    if (s is PanelState.Unavailable) {
        SideEffect { onHeight(0) }
        return
    }
    val context = LocalContext.current
    val base = container.secrets.whisparr.collectAsStateWithLifecycle().value?.baseUrl
    val slug = s.sceneOrNull()?.titleSlug
    val open = if (slug != null && base != null) SheetAction(stringResource(R.string.whisparr_open), StashlingIcons.OpenExternal) { openLink(context, "$base/movie/$slug") } else null
    val add = SheetAction(stringResource(R.string.whisparr_add), StashlingIcons.Add) { vm.add(AddMode.REQUEST) }
    val addSearch = SheetAction(stringResource(R.string.whisparr_add_and_search), StashlingIcons.Search) { vm.add(AddMode.QUEUE) }
    val interactive = stringResource(R.string.whisparr_interactive_search)
    val search = stringResource(R.string.whisparr_search)
    val (primary, rest) =
        when (s) {
            // Settings "search on add" decides which add is the primary; the other one sits in the overflow.
            is PanelState.NotInLibrary -> (if (searchOnAdd) addSearch else add) to listOf(if (searchOnAdd) add else addSearch, SheetAction(interactive, StashlingIcons.Search) { vm.add(AddMode.PICK) })

            is PanelState.Missing ->
                SheetAction(search, StashlingIcons.Search) { vm.search(s.scene) } to
                    listOf(
                        SheetAction(interactive, StashlingIcons.Search) { vm.openReleases(s.scene) },
                        if (s.scene.monitored) {
                            SheetAction(stringResource(R.string.whisparr_unmonitor), StashlingIcons.Visibility) { vm.setMonitored(s.scene, false) }
                        } else {
                            SheetAction(stringResource(R.string.whisparr_monitor), StashlingIcons.Visibility) { vm.setMonitored(s.scene, true) }
                        },
                    )

            is PanelState.Failed -> SheetAction(search, StashlingIcons.Search) { vm.search(s.scene) } to listOf(SheetAction(interactive, StashlingIcons.Search) { vm.openReleases(s.scene) })

            is PanelState.Error -> SheetAction(stringResource(R.string.whisparr_retry), StashlingIcons.Search, onClick = vm::retry) to emptyList()

            else -> null to emptyList()
        }
    val overflow = rest + listOfNotNull(open)
    val haptic = LocalStashHaptics.current
    val shape = RoundedCornerShape(GlassCorner)
    val surface = if (overImage) Modifier.glass(glass, GlassRole.Dock) else Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).blockPointerPassThrough()
    Column(
        modifier
            .onSizeChanged { onHeight(it.height) }
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.L)
            .padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        if (s is PanelState.Error && LocalContext.current.localNetworkMissing()) LocalNetworkNotice()
        Row(
            Modifier.fillMaxWidth().then(surface).padding(BarInset).heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = Space.S), verticalArrangement = Arrangement.spacedBy(Space.Xs)) { BarStatus(s, noRelease, preset, busy) { presetSheet = true } }
            primary?.let { p ->
                Button(
                    {
                        haptic.confirm()
                        p.onClick()
                    },
                    Modifier.heightIn(min = 56.dp),
                    enabled = !busy,
                    shape = concentricShape(GlassCorner, BarInset),
                ) {
                    if (busy) LoadingIndicator(Modifier.size(24.dp), LocalContentColor.current) else Icon(p.icon, null, Modifier.size(20.dp))
                    Text(p.label, Modifier.padding(start = Space.S), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
            if (overflow.isNotEmpty()) {
                IconButton({ more = true }, Modifier.size(48.dp)) { Icon(StashlingIcons.More, stringResource(R.string.whisparr_more)) }
            }
        }
    }
    if (more) ActionSheet(stringResource(R.string.whisparr_title), overflow) { more = false }
    releases?.let { ReleaseSheet(it, onGrab = vm::grab, onDismiss = vm::closeReleases) }
    preset?.takeIf { presetSheet }?.let { choice ->
        PresetSheet(choice, onPick = {
            vm.overridePreset(it)
            presetSheet = false
        }, onDismiss = { presetSheet = false })
    }
}

/** Same 8dp inset as the dock's tab row, so the button's radius is concentric with the 28dp bar. */
private val BarInset = 8.dp

/** Start side of the bar: a ring while downloading, otherwise the status pill and one supporting line (preset chip, file, reason). */
@Composable
private fun BarStatus(
    s: PanelState,
    noRelease: Boolean,
    preset: PresetChoice?,
    busy: Boolean,
    onPreset: () -> Unit,
) {
    val context = LocalContext.current
    val small = MaterialTheme.typography.bodySmall
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    when (s) {
        PanelState.Loading -> SkeletonBox(Modifier.size(120.dp, 24.dp), MaterialTheme.shapes.small)

        is PanelState.InQueue -> Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
            val item = s.item
            CircularProgressIndicator({ item.progress.toFloat().coerceIn(0f, 1f) }, Modifier.size(32.dp))
            Column {
                Text(queueLabel(item), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val done = (item.size - item.sizeleft).toLong().coerceAtLeast(0)
                Text(
                    stringResource(R.string.whisparr_progress, (item.progress * 100).toInt(), Formatter.formatShortFileSize(context, done), Formatter.formatShortFileSize(context, item.size.toLong())),
                    style = small,
                    color = dim,
                    maxLines = 1,
                )
            }
        }

        is PanelState.Searching -> Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.whisparr_searching), style = MaterialTheme.typography.labelLarge)
        }

        else -> {
            Pill(s)
            val line =
                when (s) {
                    is PanelState.NotInLibrary -> if (s.excluded) stringResource(R.string.whisparr_excluded) else null

                    is PanelState.Missing -> if (noRelease) stringResource(R.string.whisparr_no_release) else null

                    is PanelState.Failed -> s.message

                    is PanelState.Error -> s.message.ifBlank { null }

                    is PanelState.Downloaded -> {
                        val file = s.scene.movieFile
                        val size = file?.size ?: s.scene.sizeOnDisk
                        listOfNotNull(file?.quality?.quality?.name, size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ").ifEmpty { null }
                    }

                    else -> null
                }
            line?.let { Text(it, style = small, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (s is PanelState.NotInLibrary) preset?.let { PresetChip(it, enabled = !busy, onClick = onPreset) }
        }
    }
}

/** 5A: "Preset: X ▾" chip; the reason it applies is its accessibility label. Tapping picks another preset for this add only. */
@Composable
private fun PresetChip(
    choice: PresetChoice,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val reason =
        when {
            choice.overridden -> stringResource(R.string.whisparr_preset_chosen)
            choice.rule != null -> stringResource(R.string.whisparr_preset_matched, choice.rule.stashTagName)
            else -> stringResource(R.string.whisparr_preset_default)
        }
    AssistChip(
        onClick,
        { Text(stringResource(R.string.whisparr_preset_chip, choice.preset.name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        Modifier.semantics { stateDescription = reason },
        enabled = enabled,
        // 16dp in from the 28dp bar corner (8dp inset + 8dp status padding).
        shape = concentricShape(GlassCorner, 16.dp),
        trailingIcon = { Icon(StashlingIcons.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun PresetSheet(
    choice: PresetChoice,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.whisparr_preset_sheet_title), style = sheetTitle)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            choice.all.forEachIndexed { i, p ->
                val selected = p.id == choice.preset.id
                ListItem(
                    { Text(p.name) },
                    Modifier.clip(groupItemShape(i, choice.all.size, outer = concentric(SheetRadius, Space.L))).clickable { onPick(p.id) },
                    supportingContent = { Text(p.rootFolder ?: stringResource(R.string.settings_not_set), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = if (selected) ({ Icon(StashlingIcons.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
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
            ui.loading -> LongOperationCard(stringResource(R.string.whisparr_searching_releases), ui.startedAt, onDismiss, Modifier.padding(vertical = Space.M))

            ui.items.isEmpty() && ui.error == null -> EmptyState(StashlingIcons.Search, stringResource(R.string.whisparr_releases_empty))

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = Space.L)) {
                    itemsIndexed(ui.items, key = { _, r -> "${r.indexerId}_${r.guid}" }) { i, r ->
                        // Sheet content sits Space.L inside the 28dp sheet: the group takes the concentric radius.
                        val shape = groupItemShape(i, ui.items.size, outer = concentric(SheetRadius, Space.L))
                        val haptic = LocalStashHaptics.current
                        ReleaseRow(r, shape, grabbing = ui.grabbing == r.guid, enabled = ui.grabbing == null) {
                            haptic.confirm()
                            onGrab(r)
                        }
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

private fun PanelState.sceneOrNull() = when (this) {
    is PanelState.Missing -> scene
    is PanelState.Searching -> scene
    is PanelState.InQueue -> scene
    is PanelState.Failed -> scene
    is PanelState.Downloaded -> scene
    else -> null
}
