package net.venipa.stashling.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.venipa.stashling.core.data.WhisparrAccount
import java.util.concurrent.TimeUnit

/** Download watch: 15 min periodic while Whisparr is connected, plus a 3 min follow-up while the queue has items. */
object DownloadWatchScheduler {
    private const val NAME = "download_watch"
    private const val NAME_FOLLOW_UP = "download_watch_follow_up"
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Call once at app start: (re)arms or cancels the watch whenever the Whisparr account appears or goes. */
    fun bind(
        context: Context,
        accounts: Flow<WhisparrAccount?>,
        scope: CoroutineScope,
    ) {
        val app = context.applicationContext
        scope.launch { accounts.map { it != null }.distinctUntilChanged().collect { if (it) start(app) else cancel(app) } }
    }

    fun start(context: Context) {
        val request = PeriodicWorkRequestBuilder<DownloadWatchWorker>(15, TimeUnit.MINUTES).setConstraints(connected).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(NAME)
            cancelUniqueWork(NAME_FOLLOW_UP)
        }
    }

    /** Sooner check while something downloads; REPLACE so only one is pending. */
    fun followUp(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<DownloadWatchWorker>()
                .setInitialDelay(3, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME_FOLLOW_UP, ExistingWorkPolicy.REPLACE, request)
    }
}
