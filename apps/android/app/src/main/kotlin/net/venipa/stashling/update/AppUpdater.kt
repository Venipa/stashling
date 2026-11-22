package net.venipa.stashling.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.venipa.stashling.BuildConfig
import net.venipa.stashling.core.data.SettingsStore
import net.venipa.stashling.core.update.AppRelease
import net.venipa.stashling.core.update.GithubReleases
import net.venipa.stashling.core.update.UpdateError
import net.venipa.stashling.core.update.isNewerVersion
import java.io.File

sealed interface UpdateState {
    /** The release this state is about; null when Idle. */
    val release: AppRelease? get() = null

    /** Nothing to offer: never checked, up to date, skipped or dismissed. */
    data object Idle : UpdateState

    data class Available(override val release: AppRelease) : UpdateState

    data class Downloading(
        override val release: AppRelease,
        val read: Long,
        val total: Long,
    ) : UpdateState {
        val progress: Float get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Downloaded and verified; waiting for Android's "install unknown apps" switch ([needsPermission]) or its install prompt. */
    data class Ready(
        override val release: AppRelease,
        val file: File,
        val needsPermission: Boolean,
    ) : UpdateState

    data class Failed(
        override val release: AppRelease,
        val reason: UpdateFailure,
    ) : UpdateState
}

enum class UpdateFailure { DOWNLOAD, CHECKSUM, SIGNATURE, OTHER_APP, INSTALL }

/**
 * The in-app updater for GitHub releases: check, download to cache, verify (checksum, same package, same signing key),
 * then hand the APK to Android's PackageInstaller. App-scoped so a download survives leaving the screen.
 */
class AppUpdater(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val api: GithubReleases = GithubReleases(REPO),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** A check is running (About shows "Checking…"). */
    val checking = MutableStateFlow(false)

    /** The last check could not reach GitHub. */
    val checkFailed = MutableStateFlow(false)

    /** "Later": the pill stays hidden for this process; a manual check or a new release shows it again. */
    val hiddenForSession = MutableStateFlow(false)
    val sheetOpen = MutableStateFlow(false)
    val permissionSheetOpen = MutableStateFlow(false)

    private var job: Job? = null
    private var openWhenFound = false
    private val dir get() = File(context.cacheDir, "updates")

    /** Returns the newer release when one is offered. Automatic checks respect a skipped tag; manual ones show it anyway. */
    suspend fun check(manual: Boolean): AppRelease? {
        // Debug builds are another package with another key: they can never install a release, so they don't nag.
        if (BuildConfig.DEBUG && !manual) return null
        if (busy()) return null
        checking.value = true
        try {
            val s = settings.settings.first()
            val release = api.latest(s.updatePrerelease)
            settings.update { it.copy(updateLastCheck = System.currentTimeMillis()) }
            checkFailed.value = false
            // A download may have started while GitHub answered: leave it (and its file) alone.
            if (busy()) return null
            val newer = release?.takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) && (manual || it.tag != s.updateSkipped) }
            if (newer == null) {
                _state.value = UpdateState.Idle
                dir.deleteRecursively() // leftovers from an installed or abandoned update
            } else {
                val previous = (_state.value as? UpdateState.Available)?.release?.tag
                _state.value = UpdateState.Available(newer)
                if (manual || previous != newer.tag) hiddenForSession.value = false
                if (openWhenFound) sheetOpen.value = true
            }
            return newer
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            checkFailed.value = true
            return null
        } finally {
            checking.value = false
            openWhenFound = false
        }
    }

    /** Opens the sheet; with nothing known yet (cold start from the notification) it checks first and opens when one is found. */
    fun openSheet() {
        if (_state.value !is UpdateState.Idle) {
            sheetOpen.value = true
            return
        }
        openWhenFound = true
        scope.launch { check(manual = true) }
    }

    fun closeSheet() {
        sheetOpen.value = false
    }

    private fun busy() = _state.value is UpdateState.Downloading || _state.value is UpdateState.Ready

    fun download() {
        val s = _state.value
        if (s !is UpdateState.Available && s !is UpdateState.Failed) return
        val release = s.release ?: return
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val me = coroutineContext[Job]

                // Only the current download writes state: a cancelled or replaced one may still report once.
                fun set(next: UpdateState) {
                    if (job === me) _state.value = next
                }
                val file = File(dir, "stashling-${release.version}.apk")
                set(UpdateState.Downloading(release, 0, release.apkSize))
                try {
                    var lastEmit = 0L
                    api.download(release, file) { read, total ->
                        val now = SystemClock.uptimeMillis()
                        if (now - lastEmit > 150 || read >= total) {
                            lastEmit = now
                            set(UpdateState.Downloading(release, read, total))
                        }
                    }
                    val problem = verify(file)
                    if (problem != null) {
                        file.delete()
                        set(UpdateState.Failed(release, problem))
                        return@launch
                    }
                    set(UpdateState.Ready(release, file, needsPermission = false))
                    if (job === me) install()
                } catch (e: CancellationException) {
                    file.delete()
                    throw e
                } catch (e: UpdateError.Checksum) {
                    set(UpdateState.Failed(release, UpdateFailure.CHECKSUM))
                } catch (e: Exception) {
                    set(UpdateState.Failed(release, UpdateFailure.DOWNLOAD))
                }
            }
        job?.start()
    }

    fun cancel() {
        val release = (_state.value as? UpdateState.Downloading)?.release ?: return
        val running = job
        job = null
        running?.cancel()
        _state.value = UpdateState.Available(release)
    }

    /** Starts the install, or asks for Android's per-app install switch first. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = ready.copy(needsPermission = true)
            permissionSheetOpen.value = true
            return
        }
        _state.value = ready.copy(needsPermission = false)
        permissionSheetOpen.value = false
        scope.launch(Dispatchers.IO) {
            runCatching { UpdateInstaller.install(context, ready.file) }
                .onFailure { _state.value = UpdateState.Failed(ready.release, UpdateFailure.INSTALL) }
        }
    }

    /** Back from Android's settings (or any resume): continue an install that waited for the switch. */
    fun onResumed() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (ready.needsPermission && context.packageManager.canRequestPackageInstalls()) install()
    }

    internal fun onInstallFailed() {
        (_state.value as? UpdateState.Ready)?.let { _state.value = UpdateState.Failed(it.release, UpdateFailure.INSTALL) }
    }

    fun skip() {
        val s = _state.value
        if (s is UpdateState.Available || s is UpdateState.Failed) s.release?.let { skipTag(it.tag) }
    }

    internal fun skipTag(tag: String) {
        scope.launch { settings.update { it.copy(updateSkipped = tag) } }
        val s = _state.value
        if ((s is UpdateState.Available || s is UpdateState.Failed) && s.release?.tag == tag) _state.value = UpdateState.Idle
        sheetOpen.value = false
    }

    fun later() {
        hiddenForSession.value = true
        sheetOpen.value = false
    }

    /** Problem with the downloaded APK, or null when it is this app, signed with the installed app's key. */
    @Suppress("DEPRECATION")
    private fun verify(file: File): UpdateFailure? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return UpdateFailure.SIGNATURE
        if (info.packageName != context.packageName) return UpdateFailure.OTHER_APP
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        val sameKey = signers.isNotEmpty() && signers.all { pm.hasSigningCertificate(context.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509) }
        return if (sameKey) null else UpdateFailure.SIGNATURE
    }

    companion object {
        const val REPO = "Venipa/stashling"
    }
}
