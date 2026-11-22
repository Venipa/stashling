package net.venipa.stashling.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A backup file (versioned JSON). [accounts] only when the user opted in; [Settings.historyCursor] is never exported. */
@Serializable
data class Backup(
    val app: String = APP,
    val version: Int = VERSION,
    val createdAt: Long,
    val settings: Settings,
    val bookmarks: List<Bookmark> = emptyList(),
    val hidden: List<Hidden> = emptyList(),
    val accounts: BackupAccounts? = null,
) {
    companion object {
        const val APP = "stashling"
        const val VERSION = 1
    }
}

@Serializable
data class BackupAccounts(val stash: StashAccount? = null, val whisparr: WhisparrAccount? = null)

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Invalid, Newer }
}

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Throws [BackupException] for anything that is not a readable Stashling backup of a version this build knows. */
    fun decode(text: String): Backup {
        val backup = try {
            val root = json.parseToJsonElement(text).jsonObject
            if (root["app"]?.jsonPrimitive?.contentOrNull != Backup.APP) throw BackupException(BackupException.Reason.Invalid)
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: throw BackupException(BackupException.Reason.Invalid)
            if (version > Backup.VERSION) throw BackupException(BackupException.Reason.Newer)
            if (version < 1) throw BackupException(BackupException.Reason.Invalid)
            json.decodeFromJsonElement(Backup.serializer(), root)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException; so are jsonObject / jsonPrimitive on the wrong shape.
            throw BackupException(BackupException.Reason.Invalid)
        }
        // StashDB is https only (contract); an http one here would send the key in clear.
        if (backup.accounts?.stash?.baseUrl?.startsWith("https://") == false) throw BackupException(BackupException.Reason.Invalid)
        return backup
    }
}

/** What the app holds after an import. */
data class Restored(val settings: Settings, val bookmarks: List<Bookmark>, val hidden: List<Hidden>)

/**
 * Replace: the backup as is. Merge: union by key; bookmarks/hidden keep the newer timestamp (the backup on a tie),
 * presets (by id) and rules (by tag) have no timestamp so the backup's copy wins; scalar settings come from the backup.
 * The local history cursor is always kept.
 */
fun Backup.restore(local: Restored, replace: Boolean): Restored {
    val s = settings.copy(historyCursor = local.settings.historyCursor)
    if (replace) return Restored(s, bookmarks, hidden)
    val presetIds = settings.presets.mapTo(HashSet()) { it.id }
    val ruleTags = settings.tagRules.mapTo(HashSet()) { it.stashTagId }
    return Restored(
        s.copy(
            presets = local.settings.presets.filterNot { it.id in presetIds } + settings.presets,
            tagRules = settings.tagRules + local.settings.tagRules.filterNot { it.stashTagId in ruleTags },
            recentSearches = (settings.recentSearches + local.settings.recentSearches).distinct().take(10),
        ),
        (bookmarks + local.bookmarks).groupBy { it.kind to it.stashId }.values.map { g -> g.maxBy { it.addedAt } },
        (hidden + local.hidden).groupBy { it.kind to it.stashId }.values.map { g -> g.maxBy { it.hiddenAt } },
    )
}

@Dao
interface BackupDao {
    @Query("DELETE FROM bookmark")
    suspend fun clearBookmarks()

    @Query("DELETE FROM hidden")
    suspend fun clearHidden()

    @Insert
    suspend fun insertBookmarks(rows: List<BookmarkEntity>)

    @Insert
    suspend fun insertHidden(rows: List<HiddenEntity>)

    /** Both tables in one transaction: an import lands whole or not at all. */
    @Transaction
    suspend fun replaceAll(bookmarks: List<BookmarkEntity>, hidden: List<HiddenEntity>) {
        clearBookmarks()
        clearHidden()
        insertBookmarks(bookmarks)
        insertHidden(hidden)
    }
}

class BackupRepository(
    private val dao: BackupDao,
    private val bookmarks: BookmarkRepository,
    private val hiddenRepo: HiddenRepository,
    private val settings: SettingsStore,
    private val secrets: SecretStore,
) {
    suspend fun export(includeAccounts: Boolean, now: Long): Backup = Backup(
        createdAt = now,
        settings = settings.settings.first().copy(historyCursor = null),
        bookmarks = bookmarks.observe().first(),
        hidden = hiddenRepo.observe().first(),
        accounts = if (includeAccounts) BackupAccounts(secrets.stash.value, secrets.whisparr.value) else null,
    )

    /** [allowLock] false keeps the local app-lock value (the lock is only switched on where the device can unlock it). */
    suspend fun import(backup: Backup, replace: Boolean, accounts: Boolean, allowLock: Boolean) {
        val local = Restored(settings.settings.first(), bookmarks.observe().first(), hiddenRepo.observe().first())
        val r = backup.restore(local, replace)
        dao.replaceAll(
            r.bookmarks.map { BookmarkEntity(it.kind, it.stashId, it.title, it.subtitle, it.imageUrl, it.addedAt) },
            r.hidden.map { HiddenEntity(it.kind, it.stashId, it.name, it.imageUrl, it.hiddenAt) },
        )
        settings.update { cur ->
            r.settings.copy(historyCursor = cur.historyCursor, appLock = if (allowLock) r.settings.appLock else cur.appLock)
        }
        if (accounts) {
            backup.accounts?.whisparr?.let { secrets.setWhisparr(it) }
            backup.accounts?.stash?.let { secrets.setStash(it) }
        }
    }
}
