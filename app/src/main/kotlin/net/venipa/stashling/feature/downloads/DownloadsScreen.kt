package net.venipa.stashling.feature.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.whisparr.HistoryItem
import net.venipa.stashling.core.whisparr.QueueItem
import net.venipa.stashling.core.whisparr.WhisparrScene
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.ConfirmSheet
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.LoadingPill
import net.venipa.stashling.ds.PillTone
import net.venipa.stashling.ds.RefreshBox
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.StatusPill
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.ds.readableWidth
import net.venipa.stashling.feature.common.LocalNetworkNotice
import net.venipa.stashling.feature.common.SceneRowSkeleton
import net.venipa.stashling.feature.common.localNetworkMissing
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.detail.whisparr.QueueProgress
import net.venipa.stashling.feature.detail.whisparr.queueLabel
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.LocalNavigator
import net.venipa.stashling.nav.SceneDetailRoute
import net.venipa.stashling.nav.SettingsAction

/** Downloads tab: Queue | History. Polls only while visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val container = LocalAppContainer.current
    val vm: DownloadsViewModel = tabViewModel { DownloadsViewModel(container) }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.poll() } }
    // Download notifications need POST_NOTIFICATIONS; ask where it obviously belongs. Android stops asking after two denials.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val nav = LocalNavigator.current
    val open: (WhisparrScene?) -> Unit = { scene -> scene?.let { nav.push(SceneDetailRoute(it.stashId ?: it.foreignId)) } }
    var confirm by remember { mutableStateOf<Pair<QueueItem, Boolean>?>(null) }

    Scaffold(
        topBar = { TopAppBar({ Text(stringResource(R.string.downloads_title)) }, actions = { SettingsAction() }) },
    ) { padding ->
        val dock = LocalDockInset.current
        Box(Modifier.fillMaxSize()) {
            RefreshBox(refreshing, vm::refresh, Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(start = Space.L, end = Space.L, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + dock + Space.L),
                    // 2dp: queue / history rows form one connected group (concentric).
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "tabs") {
                        StashSegmented(
                            listOf(stringResource(R.string.downloads_tab_queue), stringResource(R.string.downloads_tab_history)),
                            tab,
                            vm::selectTab,
                            Modifier.padding(bottom = Space.M),
                        )
                    }
                    val list = if (tab == 0) queue else history
                    list.error?.let { msg -> item(key = "error") { if (LocalContext.current.localNetworkMissing()) LocalNetworkNotice(Modifier.padding(bottom = Space.S)) else TonalCard(Modifier.padding(bottom = Space.S), tone = CardTone.Error) { Text(msg) } } }
                    when {
                        list.items == null -> items(6, key = { "skeleton$it" }) { SceneRowSkeleton(it, 6) }

                        list.items.isEmpty() ->
                            item(key = "empty") {
                                EmptyState(StashlingIcons.Download, stringResource(if (tab == 0) R.string.downloads_queue_empty else R.string.downloads_history_empty))
                            }

                        tab == 0 -> {
                            val rows = queue.items.orEmpty()
                            itemsIndexed(rows, key = { _, q -> "q${q.id}" }) { i, q -> QueueRow(q, groupItemShape(i, rows.size), { open(q.movie) }, { confirm = q to it }) }
                        }

                        else -> {
                            val rows = history.items.orEmpty()
                            itemsIndexed(rows, key = { _, h -> "h${h.id}" }) { i, h -> HistoryRow(h, groupItemShape(i, rows.size)) { open(h.movie) } }
                        }
                    }
                }
            }
            LoadingPill((if (tab == 0) queue else history).items == null, Modifier.align(Alignment.Center))
        }
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
private fun QueueRow(
    item: QueueItem,
    shape: Shape,
    onOpen: () -> Unit,
    onRemove: (blocklist: Boolean) -> Unit,
) = TonalCard(shape = shape, onClick = item.movie?.let { onOpen }) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(item.movie?.title ?: item.title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        StatusPill(queueLabel(item), if (item.trackedDownloadStatus == "warning" || item.status == "warning") PillTone.Warn else PillTone.Ok)
    }
    QueueProgress(item)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({ onRemove(true) }) { Text(stringResource(R.string.downloads_blocklist)) }
        TextButton({ onRemove(false) }) { Text(stringResource(R.string.downloads_remove)) }
    }
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
