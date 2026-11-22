package net.venipa.stashling.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationManagerCompat
import net.venipa.stashling.StashlingApp
import java.io.File

/** Writes the APK into a PackageInstaller session; the result arrives in [UpdateReceiver]. */
object UpdateInstaller {
    const val ACTION_STATUS = "net.venipa.stashling.update.INSTALL_STATUS"
    const val ACTION_SKIP = "net.venipa.stashling.update.SKIP"
    const val EXTRA_TAG = "tag"

    fun install(
        context: Context,
        file: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                // Skips the prompt only where Android allows it (this app installed the current version); otherwise it still asks.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Mutable: the installer adds the status extras. Explicit component, so no other app can receive it.
                val status = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
                val pending = PendingIntent.getBroadcast(context, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            // A half-written session would stay staged until Android expires it.
            installer.abandonSession(id)
            throw e
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val updater = (context.applicationContext as StashlingApp).container.updater
        when (intent.action) {
            UpdateInstaller.ACTION_STATUS ->
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    // Android's own install prompt; the app is in front when this arrives (it just started the install).
                    PackageInstaller.STATUS_PENDING_USER_ACTION ->
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }

                    // The new version replaces this process; aborted = prompt dismissed: stay Ready so the pill offers Install again.
                    PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

                    else -> updater.onInstallFailed()
                }

            UpdateInstaller.ACTION_SKIP -> {
                intent.getStringExtra(UpdateInstaller.EXTRA_TAG)?.let(updater::skipTag)
                NotificationManagerCompat.from(context).cancel(UpdateNotifications.ID)
            }
        }
    }
}
