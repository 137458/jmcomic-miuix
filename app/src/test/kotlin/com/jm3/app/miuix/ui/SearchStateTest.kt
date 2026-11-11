package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchStateTest {

    @Test
    fun okOutcomeMapsToLoadedFeed() {
        val page = SearchPage(
            currentPage = 1,
            totalPages = 3,
            items = listOf(
                SearchResult(
                    id = "100",
                    title = "Sample",
                    authors = listOf("Author"),
                    tags = listOf("Tag"),
                    coverUrl = null,
                ),
            ),
            totalItems = 1,
        )

        val state = searchStateOf(SessionOutcome.Ok(page), failureMessage = "搜索失败")

        val loaded = assertIs<SearchUiState.Loaded>(state)
        assertEquals(1, loaded.feed.items.size)
        assertEquals("100", loaded.feed.items.single().id)
    }

    @Test
    fun expiredOutcomeMarksSessionExpiredTrue() {
        val state = searchStateOf(SessionOutcome.Expired, failureMessage = "搜索失败")

        val failed = assertIs<SearchUiState.Failed>(state)
        assertTrue(failed.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, failed.message)
    }

    @Test
    fun failedOutcomeUsesFallbackMessageWithoutMarkingExpired() {
        val state = searchStateOf(SessionOutcome.Failed("raw server error"), failureMessage = "搜索失败")

        val failed = assertIs<SearchUiState.Failed>(state)
        assertFalse(failed.sessionExpired)
        assertEquals("搜索失败", failed.message)
    }

    @Test
    fun timeoutOutcomeMapsToTimeoutMessageWithoutMarkingExpired() {
        val state = searchStateOf(
            SessionOutcome.Failed("timed out", java.net.SocketTimeoutException("timeout")),
            failureMessage = "搜索失败",
        )

        val failed = assertIs<SearchUiState.Failed>(state)
        assertFalse(failed.sessionExpired)
        assertEquals("网络超时，请稍后重试", failed.message)
    }
}
