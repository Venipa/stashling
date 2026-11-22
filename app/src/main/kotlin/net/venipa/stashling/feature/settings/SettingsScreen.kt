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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.ThemeMode
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.ScreenColumn
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
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
    val settings by container.settings.settings.collectAsState(Settings())
    val whisparr by container.secrets.whisparr.collectAsState()
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
                TonalGroup(Gutter) {
                    GroupRow(stringResource(R.string.settings_appearance), supporting = theme, icon = StashlingIcons.Palette, onClick = { onOpen(SettingsSection.Appearance) })
                    GroupRow(
                        stringResource(R.string.settings_privacy),
                        supporting = stringResource(R.string.settings_privacy_value, if (settings.secureScreen) on else off, if (settings.appLock) on else off),
                        icon = StashlingIcons.Privacy,
                        onClick = { onOpen(SettingsSection.Privacy) },
                    )
                    GroupRow(
                        stringResource(R.string.settings_accounts),
                        supporting = stringResource(if (whisparr != null) R.string.settings_accounts_both else R.string.settings_accounts_stash),
                        icon = StashlingIcons.Person,
                        onClick = { onOpen(SettingsSection.Accounts) },
                    )
                    if (whisparr != null) {
                        GroupRow(
                            stringResource(R.string.settings_whisparr),
                            supporting = stringResource(R.string.settings_whisparr_menu_value, settings.allPresets.size, settings.tagRules.size),
                            icon = StashlingIcons.Download,
                            onClick = { onOpen(SettingsSection.WhisparrDefaults) },
                        )
                    }
                    GroupRow(stringResource(R.string.settings_storage), supporting = stringResource(R.string.settings_storage_value), icon = StashlingIcons.ClearCache, onClick = { onOpen(SettingsSection.Storage) })
                    GroupRow(stringResource(R.string.settings_about), supporting = stringResource(R.string.settings_about_value, net.venipa.stashling.BuildConfig.VERSION_NAME), icon = StashlingIcons.Info, onClick = { onOpen(SettingsSection.About) })
                }
            }
        }

        SettingsSection.Appearance -> AppearancePage(settings, onBack)

        SettingsSection.Privacy -> PrivacyPage(settings, onBack)

        SettingsSection.Accounts -> AccountsPage(onBack, onRerun)

        SettingsSection.WhisparrDefaults -> WhisparrSettings(onBack, onRerun)

        SettingsSection.Storage -> StoragePage(onBack)

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
