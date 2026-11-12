package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseWeeklyStateTest {

    private fun result(id: String) = SearchResult(
        id = id,
        title = "title-$id",
        authors = emptyList(),
        tags = emptyList(),
        coverUrl = null,
    )

    @Test
    fun `loadWeekly Ok clears weeklyFailure so empty state remains reachable`() {
        val outcome = SessionOutcome.Ok(SearchPage(currentPage = 1, totalPages = 1, items = emptyList()))

        val next = resolveWeeklyLoad(currentFeed = PagedFeed(), outcome = outcome)

        assertNull(next.failure)
        assertTrue(next.feed.items.isEmpty())
    }

    @Test
    fun `loadWeekly Expired and Failed populate weeklyFailure while preserving existing feed`() {
        val existing = PagedFeed(items = listOf(result("1")), page = 1, hasMore = false)

        val expired = resolveWeeklyLoad(existing, SessionOutcome.Expired)
        assertEquals(ScreenLoadPhase.Expired, expired.failure?.phase)
        assertEquals(LOGIN_EXPIRED_MESSAGE, expired.failure?.message)
        assertEquals(existing, expired.feed)

        val failed = resolveWeeklyLoad(existing, SessionOutcome.Failed("boom"))
        assertEquals(ScreenLoadPhase.Failed, failed.failure?.phase)
        assertEquals("每周更新加载失败", failed.failure?.message)
        assertEquals(existing, failed.feed)
    }

    @Test
    fun `loadMoreWeekly preserves Expired instead of degrading to generic failure`() {
        val requested = PagedFeed(items = listOf(result("1")), page = 1, pageCount = 2, hasMore = true)
            .beginLoadMore()

        val expired = resolveWeeklyLoadMore(requested, SessionOutcome.Expired)

        assertFalse(expired.feed.loadingMore)
        assertTrue(expired.feed.loadMoreFailed)
        assertEquals(ScreenLoadPhase.Expired, expired.failure?.phase)
        assertEquals(LOGIN_EXPIRED_MESSAGE, expired.failure?.message)
    }

    @Test
    fun `loadMoreWeekly Ok appends items and clears failure`() {
        val requested = PagedFeed(items = listOf(result("1")), page = 1, pageCount = 2, hasMore = true)
            .beginLoadMore()
        val page2 = SearchPage(currentPage = 2, totalPages = 2, items = listOf(result("2")))

        val ok = resolveWeeklyLoadMore(requested, SessionOutcome.Ok(page2))

        assertNull(ok.failure)
        assertFalse(ok.feed.loadingMore)
        assertFalse(ok.feed.loadMoreFailed)
        assertEquals(listOf("1", "2"), ok.feed.items.map { it.id })
    }

    @Test
    fun `out-of-bounds enum tab index returns null with getOrNull`() {
        assertNull(BrowseTime.entries.getOrNull(-1))
        assertNull(BrowseTime.entries.getOrNull(BrowseTime.entries.size))
        assertNull(BrowseOrder.entries.getOrNull(BrowseOrder.entries.size))
        assertNull(BrowseCategory.entries.getOrNull(BrowseCategory.entries.size))
    }
}
