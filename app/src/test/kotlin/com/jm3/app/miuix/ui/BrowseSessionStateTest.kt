package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseSessionStateTest {

    private val fakePage = SearchPage(
        currentPage = 1,
        totalPages = 2,
        items = listOf(
            SearchResult(
                id = "1001",
                title = "火影同人",
                authors = listOf("岸本"),
                tags = listOf("热血"),
                coverUrl = "https://cover/1001.jpg",
            ),
        ),
        totalItems = 1,
    )

    private fun key(
        mode: BrowseMode = BrowseMode.CATEGORY,
        category: String = "all",
        order: String = "mr",
        time: String = "all",
    ) = BrowsePageKey(mode, category, order, time)

    @BeforeTest
    fun setUp() {
        BrowseSessionCache.reset()
    }

    @Test
    fun 同一口径加载成功后切页回来能取回上次的结果() {
        BrowseSessionCache.remember(key(), BrowseUiState.Loaded(fakePage))

        assertEquals(BrowseUiState.Loaded(fakePage), BrowseSessionCache.stateFor(key()))
        assertTrue(BrowseSessionCache.hasPage(key()))
    }

    @Test
    fun `筛选条件变了就不认旧结果，页面要重新请求`() {
        BrowseSessionCache.remember(key(category = "all"), BrowseUiState.Loaded(fakePage))

        assertFalse(BrowseSessionCache.hasPage(key(category = "doujin")))
        assertEquals(BrowseUiState.Loading, BrowseSessionCache.stateFor(key(category = "doujin")))
    }

    @Test
    fun 分类与榜单两种模式各存各的互不覆盖() {
        val rankPage = fakePage.copy(currentPage = 2)
        BrowseSessionCache.remember(key(), BrowseUiState.Loaded(fakePage))
        BrowseSessionCache.remember(key(mode = BrowseMode.RANK), BrowseUiState.Loaded(rankPage))

        assertEquals(BrowseUiState.Loaded(fakePage), BrowseSessionCache.stateFor(key()))
        assertEquals(
            BrowseUiState.Loaded(rankPage),
            BrowseSessionCache.stateFor(key(mode = BrowseMode.RANK)),
        )
    }

    @Test
    fun `加载失败不进缓存，回来时按未加载处理`() {
        BrowseSessionCache.remember(key(), BrowseUiState.Loaded(fakePage))
        BrowseSessionCache.remember(key(), BrowseUiState.Failed("网络不可用"))

        assertFalse(BrowseSessionCache.hasPage(key()))
        assertEquals(BrowseUiState.Loading, BrowseSessionCache.stateFor(key()))
    }
}
