package net.venipa.stashling.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class BookmarkKind { SCENE, PERFORMER, STUDIO, TAG }

@Serializable
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

/** Which performers lists show (setting); BOTH = no gender filter. */
enum class PerformerGender { FEMALE, MALE, BOTH }

/** The built-in "Default" preset (stored in the original keys, so existing values stay); [tagIds] = Whisparr tag ids. */
@Serializable
data class WhisparrDefaults(val rootFolder: String?, val qualityProfileId: Int?, val monitor: String = "movieOnly", val searchOnAdd: Boolean = true, val tagIds: List<Int> = emptyList())

/** A named set of add options. The Default one ([DEFAULT_PRESET_ID]) is [Settings.whisparr]; the others are [Settings.presets]. */
@Serializable
data class WhisparrPreset(
    val id: String,
    val name: String,
    val rootFolder: String? = null,
    val qualityProfileId: Int? = null,
    val monitor: String = "movieOnly",
    val searchOnAdd: Boolean = true,
    val whisparrTagIds: List<Int> = emptyList(),
)

/** StashDB tag → preset. Rules are an ordered list: position = priority, the first one whose tag is on the scene wins. */
@Serializable
data class TagRule(val stashTagId: String, val stashTagName: String, val presetId: String)

const val DEFAULT_PRESET_ID = "default"

fun WhisparrDefaults.asPreset(name: String = "Default") = WhisparrPreset(DEFAULT_PRESET_ID, name, rootFolder, qualityProfileId, monitor, searchOnAdd, tagIds)

fun WhisparrPreset.asDefaults() = WhisparrDefaults(rootFolder, qualityProfileId, monitor, searchOnAdd, whisparrTagIds)

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val secureScreen: Boolean = false,
    val appLock: Boolean = false,
    val appLockTimeoutSec: Int = 60,
    val sceneListMode: ListMode = ListMode.CARDS,
    val performerGender: PerformerGender = PerformerGender.BOTH,
    val whisparr: WhisparrDefaults = WhisparrDefaults(null, null),
    val historyCursor: Long? = null,
    /** Custom presets, without Default. */
    val presets: List<WhisparrPreset> = emptyList(),
    val tagRules: List<TagRule> = emptyList(),
    /** Browse search history: last 10, newest first. */
    val recentSearches: List<String> = emptyList(),
    val haptics: Boolean = true,
) {
    /** Default first, then the custom ones. */
    val allPresets: List<WhisparrPreset> get() = listOf(whisparr.asPreset()) + presets

    /** 4A: the first rule (list order) whose tag is on the scene and whose preset still exists; else Default with no rule. */
    fun resolvePreset(sceneTagIds: Collection<String>): Pair<WhisparrPreset, TagRule?> {
        for (rule in tagRules) {
            if (rule.stashTagId !in sceneTagIds) continue
            val preset = allPresets.firstOrNull { it.id == rule.presetId } ?: continue
            return preset to rule
        }
        return whisparr.asPreset() to null
    }

    /** Saves [preset] in place (Default goes to [whisparr]); a new id is appended. */
    fun withPreset(preset: WhisparrPreset): Settings = when {
        preset.id == DEFAULT_PRESET_ID -> copy(whisparr = preset.asDefaults())
        presets.any { it.id == preset.id } -> copy(presets = presets.map { if (it.id == preset.id) preset else it })
        else -> copy(presets = presets + preset)
    }

    /** Deletes a custom preset; its rules then point at Default. */
    fun withoutPreset(id: String): Settings = if (id == DEFAULT_PRESET_ID) {
        this
    } else {
        copy(presets = presets.filterNot { it.id == id }, tagRules = tagRules.map { if (it.presetId == id) it.copy(presetId = DEFAULT_PRESET_ID) else it })
    }
}

interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
