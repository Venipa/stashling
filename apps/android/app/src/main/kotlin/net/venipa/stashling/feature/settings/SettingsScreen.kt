package net.venipa.stashling.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.HiddenKind
import net.venipa.stashling.core.data.ListMode
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.ThemeMode
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.ScreenColumn
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.SettingsSection

/** Settings: a menu of rows (icon, title, current value), each opening a pushed sub-page. [onRerun] 1 = StashDB, 2 = Whisparr onboarding step. */
@Composable
fun SettingsScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
    onRerun: (Int) -> Unit,
    tab: Boolean = false,
) {
    val container = LocalAppContainer.current
    val hasUpdate = container.updater.state.collectAsState().value !is net.venipa.stashling.update.UpdateState.Idle
    // Not rendered until the stored values are in: controls must not animate from defaults to the real values.
    val settings = container.settingsState.collectAsState().value ?: return
    val whisparr by container.secrets.whisparr.collectAsState()
    val stash by container.secrets.stash.collectAsState()
    val hidden by remember(container) { container.hidden.observe() }.collectAsState(emptyList())
    when (section) {
        SettingsSection.Menu -> {
            val theme = stringResource(
                when (settings.theme) {
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                    ThemeMode.DARK -> R.string.settings_theme_dark
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                },
            )
            val on = stringResource(R.string.settings_on)
            val off = stringResource(R.string.settings_off)
            SettingsPage(stringResource(R.string.settings_title), if (tab) null else onBack) {
                val store = container.settings
                val scope = rememberCoroutineScope()
                SectionHeader(stringResource(R.string.settings_group_connections), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(stringResource(R.string.onboarding_stashdb), supporting = stash?.baseUrl?.hostOf(), icon = StashlingIcons.Person, onClick = { onOpen(SettingsSection.StashDb) })
                    GroupRow(
                        stringResource(R.string.settings_whisparr),
                        supporting = whisparr?.let {
                            listOf(
                                it.baseUrl.hostOf(),
                                pluralStringResource(R.plurals.settings_whisparr_presets_value, settings.allPresets.size, settings.allPresets.size),
                                pluralStringResource(R.plurals.settings_whisparr_rules_menu_value, settings.tagRules.size, settings.tagRules.size),
                            ).joinToString(" · ")
                        } ?: stringResource(R.string.onboarding_not_connected),
                        icon = StashlingIcons.Download,
                        onClick = { onOpen(SettingsSection.Whisparr) },
                    )
                }
                SectionHeader(stringResource(R.string.settings_group_content), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(
                        stringResource(R.string.settings_browsing),
                        supporting = stringResource(R.string.settings_browsing_value, stringResource(settings.performerGender.menuRes()), stringResource(if (settings.sceneListMode == ListMode.CARDS) R.string.browse_layout_cards else R.string.browse_layout_rows)),
                        icon = StashlingIcons.Search,
                        onClick = { onOpen(SettingsSection.Browsing) },
                    )
                    GroupRow(
                        stringResource(R.string.settings_hidden),
                        supporting = stringResource(R.string.settings_hidden_value, hidden.count { it.kind == HiddenKind.SCENE }, hidden.count { it.kind == HiddenKind.PERFORMER }),
                        icon = BrowseIcons.VisibilityOff,
                        onClick = { onOpen(SettingsSection.Hidden) },
                    )
                }
                SectionHeader(stringResource(R.string.settings_appearance), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(
                        stringResource(R.string.settings_theme_colour),
                        supporting = stringResource(R.string.settings_theme_value, theme, stringResource(if (settings.dynamicColor) R.string.settings_dynamic else R.string.settings_default_colour)),
                        icon = StashlingIcons.Palette,
                        onClick = { onOpen(SettingsSection.Appearance) },
                    )
                    GroupRow(
                        stringResource(R.string.settings_haptics),
                        supporting = stringResource(R.string.settings_haptics_body),
                        icon = StashlingIcons.Vibration,
                        trailing = { StashSwitch(settings.haptics, { v -> scope.launch { store.update { it.copy(haptics = v) } } }) },
                        onClick = { scope.launch { store.update { it.copy(haptics = !it.haptics) } } },
                    )
                }
                SectionHeader(stringResource(R.string.settings_group_privacy), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(
                        stringResource(R.string.settings_privacy),
                        supporting = stringResource(R.string.settings_privacy_value, if (settings.secureScreen) on else off, if (settings.appLock) on else off),
                        icon = StashlingIcons.Privacy,
                        onClick = { onOpen(SettingsSection.Privacy) },
                    )
                }
                SectionHeader(stringResource(R.string.settings_group_data), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(stringResource(R.string.settings_backup), supporting = stringResource(R.string.settings_backup_value), icon = StashlingIcons.Upload, onClick = { onOpen(SettingsSection.Backup) })
                    GroupRow(stringResource(R.string.settings_image_cache), supporting = stringResource(R.string.settings_image_cache_value), icon = StashlingIcons.ClearCache, onClick = { onOpen(SettingsSection.ImageCache) })
                }
                SectionHeader(stringResource(R.string.settings_about), Gutter)
                TonalGroup(Gutter) {
                    GroupRow(stringResource(R.string.settings_about_title), supporting = stringResource(if (hasUpdate) R.string.settings_about_update else R.string.settings_about_value, net.venipa.stashling.BuildConfig.VERSION_NAME), icon = StashlingIcons.Info, onClick = { onOpen(SettingsSection.About) })
                }
            }
        }

        SettingsSection.Appearance -> AppearancePage(settings, onBack)

        SettingsSection.Privacy -> PrivacyPage(settings, onBack)

        SettingsSection.StashDb -> AccountsPage(onBack, onRerun)

        SettingsSection.Whisparr -> WhisparrSettings(onBack, onRerun)

        SettingsSection.Browsing -> BrowsingPage(settings, hidden.size, onBack, onOpen)

        SettingsSection.Hidden -> HiddenPage(hidden, onBack)

        SettingsSection.ImageCache -> StoragePage(onBack)

        SettingsSection.Backup -> BackupPage(onBack)

        SettingsSection.About -> AboutPage(onBack)
    }
}

/** A settings page: medium top app bar that collapses on scroll, the body a [ScreenColumn]; [snackbar] hosts the page's messages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                { Text(title) },
                navigationIcon = { onBack?.let { b -> IconButton(b) { Icon(StashlingIcons.Back, stringResource(R.string.settings_back)) } } },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) { d -> AppSnackbar(d) } } },
    ) { padding: PaddingValues -> ScreenColumn(padding, bottom = Space.L + LocalDockInset.current, content = content) }
}

/** "host:port" of a stored base URL. */
private fun String.hostOf() = substringAfter("://").substringBefore("/")
