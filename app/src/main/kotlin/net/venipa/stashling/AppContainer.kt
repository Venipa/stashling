package net.venipa.stashling

import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.venipa.stashling.core.whisparr.WhisparrClients
import net.venipa.stashling.core.whisparr.WhisparrRepository
import net.venipa.stashling.core.data.BookmarkRepository
import net.venipa.stashling.core.data.RoomBookmarkRepository
import net.venipa.stashling.core.data.SecretStore
import net.venipa.stashling.core.data.SecretStoreImpl
import net.venipa.stashling.core.data.SettingsStore
import net.venipa.stashling.core.data.SettingsStoreImpl
import net.venipa.stashling.core.data.StashlingDatabase
import net.venipa.stashling.core.stash.StashDbApi
import net.venipa.stashling.core.stash.StashDbClient
import net.venipa.stashling.core.stash.newStashHttpClient

/** Manual DI: one instance per process, created by [StashlingApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secrets: SecretStore = SecretStoreImpl(context)
    val settings: SettingsStore = SettingsStoreImpl(context)

    // stashling.db, kept out of backup (data_extraction_rules.xml).
    private val db by lazy { StashlingDatabase.open(context) }
    val bookmarks: BookmarkRepository by lazy { RoomBookmarkRepository(db.bookmarks()) }

    // StashDB: its own client (https only, no redirects); the key is read per call, so a re-login needs no rebuild.
    val stash: StashDbApi = StashDbClient(newStashHttpClient()) { secrets.stash.value }

    // --- whisparr ---
    // Own client per account (never the StashDB one); null = not connected → PanelState.Unavailable.
    // shortcut: the previous client is not closed on account change (rare, small leak), close it if switching gets common.
    val whisparr: StateFlow<WhisparrRepository?> = secrets.whisparr
        .map { account -> account?.let { WhisparrRepository(WhisparrClients.create(it), settings) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)
    // --- end whisparr ---
}

val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("AppContainer not provided") }
