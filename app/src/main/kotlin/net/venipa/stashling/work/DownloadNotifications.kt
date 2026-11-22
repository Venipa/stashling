package net.venipa.stashling.work

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
import net.venipa.stashling.MainActivity
import net.venipa.stashling.R
import net.venipa.stashling.core.whisparr.HistoryItem

object DownloadNotifications {
    const val CHANNEL = "downloads"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.downloads_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Silently skipped without POST_NOTIFICATIONS; one notification per scene (a later event replaces the earlier one). */
    fun show(
        context: Context,
        event: HistoryItem,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title =
            when (event.eventType) {
                "grabbed" -> R.string.downloads_notify_grabbed
                "downloadFolderImported" -> R.string.downloads_notify_imported
                else -> R.string.downloads_notify_failed
            }
        val stashId = event.movie?.let { it.stashId ?: it.foreignId }
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (stashId != null) data = "stashling://scene/$stashId".toUri()
            }
        val pending = PendingIntent.getActivity(context, event.movieId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ensureChannel(context)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.downloads_ic_notification)
                .setContentTitle(context.getString(title))
                .setContentText(event.movie?.title ?: event.sourceTitle)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setWhen(event.date.toEpochMilliseconds())
                .setShowWhen(true)
                .build()
        NotificationManagerCompat.from(context).notify(event.movieId, notification)
    }
}
