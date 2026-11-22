package net.venipa.stashling.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Tab roots (alone on the stack).
@Serializable data object HomeRoute : NavKey

@Serializable data object BrowseRoute : NavKey

@Serializable data object SavedRoute : NavKey

@Serializable data object DownloadsRoute : NavKey

// Pushed routes (dock hidden). Ids are StashDB uuids.

/** [only] = -1: the full flow from the welcome step; 1 = only StashDB, 2 = only Whisparr (re-run from Settings, then back). */
@Serializable data class OnboardingRoute(val only: Int = -1) : NavKey

@Serializable data class SettingsRoute(val section: SettingsSection = SettingsSection.Menu) : NavKey

@Serializable data class SceneDetailRoute(val id: String) : NavKey

@Serializable data class PerformerDetailRoute(val id: String) : NavKey

@Serializable data class StudioDetailRoute(val id: String) : NavKey

@Serializable data class TagDetailRoute(val id: String) : NavKey

/** [kind] is "scene", "performer" or "studio"; [id] the owner of the images; [index] the page to open. */
@Serializable data class GalleryRoute(val kind: String, val id: String, val index: Int = 0) : NavKey

@Serializable
enum class SettingsSection { Menu, Appearance, Privacy, Accounts, WhisparrDefaults, Storage, About }
