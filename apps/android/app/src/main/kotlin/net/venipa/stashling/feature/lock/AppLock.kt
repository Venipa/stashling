package net.venipa.stashling.feature.lock

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.StashlingIcons

private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/** True when the device has a biometric or a screen lock to prompt with. */
fun canAuthenticate(activity: FragmentActivity) = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun authenticate(
    activity: FragmentActivity,
    title: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {},
) {
    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) = onFailure()
            },
        )
    // Device credential as fallback: no negative button allowed with it.
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTHENTICATORS).build())
}

/**
 * Locks on start-up and when the app returns after [Settings.appLockTimeoutSec] in the background, while [Settings.appLock] is on.
 * One per process (AppContainer) so a configuration change doesn't lock again; each activity adds it as a lifecycle observer.
 */
class AppLockController(
    settings: Flow<Settings>,
    scope: CoroutineScope,
) : DefaultLifecycleObserver {
    /** Until the first settings value arrives the screen stays covered. */
    var locked by mutableStateOf(true)
        private set
    var loaded by mutableStateOf(false)
        private set
    private var current: Settings? = null
    private var stoppedAt = 0L

    init {
        scope.launch {
            settings.collect {
                current = it
                if (!loaded) {
                    locked = it.appLock
                    loaded = true
                } else if (!it.appLock) {
                    locked = false
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if ((owner as? Activity)?.isChangingConfigurations == true) return
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val s = current ?: return
        if (s.appLock && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= s.appLockTimeoutSec * 1000L) locked = true
    }

    fun unlock() {
        locked = false
        stoppedAt = 0
    }
}

/** Opaque cover while locked; prompts on show and again from the button. */
@Composable
fun LockOverlay(controller: AppLockController) {
    if (!controller.locked) return
    val activity = LocalActivity.current as? FragmentActivity
    BackHandler { activity?.moveTaskToBack(true) }
    val title = stringResource(R.string.settings_lock_prompt)
    var failed by remember { mutableStateOf(false) }
    fun prompt() {
        activity?.let { authenticate(it, title, onSuccess = controller::unlock, onFailure = { failed = true }) }
    }
    val store = LocalAppContainer.current.settings
    LaunchedEffect(controller.loaded) {
        if (!controller.loaded) return@LaunchedEffect
        // Screen lock removed since the lock was turned on: prompting would fail forever and lock the user out.
        if (activity != null && !canAuthenticate(activity)) {
            store.update { it.copy(appLock = false) }
            controller.unlock()
        } else {
            prompt()
        }
    }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        if (controller.loaded) {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
                Icon(StashlingIcons.Privacy, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_lock_locked), style = MaterialTheme.typography.titleLarge)
                if (failed) Text(stringResource(R.string.settings_lock_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionButton(stringResource(R.string.settings_lock_unlock), ::prompt)
            }
        }
    }
}
