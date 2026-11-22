package net.venipa.stashling.core.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// File datastore/settings.preferences_pb: backed up (no secrets in it).
private val Context.settingsData by preferencesDataStore(name = "settings")

class SettingsStoreImpl(context: Context) : SettingsStore {
    private val data = context.applicationContext.settingsData

    override val settings: Flow<Settings> = data.data.map { it.toSettings() }.distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings) {
        data.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val OLED = booleanPreferencesKey("oled_black")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SECURE = booleanPreferencesKey("secure_screen")
        val LOCK = booleanPreferencesKey("app_lock")
        val LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_sec")
        val LIST_MODE = stringPreferencesKey("scene_list_mode")
        val W_ROOT = stringPreferencesKey("whisparr_root_folder")
        val W_PROFILE = intPreferencesKey("whisparr_quality_profile_id")
        val W_MONITOR = stringPreferencesKey("whisparr_monitor")
        val W_SEARCH = booleanPreferencesKey("whisparr_search_on_add")
        val CURSOR = longPreferencesKey("history_cursor")

        // Presets and rules: small lists read with the rest of Settings, so JSON in DataStore (no Room table, no migration; backed up).
        val W_TAGS = stringPreferencesKey("whisparr_tag_ids")
        val PRESETS = stringPreferencesKey("whisparr_presets")
        val RULES = stringPreferencesKey("whisparr_tag_rules")
        val json = Json { ignoreUnknownKeys = true }

        // An unreadable list reads as empty rather than crashing every Settings read.
        fun <T> Preferences.list(key: Preferences.Key<String>, s: KSerializer<T>): List<T> = this[key]?.let {
            try {
                json.decodeFromString(ListSerializer(s), it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: emptyList()

        inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E = this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

        fun Preferences.toSettings(): Settings {
            val d = Settings()
            return Settings(
                theme = enum(THEME, d.theme),
                oledBlack = this[OLED] ?: d.oledBlack,
                dynamicColor = this[DYNAMIC] ?: d.dynamicColor,
                secureScreen = this[SECURE] ?: d.secureScreen,
                appLock = this[LOCK] ?: d.appLock,
                appLockTimeoutSec = this[LOCK_TIMEOUT] ?: d.appLockTimeoutSec,
                sceneListMode = enum(LIST_MODE, d.sceneListMode),
                whisparr =
                WhisparrDefaults(
                    rootFolder = this[W_ROOT],
                    qualityProfileId = this[W_PROFILE],
                    monitor = this[W_MONITOR] ?: d.whisparr.monitor,
                    searchOnAdd = this[W_SEARCH] ?: d.whisparr.searchOnAdd,
                    tagIds = list(W_TAGS, Int.serializer()),
                ),
                historyCursor = this[CURSOR],
                presets = list(PRESETS, WhisparrPreset.serializer()),
                tagRules = list(RULES, TagRule.serializer()),
            )
        }

        fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
            if (value == null) remove(key) else this[key] = value
        }

        fun MutablePreferences.write(s: Settings) {
            this[THEME] = s.theme.name
            this[OLED] = s.oledBlack
            this[DYNAMIC] = s.dynamicColor
            this[SECURE] = s.secureScreen
            this[LOCK] = s.appLock
            this[LOCK_TIMEOUT] = s.appLockTimeoutSec
            this[LIST_MODE] = s.sceneListMode.name
            setOrRemove(W_ROOT, s.whisparr.rootFolder)
            setOrRemove(W_PROFILE, s.whisparr.qualityProfileId)
            this[W_MONITOR] = s.whisparr.monitor
            this[W_SEARCH] = s.whisparr.searchOnAdd
            setOrRemove(CURSOR, s.historyCursor)
            this[W_TAGS] = json.encodeToString(ListSerializer(Int.serializer()), s.whisparr.tagIds)
            this[PRESETS] = json.encodeToString(ListSerializer(WhisparrPreset.serializer()), s.presets)
            this[RULES] = json.encodeToString(ListSerializer(TagRule.serializer()), s.tagRules)
        }
    }
}
