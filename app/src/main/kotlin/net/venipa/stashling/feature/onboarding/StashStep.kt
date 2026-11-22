package net.venipa.stashling.feature.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.StashAccount
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.Loading
import net.venipa.stashling.ds.StashSegmented

private const val DEFAULT_URL = "https://stashdb.org"

/** Step 1: StashDB. API key (default) or username and password (exchanged for a key; the password is discarded). */
@Composable
internal fun StashStep(
    state: OnboardingState,
    finishLabel: String?,
    onNext: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(0) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf(DEFAULT_URL) }
    var username by rememberSaveable { mutableStateOf("") }
    // Secrets: plain remember, never saved state.
    var apiKey by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val verified = state.stash != null

    fun submit() {
        if (verified && apiKey.isBlank() && password.isEmpty()) return onNext()
        val base = normalizeStashUrl(if (custom) url else DEFAULT_URL)
        error =
            when {
                base == null -> R.string.onboarding_err_url_https
                mode == 0 && apiKey.isBlank() -> R.string.onboarding_err_key_empty
                mode == 1 && (username.isBlank() || password.isEmpty()) -> R.string.onboarding_err_login_empty
                else -> null
            }
        if (error != null || base == null) return
        busy = true
        scope.launch {
            try {
                val key = if (mode == 0) apiKey.trim() else container.stash.login(base, username.trim(), password)
                val me = container.stash.verify(base, key)
                password = ""
                state.stash = StashAccount(base, key)
                state.stashName = me.name
                onNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = stashErrorText(e)
            } finally {
                busy = false
            }
        }
    }

    StepScaffold(
        stringResource(R.string.onboarding_stash_title),
        stringResource(R.string.onboarding_stash_body),
        actions = {
            ActionButton(finishLabel ?: stringResource(R.string.onboarding_continue), ::submit, enabled = !busy && (verified || mode == 1 || apiKey.isNotBlank()))
        },
    ) {
        StashSegmented(
            listOf(stringResource(R.string.onboarding_mode_key), stringResource(R.string.onboarding_mode_login)),
            mode,
            {
                mode = it
                error = null
            },
        )
        if (mode == 0) {
            CredentialField(apiKey, {
                apiKey = it
                error = null
            }, stringResource(R.string.onboarding_api_key), secret = true, paste = true, isError = error != null)
        } else {
            CredentialField(username, {
                username = it
                error = null
            }, stringResource(R.string.onboarding_username), isError = error != null)
            CredentialField(password, {
                password = it
                error = null
            }, stringResource(R.string.onboarding_password), secret = true, isError = error != null)
        }
        if (custom) {
            CredentialField(url, {
                url = it
                error = null
            }, stringResource(R.string.onboarding_server_url), keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, isError = error == R.string.onboarding_err_url_https)
        } else {
            TextButton({ custom = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_custom_server)) }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (busy) Loading()
    }
}
