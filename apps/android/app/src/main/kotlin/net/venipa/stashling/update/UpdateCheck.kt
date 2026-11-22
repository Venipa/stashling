package net.venipa.stashling.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.venipa.stashling.MainActivity
import net.venipa.stashling.R
import net.venipa.stashling.StashlingApp
import net.venipa.stashling.core.update.AppRelease
import java.util.concurrent.TimeUnit

/** Daily background check; notifies once per new release. */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StashlingApp).container
        val settings = container.settings.settings.first()
        if (!settings.updateAuto) return Result.success()
        val release = container.updater.check(manual = false) ?: return Result.success()
        if (release.tag != settings.updateNotified) {
            UpdateNotifications.show(applicationContext, release)
            container.settings.update { it.copy(updateNotified = release.tag) }
        }
        return Result.success()
    }
}

object UpdateCheckScheduler {
    private const val NAME = "update_check"

    /** Arms or cancels the daily check whenever Settings.updateAuto changes. */
    fun bind(
        context: Context,
        auto: Flow<Boolean>,
        scope: CoroutineScope,
    ) {
        val app = context.applicationContext
        scope.launch {
            auto.distinctUntilChanged().collect { on ->
                val wm = WorkManager.getInstance(app)
                if (on) {
                    val request =
                        PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                    wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
                } else {
                    wm.cancelUniqueWork(NAME)
                }
            }
        }
    }
}

object UpdateNotifications {
    const val CHANNEL = "updates"
    const val ID = 7_100

    /** Silently skipped without POST_NOTIFICATIONS. Tap and Update open the update sheet (stashling://update). */
    fun show(
        context: Context,
        release: AppRelease,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.update_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open =
            PendingIntent.getActivity(
                context,
                ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = "stashling://update".toUri()
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val skip =
            PendingIntent.getBroadcast(
                context,
                ID,
                Intent(context, UpdateReceiver::class.java).setAction(UpdateInstaller.ACTION_SKIP).putExtra(UpdateInstaller.EXTRA_TAG, release.tag),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val firstLine = release.notes.lineSequence().map { it.removePrefix("• ").trim() }.firstOrNull { it.isNotEmpty() }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.downloads_ic_notification)
                .setContentTitle(context.getString(R.string.update_available_title, release.version))
                .setContentText(firstLine ?: context.getString(R.string.update_notify_text))
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.update_action), open)
                .addAction(0, context.getString(R.string.update_skip_short), skip)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}
