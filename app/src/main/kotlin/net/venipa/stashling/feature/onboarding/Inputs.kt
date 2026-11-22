package net.venipa.stashling.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.ds.StashlingIcons
import java.net.URI
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** https only, host required, no trailing slash; null = not acceptable (StashDB is never reached over http). */
internal fun normalizeStashUrl(input: String): String? {
    val t = input.trim().trimEnd('/')
    val uri = runCatching { URI(t) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null) return null
    return t
}

@StringRes
internal fun stashErrorText(e: Throwable): Int = when (e) {
    is StashError.Unauthorized -> R.string.onboarding_err_key_rejected
    is StashError.BadLogin -> R.string.onboarding_err_bad_login
    is StashError.Network -> if (e.cause is SSLException) R.string.onboarding_err_tls else R.string.onboarding_err_network
    is StashError.Http -> if (e.status >= 500) R.string.onboarding_err_try_later else R.string.onboarding_err_generic
    else -> R.string.onboarding_err_generic
}

@StringRes
internal fun whisparrErrorText(e: Throwable): Int = when (e) {
    is WhisparrError.Unauthorized -> R.string.onboarding_err_whisparr_key

    is WhisparrError.BadLogin -> R.string.onboarding_err_bad_login

    is WhisparrError.LoginRequired -> R.string.onboarding_err_whisparr_login_required

    is WhisparrError.Http -> if (e.status == 404) R.string.onboarding_err_not_whisparr else R.string.onboarding_err_generic

    is WhisparrError.Network ->
        when (e.cause) {
            is SSLException -> R.string.onboarding_err_whisparr_tls
            is UnknownServiceException -> R.string.onboarding_err_cleartext
            else -> R.string.onboarding_err_unreachable
        }

    else -> R.string.onboarding_err_not_whisparr
}

/** A text field; [secret] hides the value behind a show/hide toggle and keeps it out of autofill and suggestions. */
@Composable
internal fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    paste: Boolean = false,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    OutlinedTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType, autoCorrectEnabled = false),
        trailingIcon =
        if (secret || paste) {
            {
                Row {
                    if (paste) {
                        TextButton({
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { onValueChange(it.toString().trim()) }
                            }
                        }) { Text(stringResource(R.string.onboarding_paste)) }
                    }
                    if (secret) {
                        IconButton({ shown = !shown }) {
                            Icon(StashlingIcons.Visibility, stringResource(if (shown) R.string.onboarding_hide else R.string.onboarding_show), tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}
