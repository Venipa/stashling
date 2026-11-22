package net.venipa.stashling.feature.update

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.venipa.stashling.BuildConfig
import net.venipa.stashling.R
import net.venipa.stashling.core.update.AppRelease
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.GlassRole
import net.venipa.stashling.ds.GlassState
import net.venipa.stashling.ds.SheetRadius
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.glass
import net.venipa.stashling.ds.sheetTitle
import net.venipa.stashling.update.AppUpdater
import net.venipa.stashling.update.UpdateFailure
import net.venipa.stashling.update.UpdateState

/** Pill height 56 with 8 padding inside the 28 glass corner: inner controls get 20 (concentric, fully round at 40 high). */
private val PillPad = 8.dp
private val PillInner = concentricShape(GlassCorner, PillPad)

/** Whether the floating update pill shows for this state (Idle never; Available unless "Later" was chosen). */
fun showsUpdatePill(
    state: UpdateState,
    hidden: Boolean,
): Boolean = when (state) {
    UpdateState.Idle -> false
    is UpdateState.Available -> !hidden
    else -> true
}

/**
 * A fully rounded glass pill floating above the dock (detached from it), on every tab root. While downloading it shows a
 * ring and the size with Cancel; tapping it opens the update sheet.
 */
@Composable
fun UpdatePill(
    updater: AppUpdater,
    glass: GlassState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by updater.state.collectAsState()
    val size = { b: Long -> Formatter.formatShortFileSize(context, b) }
    Row(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = Space.L)
            .glass(glass, GlassRole.Dock)
            .clickable(onClick = updater::openSheet)
            .heightIn(min = 56.dp)
            .padding(PillPad),
        horizontalArrangement = Arrangement.spacedBy(Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is UpdateState.Downloading -> CircularProgressIndicator(progress = { s.progress }, Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                is UpdateState.Failed -> Icon(StashlingIcons.Warning, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                else -> Icon(StashlingIcons.Download, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        val (title, body) =
            when (val s = state) {
                is UpdateState.Available -> stringResource(R.string.update_available_title, s.release.version) to stringResource(R.string.update_available_body)

                is UpdateState.Downloading ->
                    stringResource(R.string.update_downloading_title, s.release.version) to
                        stringResource(R.string.update_progress, (s.progress * 100).toInt(), size(s.read), size(s.total))

                is UpdateState.Ready ->
                    stringResource(R.string.update_ready_title, s.release.version) to
                        stringResource(if (s.needsPermission) R.string.update_permission_pill else R.string.update_ready_body)

                is UpdateState.Failed -> stringResource(R.string.update_failed_title) to stringResource(failureText(s.reason))

                UpdateState.Idle -> "" to ""
            }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val pad = PaddingValues(horizontal = Space.L)
        when (val s = state) {
            is UpdateState.Available -> Button(updater::openSheet, Modifier.height(40.dp), shape = PillInner, contentPadding = pad) { Text(stringResource(R.string.update_action)) }

            is UpdateState.Downloading -> TextButton(updater::cancel, Modifier.height(40.dp), shape = PillInner, contentPadding = pad) { Text(stringResource(R.string.update_cancel)) }

            is UpdateState.Ready ->
                Button(updater::install, Modifier.height(40.dp), shape = PillInner, contentPadding = pad) {
                    Text(stringResource(if (s.needsPermission) R.string.update_allow else R.string.update_install))
                }

            is UpdateState.Failed -> if (s.reason.retryable) TextButton(updater::download, Modifier.height(40.dp), shape = PillInner, contentPadding = pad) { Text(stringResource(R.string.update_retry)) }

            UpdateState.Idle -> Unit
        }
    }
}

private val UpdateFailure.retryable get() = this == UpdateFailure.DOWNLOAD || this == UpdateFailure.CHECKSUM || this == UpdateFailure.INSTALL

private fun failureText(reason: UpdateFailure) = when (reason) {
    UpdateFailure.DOWNLOAD -> R.string.update_fail_download
    UpdateFailure.CHECKSUM -> R.string.update_fail_checksum
    UpdateFailure.SIGNATURE -> R.string.update_fail_signature
    UpdateFailure.OTHER_APP -> R.string.update_fail_other_app
    UpdateFailure.INSTALL -> R.string.update_fail_install
}

/** The update sheet and the one-time install-permission sheet; host once over the whole app. */
@Composable
fun UpdateSheets(updater: AppUpdater) {
    val open by updater.sheetOpen.collectAsState()
    val permission by updater.permissionSheetOpen.collectAsState()
    val state by updater.state.collectAsState()
    val release = state.release
    if (open && release != null && !permission) UpdateSheet(updater, state, release)
    if (permission) PermissionSheet(updater)
}

@Composable
private fun UpdateSheet(
    updater: AppUpdater,
    state: UpdateState,
    release: AppRelease,
) = AppBottomSheet(updater::closeSheet) {
    val context = LocalContext.current
    val inner = concentricShape(SheetRadius, Space.L)
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.M)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
            // Decorative: both adaptive icon layers, clipped like a launcher icon (as on About).
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.update_sheet_title, release.version), style = sheetTitle)
                    if (release.prerelease) Text(stringResource(R.string.update_prerelease), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                val sizeText = Formatter.formatShortFileSize(context, release.apkSize)
                val meta =
                    release.publishedAt?.let {
                        val ago = DateUtils.getRelativeTimeSpanString(it.toEpochMilliseconds(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                        stringResource(R.string.update_sheet_meta, ago.replaceFirstChar(Char::lowercaseChar), sizeText, BuildConfig.VERSION_NAME)
                    } ?: stringResource(R.string.update_sheet_meta_nodate, sizeText, BuildConfig.VERSION_NAME)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Notes(release.notes, inner)
        if (state is UpdateState.Failed) Text(stringResource(failureText(state.reason)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        val big = Modifier.fillMaxWidth().height(56.dp)
        when (state) {
            is UpdateState.Downloading -> {
                FilledTonalButton({}, big, enabled = false, shape = inner) { Text(stringResource(R.string.update_downloading_button, (state.progress * 100).toInt())) }
                TextButton(updater::cancel, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.update_cancel)) }
            }

            is UpdateState.Ready ->
                Button({
                    updater.closeSheet()
                    updater.install()
                }, big, shape = inner) { Text(stringResource(R.string.update_install)) }

            else -> {
                if (state !is UpdateState.Failed || state.reason.retryable) {
                    // The sheet closes; the floating pill carries the progress.
                    Button({
                        updater.closeSheet()
                        updater.download()
                    }, big, shape = inner) { Text(stringResource(R.string.update_download_install)) }
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton(updater::skip) { Text(stringResource(R.string.update_skip)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(updater::later) { Text(stringResource(R.string.update_later)) }
                }
            }
        }
    }
}

/** Release notes in a tonal card; long notes clamp to 8 lines with More, and scroll inside the card when expanded. */
@Composable
private fun Notes(
    notes: String,
    shape: androidx.compose.ui.graphics.Shape,
) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.M),
        verticalArrangement = Arrangement.spacedBy(Space.Xs),
    ) {
        Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
        Text(
            notes.ifBlank { stringResource(R.string.update_no_notes) },
            Modifier.then(if (expanded) Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()) else Modifier),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(if (expanded) R.string.update_less else R.string.update_more)) }
        }
    }
}

@Composable
private fun PermissionSheet(updater: AppUpdater) = AppBottomSheet({ updater.permissionSheetOpen.value = false }) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.update_permission_title), style = sheetTitle)
        Text(stringResource(R.string.update_permission_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            {
                updater.permissionSheetOpen.value = false
                // Back in the app, AppUpdater.onResumed continues the install by itself.
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            },
            Modifier.fillMaxWidth().padding(top = Space.S).height(56.dp),
            shape = concentricShape(SheetRadius, Space.L),
        ) { Text(stringResource(R.string.update_permission_open)) }
        TextButton({ updater.permissionSheetOpen.value = false }, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.update_not_now)) }
    }
}
