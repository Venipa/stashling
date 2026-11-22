package net.venipa.stashling.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class BookmarkKind { SCENE, PERFORMER, STUDIO, TAG }

data class Bookmark(val kind: BookmarkKind, val stashId: String, val title: String, val subtitle: String?, val imageUrl: String?, val addedAt: Long)

interface BookmarkRepository {
    /** Newest first. */
    fun observe(kind: BookmarkKind? = null): Flow<List<Bookmark>>

    fun isBookmarked(kind: BookmarkKind, stashId: String): Flow<Boolean>

    suspend fun add(bookmark: Bookmark)

    suspend fun remove(kind: BookmarkKind, stashId: String)

    /** After a merge (merged_into_id). If [newId] is already bookmarked, the old row is dropped. */
    suspend fun rekey(kind: BookmarkKind, oldId: String, newId: String)
}

/** https only, no trailing slash. */
@Serializable
data class StashAccount(val baseUrl: String, val apiKey: String)

/** [baseUrl] includes urlBase; ip:port or domain; http allowed. */
@Serializable
data class WhisparrAccount(val baseUrl: String, val apiKey: String, val ignoreCertErrors: Boolean = false)

/** Keystore AES-GCM over DataStore, excluded from backup; an unreadable blob = signed out. */
interface SecretStore {
    val stash: StateFlow<StashAccount?>
    val whisparr: StateFlow<WhisparrAccount?>

    suspend fun setStash(account: StashAccount?)

    suspend fun setWhisparr(account: WhisparrAccount?)
}

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class ListMode { CARDS, ROWS }

data class WhisparrDefaults(val rootFolder: String?, val qualityProfileId: Int?, val monitor: String = "movieOnly", val searchOnAdd: Boolean = true)

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val secureScreen: Boolean = false,
    val appLock: Boolean = false,
    val appLockTimeoutSec: Int = 60,
    val sceneListMode: ListMode = ListMode.CARDS,
    val whisparr: WhisparrDefaults = WhisparrDefaults(null, null),
    val historyCursor: Long? = null,
)

interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
