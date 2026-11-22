package net.venipa.stashling.feature.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.whisparr.HistoryItem
import net.venipa.stashling.core.whisparr.QueueItem
import net.venipa.stashling.core.whisparr.WhisparrScene
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.CollapsingHeader
import net.venipa.stashling.ds.ConfirmSheet
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.LoadingPill
import net.venipa.stashling.ds.PillTone
import net.venipa.stashling.ds.RefreshBox
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.StatusPill
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.ds.readableWidth
import net.venipa.stashling.ds.rememberCollapsingHeader
import net.venipa.stashling.feature.common.LocalNetworkNotice
import net.venipa.stashling.feature.common.SceneRowSkeleton
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.localNetworkMissing
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.detail.whisparr.QueueProgress
import net.venipa.stashling.feature.detail.whisparr.queueLabel
import net.venipa.stashling.nav.BrowseRoute
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.LocalNavigator
import net.venipa.stashling.nav.OnboardingRoute
import net.venipa.stashling.nav.SceneDetailRoute

/**
 * Library → Downloads: one list grouped by status (Downloading, Queued, Failed, Imported in the last 14 days),
 * each a connected group. Long-press a queue row for Remove / Blocklist. The shell polls (one poller while the app is visible).
 */
@Composable
fun DownloadsScreen(
    connected: Boolean,
    librarySwitch: @Composable () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DownloadsViewModel = tabViewModel { DownloadsViewModel(container) }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    // Download notifications need POST_NOTIFICATIONS; ask where it obviously belongs. Android stops asking after two denials.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(connected) {
        if (connected && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val nav = LocalNavigator.current
    val open: (WhisparrScene?) -> Unit = { scene -> scene?.let { nav.push(SceneDetailRoute(it.stashId ?: it.foreignId)) } }
    var actionsFor by remember { mutableStateOf<QueueItem?>(null) }
    var confirm by remember { mutableStateOf<Pair<QueueItem, Boolean>?>(null) }

    val header = rememberCollapsingHeader()
    Scaffold(
        Modifier.nestedScroll(header.connection),
        topBar = {
            CollapsingHeader(header) {
                StashTopBar(stringResource(R.string.nav_library))
                librarySwitch()
            }
        },
    ) { padding ->
        val dock = LocalDockInset.current
        val firstLoad = connected && queue.items == null
        Box(Modifier.fillMaxSize()) {
            RefreshBox(refreshing, vm::refresh, Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(start = Space.L, end = Space.L, top = padding.calculateTopPadding() + Space.S, bottom = padding.calculateBottomPadding() + dock + Space.L),
                    // 2dp: rows of a status form one connected group (concentric).
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!connected) {
                        item(key = "connect") {
                            EmptyState(StashlingIcons.Download, stringResource(R.string.library_no_whisparr), body = stringResource(R.string.library_no_whisparr_body)) {
                                ActionButton(stringResource(R.string.library_connect_whisparr), { nav.push(OnboardingRoute(2)) }, Modifier.padding(top = Space.S))
                            }
                        }
                        return@LazyColumn
                    }
                    (queue.error ?: history.error)?.let { msg -> item(key = "error") { if (LocalContext.current.localNetworkMissing()) LocalNetworkNotice(Modifier.padding(bottom = Space.S)) else TonalCard(Modifier.padding(bottom = Space.S), tone = CardTone.Error) { Text(msg) } } }
                    if (firstLoad) {
                        items(6, key = { "skeleton$it" }) { SceneRowSkeleton(it, 6) }
                        return@LazyColumn
                    }
                    if (queue.items.isNullOrEmpty()) {
                        item(key = "empty") {
                            EmptyState(StashlingIcons.Download, stringResource(R.string.library_queue_empty), body = stringResource(R.string.library_queue_empty_body)) {
                                ActionButton(stringResource(R.string.library_browse), { nav.tab(BrowseRoute) }, Modifier.padding(top = Space.S), tonal = true)
                            }
                        }
                    }
                    queueGroup("downloading", R.string.library_dl_downloading, sections.downloading, true, { open(it.movie) }, { actionsFor = it })
                    queueGroup("queued", R.string.library_dl_queued, sections.queued, false, { open(it.movie) }, { actionsFor = it })
                    // Failed: queue failures (with actions) and failures that already left the queue, one group.
                    val failedCount = sections.failed.size + sections.failedHistory.size
                    if (failedCount > 0) {
                        item(key = "h-failed") { GroupHeader(stringResource(R.string.library_dl_failed, failedCount)) }
                        itemsIndexed(sections.failed, key = { _, q -> "q${q.id}" }) { i, q -> QueueRow(q, groupItemShape(i, failedCount), false, { open(q.movie) }, { actionsFor = q }) }
                        itemsIndexed(sections.failedHistory, key = { _, h -> "h${h.id}" }) { i, h -> HistoryRow(h, groupItemShape(sections.failed.size + i, failedCount)) { open(h.movie) } }
                    }
                    if (sections.imported.isNotEmpty()) {
                        item(key = "h-imported") { GroupHeader(stringResource(R.string.library_dl_imported, sections.imported.size)) }
                        itemsIndexed(sections.imported, key = { _, h -> "h${h.id}" }) { i, h -> HistoryRow(h, groupItemShape(i, sections.imported.size)) { open(h.movie) } }
                    }
                }
            }
            LoadingPill(firstLoad, Modifier.align(Alignment.Center))
        }
    }

    actionsFor?.let { item ->
        ActionSheet(
            item.movie?.title ?: item.title,
            listOf(
                SheetAction(stringResource(R.string.downloads_remove), StashlingIcons.Delete, destructive = true) { confirm = item to false },
                SheetAction(stringResource(R.string.downloads_blocklist), StashlingIcons.Close, destructive = true) { confirm = item to true },
            ),
            { actionsFor = null },
        )
    }
    confirm?.let { (item, blocklist) ->
        ConfirmSheet(
            title = stringResource(if (blocklist) R.string.downloads_blocklist_title else R.string.downloads_remove_title),
            body = stringResource(if (blocklist) R.string.downloads_blocklist_body else R.string.downloads_remove_body),
            confirmLabel = stringResource(if (blocklist) R.string.downloads_blocklist else R.string.downloads_remove),
            onConfirm = {
                vm.remove(item, blocklist)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun GroupHeader(text: String) = SectionHeader(text, Modifier.padding(top = Space.M, bottom = Space.Xs))

private fun LazyListScope.queueGroup(
    key: String,
    @StringRes title: Int,
    rows: List<QueueItem>,
    progress: Boolean,
    onOpen: (QueueItem) -> Unit,
    onLongPress: (QueueItem) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "h-$key") { GroupHeader(stringResource(title, rows.size)) }
    itemsIndexed(rows, key = { _, q -> "q${q.id}" }) { i, q -> QueueRow(q, groupItemShape(i, rows.size), progress, { onOpen(q) }, { onLongPress(q) }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    item: QueueItem,
    shape: Shape,
    progress: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) = TonalCard(Modifier.clip(shape).combinedClickable(onLongClick = onLongPress, onClick = onOpen), shape = shape) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(item.movie?.title ?: item.title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val tone =
            when {
                item.trackedDownloadState in setOf("failed", "failedPending") || item.status == "failed" -> PillTone.Error
                item.trackedDownloadStatus == "warning" || item.status == "warning" -> PillTone.Warn
                else -> PillTone.Ok
            }
        StatusPill(queueLabel(item), tone)
    }
    if (progress) QueueProgress(item) else item.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    shape: Shape,
    onOpen: () -> Unit,
) = TonalCard(shape = shape, onClick = item.movie?.let { onOpen }) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(item.movie?.title ?: item.sourceTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val (label, tone) =
            when (item.eventType) {
                "grabbed" -> R.string.downloads_event_grabbed to PillTone.Neutral
                "downloadFolderImported" -> R.string.downloads_event_imported to PillTone.Ok
                else -> R.string.downloads_event_failed to PillTone.Error
            }
        StatusPill(stringResource(label), tone)
    }
    Text(
        DateUtils.getRelativeTimeSpanString(item.date.toEpochMilliseconds(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
