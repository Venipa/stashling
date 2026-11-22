package net.venipa.stashling.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.venipa.stashling.R
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.feature.downloads.DownloadsScreen
import net.venipa.stashling.feature.saved.SavedScreen

/** Library tab: Saved | Downloads (the segment only when Whisparr is connected); both screens keep their own ViewModels. */
@Composable
fun LibraryScreen(
    hasDownloads: Boolean,
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    val switch: (@Composable () -> Unit)? =
        if (hasDownloads) {
            {
                StashSegmented(
                    listOf(stringResource(R.string.nav_saved), stringResource(R.string.nav_downloads)),
                    segment,
                    { segment = it },
                    Modifier.padding(horizontal = Space.L, vertical = Space.Xs),
                )
            }
        } else {
            null
        }
    if (hasDownloads && segment == 1) DownloadsScreen(switch) else SavedScreen(onOpenScene, onOpenPerformer, onOpenStudio, onOpenTag, switch)
}
