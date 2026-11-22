package net.venipa.stashling.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.venipa.stashling.StashlingApp
import net.venipa.stashling.core.whisparr.WhisparrError

/** Whisparr history since the cursor → one notification per grabbed / imported / failed scene. */
class DownloadWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StashlingApp).container
        if (container.secrets.whisparr.value == null) return Result.success()
        // container.whisparr is built asynchronously at process start; a cold worker may get here first.
        val repo = container.whisparr.value ?: withTimeoutOrNull(10_000) { container.whisparr.filterNotNull().first() } ?: return Result.retry()
        return try {
            repo.newEvents().forEach { DownloadNotifications.show(applicationContext, it) }
            if (repo.queue().isNotEmpty()) DownloadWatchScheduler.followUp(applicationContext)
            Result.success()
        } catch (_: WhisparrError) {
            // Server down or key revoked: a few retries, then wait for the next period.
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }
}
