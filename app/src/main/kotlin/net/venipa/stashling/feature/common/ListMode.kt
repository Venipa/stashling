package net.venipa.stashling.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.core.data.ListMode
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.ds.ViewModeToggle

/** The persisted scene list mode (Settings.sceneListMode). */
@Composable
fun rememberSceneListMode(): State<ListMode> {
    val settings = LocalAppContainer.current.settings.settings.collectAsState(Settings())
    return remember { derivedStateOf { settings.value.sceneListMode } }
}

/** The cards/rows toggle for list top bars. */
@Composable
fun ListModeToggle(mode: ListMode) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    ViewModeToggle(mode == ListMode.ROWS, { rows -> scope.launch { store.update { it.copy(sceneListMode = if (rows) ListMode.ROWS else ListMode.CARDS) } } })
}
