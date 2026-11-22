package net.venipa.stashling.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.downloads.DownloadsScreen
import net.venipa.stashling.feature.downloads.DownloadsViewModel
import net.venipa.stashling.feature.saved.SavedScreen

/**
 * Library tab: Saved | Downloads, the segment always shown (Downloads explains how to connect Whisparr).
 * The segment lives in the activity-scoped Downloads ViewModel so the downloads pill can open Downloads from any tab.
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
    val switch: @Composable () -> Unit = {
        StashSegmented(
            listOf(stringResource(R.string.nav_saved), stringResource(R.string.nav_downloads)),
            segment,
            { downloads.librarySegment.value = it },
            Modifier.padding(horizontal = Space.L, vertical = Space.Xs),
        )
    }
    if (segment == 1) DownloadsScreen(hasDownloads, switch) else SavedScreen(onOpenScene, onOpenPerformer, onOpenStudio, onOpenTag, switch)
}
