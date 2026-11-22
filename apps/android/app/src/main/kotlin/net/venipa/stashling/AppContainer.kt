package net.venipa.stashling

import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import net.venipa.stashling.core.whisparr.LibraryBadge
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
    val backup by lazy { net.venipa.stashling.core.data.BackupRepository(db.backup(), bookmarks, hidden, settings, secrets) }

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

    /** In-app updater (GitHub releases). */
    val updater by lazy { net.venipa.stashling.update.AppUpdater(context.applicationContext, settings, appScope) }

    // --- whisparr ---
    // Own client per account (never the StashDB one); null = not connected → PanelState.Unavailable.
    // shortcut: the previous client is not closed on account change (rare, small leak), close it if switching gets common.
    val whisparr: StateFlow<WhisparrRepository?> = secrets.whisparr
        .map { account -> account?.let { WhisparrRepository(WhisparrClients.create(it), settings) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // Library part of the tile badges (in library / downloaded); a full library call, so refreshed rarely (see [refreshWhisparrIndex]).
    private val libraryBadges = kotlinx.coroutines.flow.MutableStateFlow<Map<String, LibraryBadge>>(emptyMap())

    /** StashDB ids of queued scenes: set by the downloads poller from the queue it already fetches every 3 s. */
    val queuedStashIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    /** stashId → badge for scene tiles (library + live DOWNLOADING overlay); empty when Whisparr isn't connected. */
    val whisparrIndex: StateFlow<Map<String, LibraryBadge>> =
        combine(libraryBadges, queuedStashIds) { lib, queued -> if (queued.isEmpty()) lib else lib + queued.associateWith { LibraryBadge.DOWNLOADING } }
            .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Full library index: on connect, on movie/moviefile events, after own add/search/grab, when a download leaves the queue,
     * and every 10 min by the poller. Decoded off the main thread; errors keep the last index.
     */
    suspend fun refreshWhisparrIndex() {
        val repo = whisparr.value ?: return run {
            libraryBadges.value = emptyMap()
            queuedStashIds.value = emptySet()
        }
        runCatching { withContext(Dispatchers.Default) { repo.libraryIndex() } }
            .onSuccess { libraryBadges.value = it }
            // Diagnosable without leaking anything: error type + Whisparr's message only.
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else System.err.println("Whisparr index failed: ${it::class.simpleName}: ${it.message}") }
    }

    // Live updates (SignalR) while the app is visible; polls slow down to a fallback while connected.
    private val whisparrLive: StateFlow<net.venipa.stashling.core.whisparr.WhisparrLive?> =
        secrets.whisparr.map { account -> account?.let { net.venipa.stashling.core.whisparr.WhisparrLive(it) } }.stateIn(appScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val whisparrLiveConnected: StateFlow<Boolean> =
        whisparrLive.flatMapLatest { it?.connected ?: flowOf(false) }.stateIn(appScope, SharingStarted.Eagerly, false)

    val whisparrEvents = MutableSharedFlow<net.venipa.stashling.core.whisparr.WhisparrEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Run while the app is visible: one hub connection; movie/moviefile events refresh the tile index (debounced 5 s). Queue events go to the 3 s poller. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun runWhisparrLive(): Unit = coroutineScope {
        // 5 s: imports and bulk edits arrive as bursts of movie/moviefile events; each refresh decodes the whole library.
        launch { whisparrEvents.filter { it.name == "movie" || it.name == "moviefile" }.debounce(5_000).collect { refreshWhisparrIndex() } }
        whisparrLive.collectLatest { live -> live?.events?.collect { whisparrEvents.emit(it) } }
    }
    // --- end whisparr ---
}

val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("AppContainer not provided") }
