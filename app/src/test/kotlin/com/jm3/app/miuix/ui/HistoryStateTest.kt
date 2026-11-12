package com.jm3.app.miuix.ui

import com.jm3.core.history.HistoryEntry
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryStateTest {

    private fun entry(photoId: String, albumId: String, updatedAt: Long = 100L) = HistoryEntry(
        photoId = photoId,
        albumId = albumId,
        title = "Title-$albumId",
        coverUrl = null,
        updatedAt = updatedAt,
    )

    private fun feedOf(vararg entries: HistoryEntry, page: Int = 1): PagedFeed<HistoryEntry> =
        PagedFeed<HistoryEntry>().replace(
            PageSlice(items = entries.toList(), hasMore = false, page = page),
        ) { it.photoId }

    @Test
    fun `removeAlbumFromFeed recomputes against latest feed so concurrent additions are preserved`() {
        val latest = feedOf(
            entry("p1", "a1"),
            entry("p2", "a2"),
            entry("p3", "a3"),
        )

        val result = removeAlbumFromFeed(latest, "a1")

        assertEquals(listOf("p2", "p3"), result.feed.items.map { it.photoId })
        assertEquals(listOf("p1"), result.slice.removed.map { it.photoId })
    }

    @Test
    fun `restoreAlbumToFeed rejects undo when requestToken has changed due to paging or clear`() {
        val currentPage2 = feedOf(entry("p10", "a10"), page = 2)
        val staleRemoved = listOf(entry("p1", "a1"))

        val restored = restoreAlbumToFeed(
            latestFeed = currentPage2,
            removed = staleRemoved,
            insertIndex = 0,
            requestTokenAtDelete = 1,
            currentRequestToken = 2,
        )

        assertNull(restored)
    }

    @Test
    fun `restoreAlbumToFeed restores items when requestToken matches`() {
        val current = feedOf(entry("p2", "a2"))
        val removed = listOf(entry("p1", "a1"))

        val restored = restoreAlbumToFeed(
            latestFeed = current,
            removed = removed,
            insertIndex = 0,
            requestTokenAtDelete = 3,
            currentRequestToken = 3,
        )

        assertEquals(listOf("p1", "p2"), restored?.items?.map { it.photoId })
    }

    @Test
    fun `clearAll failure keeps existing feed and state and requests failure snackbar`() {
        val existingFeed = feedOf(entry("p1", "a1"), entry("p2", "a2"))

        val outcome = resolveClearAllOutcome(
            currentFeed = existingFeed,
            currentState = HistoryListState.Loaded,
            outcome = Result.failure(IllegalStateException("db error")),
            requestTokenAtClear = 5,
            currentRequestToken = 5,
        )

        assertEquals(existingFeed, outcome.feed)
        assertEquals(HistoryListState.Loaded, outcome.state)
        assertTrue(outcome.showFailureSnackbar)
    }

    @Test
    fun `clearAll success clears feed and updates state when token matches`() {
        val existingFeed = feedOf(entry("p1", "a1"))

        val outcome = resolveClearAllOutcome(
            currentFeed = existingFeed,
            currentState = HistoryListState.Loading,
            outcome = Result.success(Unit),
            requestTokenAtClear = 5,
            currentRequestToken = 5,
        )

        assertTrue(outcome.feed.items.isEmpty())
        assertEquals(HistoryListState.Loaded, outcome.state)
        assertFalse(outcome.showFailureSnackbar)
    }
}
