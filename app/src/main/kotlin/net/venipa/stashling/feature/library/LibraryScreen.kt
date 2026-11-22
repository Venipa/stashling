package net.venipa.stashling.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.DockItemShape
import net.venipa.stashling.ds.DockScrollState
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.downloads.DownloadsScreen
import net.venipa.stashling.feature.downloads.DownloadsViewModel
import net.venipa.stashling.feature.saved.SavedScreen

/**
 * Library tab: Saved | Downloads (Downloads explains how to connect Whisparr). The segment lives in the
 * activity-scoped Downloads ViewModel so the downloads row can open Downloads from any tab; its control is on the dock.
 */
@Composable
fun LibraryScreen(
    hasDownloads: Boolean,
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val downloads = tabViewModel { DownloadsViewModel(c) }
    val segment by downloads.librarySegment.collectAsState()
    if (segment == 1) DownloadsScreen(hasDownloads) else SavedScreen(onOpenScene, onOpenPerformer, onOpenStudio, onOpenTag)
}

/** Library's dock row: Saved | Downloads; compact only the active segment + [ring], tapping it expands again. */
@Composable
fun LibraryDockControls(
    dock: DockScrollState,
    ring: @Composable () -> Unit,
) {
    val c = LocalAppContainer.current
    val downloads = tabViewModel { DownloadsViewModel(c) }
    val segment by downloads.librarySegment.collectAsState()
    val labels = listOf(stringResource(R.string.nav_saved), stringResource(R.string.nav_downloads))
    if (dock.compact) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(DockItemShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable { dock.compact = false },
                contentAlignment = Alignment.Center,
            ) { Text(labels[segment], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1) }
            ring()
        }
    } else {
        StashSegmented(labels, segment, { downloads.librarySegment.value = it })
    }
}
