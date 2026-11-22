package net.venipa.stashling.feature.settings

import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.venipa.stashling.BuildConfig
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.ThemeMode
import net.venipa.stashling.core.data.WhisparrDefaults
import net.venipa.stashling.core.whisparr.QualityProfile
import net.venipa.stashling.core.whisparr.RootFolder
import net.venipa.stashling.core.whisparr.WhisparrClients
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.ConfirmSheet
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.LoadingSkeleton
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.ds.rememberRevealAnchor
import net.venipa.stashling.feature.lock.authenticate
import net.venipa.stashling.feature.lock.canAuthenticate

@Composable
internal fun AppearancePage(
    settings: Settings,
    onBack: () -> Unit,
) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val themes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    val segAnchor = rememberRevealAnchor()
    val oledAnchor = rememberRevealAnchor()
    val dynAnchor = rememberRevealAnchor()
    SettingsPage(stringResource(R.string.settings_appearance), onBack) {
        StashSegmented(
            listOf(R.string.settings_theme_light, R.string.settings_theme_dark, R.string.settings_theme_system).map { stringResource(it) },
            themes.indexOf(settings.theme),
            { i ->
                // The circle starts at the tapped segment.
                val b = segAnchor.bounds
                segAnchor.reveal(Offset(b.left + b.width * (i + 0.5f) / themes.size, b.center.y)) { scope.launch { store.update { it.copy(theme = themes[i]) } } }
            },
            Gutter.then(segAnchor.modifier),
        )
        TonalGroup(Gutter) {
            GroupRow(
                stringResource(R.string.settings_oled),
                supporting = stringResource(R.string.settings_oled_body),
                trailing = { Switch(settings.oledBlack, { v -> oledAnchor.reveal { scope.launch { store.update { it.copy(oledBlack = v) } } } }, oledAnchor.modifier) },
                onClick = { oledAnchor.reveal { scope.launch { store.update { it.copy(oledBlack = !it.oledBlack) } } } },
            )
            GroupRow(
                stringResource(R.string.settings_dynamic),
                supporting = stringResource(R.string.settings_dynamic_body),
                trailing = { Switch(settings.dynamicColor, { v -> dynAnchor.reveal { scope.launch { store.update { it.copy(dynamicColor = v) } } } }, dynAnchor.modifier) },
                onClick = { dynAnchor.reveal { scope.launch { store.update { it.copy(dynamicColor = !it.dynamicColor) } } } },
            )
        }
    }
}

@Composable
internal fun PrivacyPage(
    settings: Settings,
    onBack: () -> Unit,
) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current as? FragmentActivity
    val snackbar = remember { SnackbarHostState() }
    val noLock = stringResource(R.string.settings_lock_unavailable)
    val prompt = stringResource(R.string.settings_lock_prompt)
    val timeouts = listOf(0, 30, 60, 300)

    fun setLock(on: Boolean) {
        if (!on) {
            scope.launch { store.update { it.copy(appLock = false) } }
            return
        }
        // Enabling proves the prompt works first, so the lock can never lock the user out.
        if (activity == null || !canAuthenticate(activity)) {
            scope.launch { snackbar.showSnackbar(noLock) }
            return
        }
        authenticate(activity, prompt, onSuccess = { scope.launch { store.update { it.copy(appLock = true) } } })
    }
    SettingsPage(stringResource(R.string.settings_privacy), onBack, snackbar) {
        TonalGroup(Gutter) {
            GroupRow(
                stringResource(R.string.settings_secure_screen),
                supporting = stringResource(R.string.settings_secure_screen_body),
                trailing = { Switch(settings.secureScreen, { v -> scope.launch { store.update { it.copy(secureScreen = v) } } }) },
                onClick = { scope.launch { store.update { it.copy(secureScreen = !it.secureScreen) } } },
            )
            GroupRow(
                stringResource(R.string.settings_app_lock),
                supporting = stringResource(R.string.settings_app_lock_body),
                trailing = { Switch(settings.appLock, ::setLock) },
                onClick = { setLock(!settings.appLock) },
            )
        }
        if (settings.appLock) {
            SectionHeader(stringResource(R.string.settings_lock_after), Gutter)
            StashSegmented(
                listOf(R.string.settings_lock_now, R.string.settings_lock_30s, R.string.settings_lock_1m, R.string.settings_lock_5m).map { stringResource(it) },
                timeouts.indexOf(settings.appLockTimeoutSec).takeIf { it >= 0 } ?: 2,
                { i -> scope.launch { store.update { it.copy(appLockTimeoutSec = timeouts[i]) } } },
                Gutter,
            )
        }
    }
}

@Composable
internal fun AccountsPage(
    onBack: () -> Unit,
    onRerun: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val stash by container.secrets.stash.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.settings_accounts), onBack) {
        SectionHeader(stringResource(R.string.onboarding_stashdb), Gutter)
        TonalGroup(Gutter) {
            GroupRow(stringResource(R.string.settings_sign_in_again), supporting = stash?.baseUrl, onClick = { onRerun(1) })
            GroupRow(stringResource(R.string.settings_disconnect), supporting = stringResource(R.string.settings_disconnect_stash_body), onClick = { confirm = true })
        }
        SectionHeader(stringResource(R.string.onboarding_whisparr), Gutter)
        WhisparrAccountGroup(onRerun)
    }
    if (confirm) {
        ConfirmSheet(
            stringResource(R.string.settings_disconnect_stash_title),
            stringResource(R.string.settings_disconnect_stash_confirm),
            stringResource(R.string.settings_disconnect),
            onConfirm = {
                confirm = false
                // Signing out of StashDB sends the shell back to onboarding.
                scope.launch { container.secrets.setStash(null) }
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
internal fun StoragePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loader = remember { SingletonImageLoader.get(context) }
    var size by remember { mutableStateOf<Long?>(null) }
    val cleared = stringResource(R.string.settings_cache_cleared)
    LaunchedEffect(Unit) { size = withContext(Dispatchers.IO) { loader.diskCache?.size } }
    SettingsPage(stringResource(R.string.settings_storage), onBack, snackbar) {
        TonalGroup(Gutter) {
            GroupRow(
                stringResource(R.string.settings_clear_cache),
                supporting = size?.let { Formatter.formatFileSize(context, it) } ?: stringResource(R.string.settings_cache_body),
                icon = StashlingIcons.ClearCache,
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            loader.memoryCache?.clear()
                            loader.diskCache?.clear()
                            size = loader.diskCache?.size
                        }
                        snackbar.showSnackbar(cleared)
                    }
                },
            )
        }
    }
}

@Composable
internal fun AboutPage(onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.settings_about), onBack) {
        TonalGroup(Gutter) {
            GroupRow(stringResource(R.string.app_name), supporting = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))
            GroupRow(stringResource(R.string.settings_licence), supporting = stringResource(R.string.settings_licence_value))
        }
        Text(stringResource(R.string.settings_about_body), Gutter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
