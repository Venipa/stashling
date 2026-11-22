package net.venipa.stashling.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.venipa.stashling.R
import net.venipa.stashling.core.data.WhisparrAccount
import net.venipa.stashling.core.data.WhisparrDefaults
import net.venipa.stashling.core.whisparr.WhisparrClients
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrLogin
import net.venipa.stashling.core.whisparr.normalizeWhisparrUrl
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.Loading
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.feature.settings.WhisparrDefaultsEditor

/**
 * Step 2, optional. One form: address, then credentials only when Whisparr asks (initialize.json is tried first), with
 * "Ignore certificate errors" under Advanced. After a verified connection the four defaults are editable rows.
 */
@Composable
internal fun WhisparrStep(
    state: OnboardingState,
    finishLabel: String?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var needCreds by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(0) }
    var username by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var ignoreCert by rememberSaveable { mutableStateOf(false) }
    // Secrets: plain remember, never saved state.
    var apiKey by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var setLater by remember { mutableStateOf(false) }
    val account = state.whisparr
    val badUrl = stringResource(R.string.onboarding_err_whisparr_url)
    val tooOld = stringResource(R.string.onboarding_err_whisparr_version)

    fun connect() {
        val base =
            try {
                normalizeWhisparrUrl(url)
            } catch (e: IllegalArgumentException) {
                error = badUrl
                errorRes = null
                return
            }
        if (needCreds && mode == 0 && apiKey.isBlank()) {
            return run {
                errorRes = R.string.onboarding_err_key_empty
                error = null
            }
        }
        if (needCreds && mode == 1 && (username.isBlank() || password.isEmpty())) {
            return run {
                errorRes = R.string.onboarding_err_login_empty
                error = null
            }
        }
        busy = true
        error = null
        errorRes = null
        scope.launch {
            try {
                val key =
                    when {
                        needCreds && mode == 0 -> apiKey.trim()
                        needCreds -> WhisparrLogin.fetchApiKey(base, username.trim(), password, ignoreCert)
                        else -> WhisparrLogin.fetchApiKey(base, null, null, ignoreCert)
                    }
                password = ""
                val acc = WhisparrAccount(base, key, ignoreCert)
                val api = WhisparrClients.create(acc)
                val status = api.status()
                if ((status.version.substringBefore('.').toIntOrNull() ?: 0) < 3) {
                    error = tooOld
                    return@launch
                }
                // Defaults are a convenience: if either list fails the step still connects and the defaults are set later.
                val roots = runCatching { api.rootFolders().filter { it.accessible } }.getOrNull()
                val profiles = runCatching { api.qualityProfiles() }.getOrNull()
                setLater = roots == null || profiles == null
                state.roots = roots.orEmpty()
                state.profiles = profiles.orEmpty()
                state.defaults = WhisparrDefaults(roots?.firstOrNull()?.path, profiles?.firstOrNull()?.id)
                state.whisparr = acc
            } catch (e: CancellationException) {
                throw e
            } catch (e: WhisparrError.LoginRequired) {
                needCreds = true
                errorRes = R.string.onboarding_err_whisparr_login_required
            } catch (e: Exception) {
                errorRes = whisparrErrorText(e)
            } finally {
                busy = false
            }
        }
    }

    StepScaffold(
        stringResource(R.string.onboarding_whisparr_title),
        stringResource(if (account == null) R.string.onboarding_whisparr_body else R.string.onboarding_whisparr_defaults_body),
        actions = {
            if (account == null) {
                ActionButton(stringResource(R.string.onboarding_connect), ::connect, enabled = !busy && url.isNotBlank())
                TextButton(onSkip, Modifier.fillMaxWidth()) { Text(stringResource(if (finishLabel != null) R.string.onboarding_cancel else R.string.onboarding_skip)) }
            } else {
                ActionButton(finishLabel ?: stringResource(R.string.onboarding_continue), onNext)
            }
        },
    ) {
        if (account == null) {
            CredentialField(url, {
                url = it
                error = null
                errorRes = null
            }, stringResource(R.string.onboarding_whisparr_url), keyboardType = KeyboardType.Uri, isError = error != null)
            if (needCreds) {
                StashSegmented(
                    listOf(stringResource(R.string.onboarding_mode_key), stringResource(R.string.onboarding_mode_signin)),
                    mode,
                    {
                        mode = it
                        error = null
                        errorRes = null
                    },
                )
                if (mode == 0) {
                    CredentialField(apiKey, { apiKey = it }, stringResource(R.string.onboarding_api_key), secret = true, paste = true)
                } else {
                    CredentialField(username, { username = it }, stringResource(R.string.onboarding_username))
                    CredentialField(password, { password = it }, stringResource(R.string.onboarding_password), secret = true)
                }
            }
            TextButton({ advanced = !advanced }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_advanced)) }
            if (advanced) {
                Row(Modifier.fillMaxWidth().clickable { ignoreCert = !ignoreCert }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ignoreCert, { ignoreCert = it })
                    Text(stringResource(R.string.onboarding_ignore_cert), style = MaterialTheme.typography.bodyLarge)
                }
                if (ignoreCert) {
                    TonalCard(tone = CardTone.Error) { Text(stringResource(R.string.onboarding_ignore_cert_warning), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            (error ?: errorRes?.let { stringResource(it) })?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (busy) Loading()
        } else {
            Text(stringResource(R.string.onboarding_connected_to, account.baseUrl), style = MaterialTheme.typography.titleMedium)
            val defaults = state.defaults ?: WhisparrDefaults(null, null)
            WhisparrDefaultsEditor(state.roots, state.profiles, defaults) { state.defaults = it }
            if (setLater) Text(stringResource(R.string.onboarding_defaults_later), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
