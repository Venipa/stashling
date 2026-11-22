package net.venipa.stashling.feature.settings

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Backup
import net.venipa.stashling.core.data.BackupCodec
import net.venipa.stashling.core.data.BackupException
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SheetRadius
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.sheetTitle
import net.venipa.stashling.feature.lock.canAuthenticate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// A backup is a few hundred KB at most; anything far bigger is not one (and must not be read into memory).
private const val MAX_BYTES = 10 * 1024 * 1024

/** Settings → Backup: export to / import from a JSON file the user picks (SAF). Errors show inline above the rows. */
@Composable
internal fun BackupPage(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val haptic = LocalStashHaptics.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<String?>(null) }
    var exportSheet by remember { mutableStateOf(false) }
    var includeAccounts by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Backup?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.backup_saved)
    val imported = stringResource(R.string.backup_imported)
    val errWrite = stringResource(R.string.backup_err_write)
    val errRead = stringResource(R.string.backup_err_read)
    val errInvalid = stringResource(R.string.backup_err_invalid)
    val errNewer = stringResource(R.string.backup_err_newer)
    val errImport = stringResource(R.string.backup_err_import)

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = BackupCodec.encode(container.backup.export(includeAccounts, System.currentTimeMillis()))
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
                haptic.confirm()
                snackbar.showSnackbar(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errWrite
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = try {
                pending = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readNBytes(MAX_BYTES + 1)
                        if (bytes.size > MAX_BYTES) throw BackupException(BackupException.Reason.Invalid)
                        bytes.decodeToString()
                    }
                    BackupCodec.decode(text)
                }
                importError = null
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                if (e.reason == BackupException.Reason.Newer) errNewer else errInvalid
            } catch (e: Exception) {
                errRead
            }
        }
    }

    SettingsPage(stringResource(R.string.settings_backup), onBack, snackbar) {
        error?.let { msg ->
            TonalCard(Gutter, tone = CardTone.Error) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
                    Icon(StashlingIcons.Error, null)
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        TonalGroup(Gutter) {
            GroupRow(
                stringResource(R.string.backup_export),
                supporting = stringResource(R.string.backup_export_body),
                icon = StashlingIcons.Download,
                onClick = {
                    error = null
                    includeAccounts = false
                    exportSheet = true
                },
            )
            GroupRow(
                stringResource(R.string.backup_import),
                supporting = stringResource(R.string.backup_import_body),
                icon = StashlingIcons.Upload,
                onClick = {
                    error = null
                    // Some file managers label .json as text or octet-stream.
                    open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
        }
    }

    if (exportSheet) {
        BackupSheet(stringResource(R.string.backup_export), onDismiss = { exportSheet = false }) {
            Text(stringResource(R.string.backup_export_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SheetGroup {
                GroupRow(
                    stringResource(R.string.backup_include_accounts),
                    supporting = stringResource(R.string.backup_include_accounts_body),
                    trailing = { StashSwitch(includeAccounts, { includeAccounts = it }) },
                    onClick = { includeAccounts = !includeAccounts },
                )
            }
            if (includeAccounts) Warning(stringResource(R.string.backup_accounts_warning))
            SheetButtons(stringResource(R.string.backup_export_action), onCancel = { exportSheet = false }) {
                exportSheet = false
                create.launch("stashling-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
            }
        }
    }

    pending?.let { backup ->
        val lockAllowed = activity != null && canAuthenticate(activity)
        ImportSheet(backup, importing, importError, onDismiss = { if (!importing) pending = null }) { replace, accounts ->
            importing = true
            importError = null
            // The page's scope, not the sheet's: closing the sheet must not cancel the import or its snackbar.
            scope.launch {
                try {
                    container.backup.import(backup, replace, accounts, lockAllowed)
                    container.stash.invalidate()
                    pending = null
                    haptic.confirm()
                    snackbar.showSnackbar(imported)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    importError = errImport
                } finally {
                    importing = false
                }
            }
        }
    }
}

/** Summary, Merge | Replace, accounts switch (only when the file has them). */
@Composable
private fun ImportSheet(
    backup: Backup,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (replace: Boolean, accounts: Boolean) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    var accounts by remember { mutableStateOf(false) }
    val hasAccounts = backup.accounts?.let { it.stash != null || it.whisparr != null } == true
    BackupSheet(stringResource(R.string.backup_import), onDismiss = onDismiss) {
        Text(
            stringResource(
                R.string.backup_summary,
                backup.bookmarks.size,
                backup.hidden.size,
                backup.settings.presets.size,
                backup.settings.tagRules.size,
                stringResource(if (hasAccounts) R.string.backup_yes else R.string.backup_no),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        StashSegmented(listOf(stringResource(R.string.backup_merge), stringResource(R.string.backup_replace)), mode, { mode = it })
        Text(
            stringResource(if (mode == 0) R.string.backup_merge_body else R.string.backup_replace_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasAccounts) {
            SheetGroup {
                GroupRow(
                    stringResource(R.string.backup_restore_accounts),
                    supporting = stringResource(R.string.backup_restore_accounts_body),
                    trailing = { StashSwitch(accounts, { accounts = it }) },
                    onClick = { accounts = !accounts },
                )
            }
        }
        error?.let { Warning(it) }
        SheetButtons(stringResource(R.string.backup_import_action), onCancel = onDismiss, busy = busy, busyText = stringResource(R.string.backup_importing)) { onImport(mode == 1, accounts) }
    }
}

@Composable
private fun BackupSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = AppBottomSheet(onDismiss) {
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.M)) {
        Text(title, style = sheetTitle)
        content()
    }
}

/** Rows concentric to the sheet (sheet radius minus its 16 dp padding). */
@Composable
private fun SheetGroup(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth().clip(concentricShape(SheetRadius, Space.L)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)

@Composable
private fun Warning(text: String) = Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.Top) {
    Icon(StashlingIcons.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SheetButtons(
    confirm: String,
    onCancel: () -> Unit,
    busy: Boolean = false,
    busyText: String? = null,
    onConfirm: () -> Unit,
) = Row(Modifier.padding(top = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
    val shape = concentricShape(SheetRadius, Space.L)
    ActionButton(stringResource(net.venipa.stashling.ds.R.string.ds_cancel), onCancel, Modifier.weight(1f), enabled = !busy, tonal = true, shape = shape)
    ActionButton(confirm, onConfirm, Modifier.weight(1f), shape = shape, loading = busy, loadingText = busyText)
}
