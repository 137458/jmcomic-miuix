package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonPagingTest {

    private fun item(id: String = "1") = SearchResult(
        id = id,
        title = "标题$id",
        authors = emptyList(),
        tags = emptyList(),
        coverUrl = null,
    )

    @Test
    fun 空列表不展示底部分页或加载区() {
        assertFalse(shouldShowPagedFooter(PagedFeed<SearchResult>(items = emptyList(), page = 1, pageCount = 3, hasMore = true)))
        assertFalse(shouldShowPagedFooter(PagedFeed<SearchResult>(items = emptyList(), page = 1, pageCount = null, hasMore = true)))
    }

    @Test
    fun 单页且无下一页时不展示底部分页或加载区() {
        assertFalse(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 1, pageCount = 1, hasMore = false)))
        assertFalse(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 1, pageCount = null, hasMore = false)))
    }

    @Test
    fun 无总页数但仍有下一页时必须展示底部分页或加载区() {
        assertTrue(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 1, pageCount = null, hasMore = true)))
    }

    @Test
    fun 无总页数已翻到后续页即使到底也展示底部分页或加载区() {
        assertTrue(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 2, pageCount = null, hasMore = false)))
    }

    @Test
    fun 多页有总页数时无论是否到底均展示底部分页或加载区() {
        assertTrue(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 1, pageCount = 5, hasMore = true)))
        assertTrue(shouldShowPagedFooter(PagedFeed(items = listOf(item()), page = 5, pageCount = 5, hasMore = false)))
    }
}
