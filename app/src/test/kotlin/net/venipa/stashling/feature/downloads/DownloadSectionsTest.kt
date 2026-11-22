package net.venipa.stashling.feature.downloads

import net.venipa.stashling.core.whisparr.HistoryItem
import net.venipa.stashling.core.whisparr.QueueItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DownloadSectionsTest {
    private fun q(id: Int, status: String, state: String? = null, size: Double = 100.0, left: Double = 50.0) = QueueItem(id, movieId = id, size = size, sizeleft = left, status = status, trackedDownloadState = state)

    private fun h(id: Int, movie: Int, event: String) = HistoryItem(id, movie, "t", Instant.fromEpochMilliseconds(id.toLong()), event)

    @Test
    fun groupsQueueAndHistory() {
        val s =
            downloadSections(
                listOf(q(1, "downloading"), q(2, "queued"), q(3, "warning", "failedPending"), q(4, "downloading", size = 300.0, left = 0.0)),
                // newest first: movie 7 failed then got imported (not failed); movie 8 failed; movie 3 is still queued.
                listOf(h(10, 7, "downloadFolderImported"), h(9, 7, "downloadFailed"), h(8, 8, "downloadFailed"), h(7, 3, "downloadFailed")),
            )
        assertEquals(listOf(1, 4), s.downloading.map { it.id })
        assertEquals(listOf(2), s.queued.map { it.id })
        assertEquals(listOf(3), s.failed.map { it.id })
        assertEquals(listOf(8), s.failedHistory.map { it.movieId })
        assertEquals(listOf(7), s.imported.map { it.movieId })
        assertEquals(1 - 50.0 / 400.0, s.progress)
    }
}
