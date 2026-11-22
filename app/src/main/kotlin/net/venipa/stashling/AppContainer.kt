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
import kotlinx.coroutines.launch
import net.venipa.stashling.core.data.BookmarkRepository
import net.venipa.stashling.core.data.HiddenIds
import net.venipa.stashling.core.data.HiddenRepository
import net.venipa.stashling.core.data.RoomBookmarkRepository
import net.venipa.stashling.core.data.SecretStore
import net.venipa.stashling.core.data.SecretStoreImpl
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.SettingsStore
import net.venipa.stashling.core.data.SettingsStoreImpl
import net.venipa.stashling.core.data.StashlingDatabase
import net.venipa.stashling.core.stash.CachingStashDbApi
import net.venipa.stashling.core.stash.StashDbClient
import net.venipa.stashling.core.stash.newStashHttpClient
import net.venipa.stashling.core.whisparr.WhisparrClients
import net.venipa.stashling.core.whisparr.WhisparrRepository
import net.venipa.stashling.feature.common.apiValue
import net.venipa.stashling.feature.lock.AppLockController

/** Manual DI: one instance per process, created by [StashlingApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secrets: SecretStore = SecretStoreImpl(context)
    val settings: SettingsStore = SettingsStoreImpl(context)

    // Loaded once per process: screens start from the stored values, so switches/segments don't animate from defaults.
    val settingsState: StateFlow<Settings?> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    // stashling.db, kept out of backup (data_extraction_rules.xml).
    private val db by lazy { StashlingDatabase.open(context) }
    val bookmarks: BookmarkRepository by lazy { RoomBookmarkRepository(db.bookmarks()) }
    val hidden: HiddenRepository by lazy { HiddenRepository(db.hidden()) }

    /** Hidden ids for the scene list filters, loaded at start so lists never flash hidden scenes. */
    val hiddenIds: StateFlow<HiddenIds> by lazy { hidden.ids().stateIn(appScope, SharingStarted.Eagerly, HiddenIds()) }

    /** Settings for per-item reads in lists (preset labels); null until DataStore answers. */
    val settingsNow: StateFlow<net.venipa.stashling.core.data.Settings?> by lazy { settings.settings.stateIn(appScope, SharingStarted.Eagerly, null) }

    /** Settings.performerGender as a GenderEnum name (null = both), for the scene list filter. */
    val sceneGender: StateFlow<String?> by lazy { settings.settings.map { it.performerGender.apiValue() }.stateIn(appScope, SharingStarted.Eagerly, null) }

    // StashDB: its own client (https only, no redirects); the key is read per call, so a re-login needs no rebuild.
    // Cached: renavigating never refetches; pull-to-refresh calls stash.invalidate(). A new account starts empty.
    val stash = CachingStashDbApi(StashDbClient(newStashHttpClient()) { secrets.stash.value })

    init {
        appScope.launch { secrets.stash.collect { stash.invalidate() } }
    }

    /** Swipe / menu scene actions; app-scoped so a hide or add finishes even when its screen is gone. */
    val sceneActions by lazy { net.venipa.stashling.feature.common.SceneSwipeController(this, appScope) }

    val appLock by lazy { AppLockController(settings.settings, appScope) }

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
