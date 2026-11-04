package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionCacheTest {

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

    private fun browseKey(
        mode: BrowseMode = BrowseMode.CATEGORY,
        category: String = "all",
        order: String = "mr",
        time: String = "all",
    ) = SessionListKey.Browse(BrowsePageKey(mode, category, order, time))

    @Test
    fun 加载成功的结果能按身份取回() {
        val cache = SessionCache()

        cache.remember(browseKey(), ListState.Loaded(fakePage))

        assertEquals(ListState.Loaded(fakePage), cache.stateOf(browseKey()))
        assertTrue(cache.hasPage(browseKey()))
    }

    @Test
    fun 筛选条件变了就不认旧结果页面要重新请求() {
        val cache = SessionCache()
        cache.remember(browseKey(category = "all"), ListState.Loaded(fakePage))

        assertFalse(cache.hasPage(browseKey(category = "doujin")))
        assertEquals(ListState.Loading, cache.stateOf(browseKey(category = "doujin")))
    }

    @Test
    fun 分类与榜单两种模式各存各的互不覆盖() {
        val cache = SessionCache()
        val rankPage = fakePage.copy(currentPage = 2)
        cache.remember(browseKey(), ListState.Loaded(fakePage))
        cache.remember(browseKey(mode = BrowseMode.RANK), ListState.Loaded(rankPage))

        assertEquals(ListState.Loaded(fakePage), cache.stateOf(browseKey()))
        assertEquals(ListState.Loaded(rankPage), cache.stateOf(browseKey(mode = BrowseMode.RANK)))
    }

    @Test
    fun 加载失败不进缓存回来时按未加载处理() {
        val cache = SessionCache()
        cache.remember(browseKey(), ListState.Loaded(fakePage))
        cache.remember(browseKey(), ListState.Failed("网络不可用"))

        assertFalse(cache.hasPage(browseKey()))
        assertEquals(ListState.Loading, cache.stateOf(browseKey()))
    }

    @Test
    fun 清空搜索结果不影响发现流那一格() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakePage))
        cache.remember(SessionListKey.HomeSearch, ListState.Loaded(fakePage))

        cache.forget(SessionListKey.HomeSearch)

        assertNull(cache.stateOrNull(SessionListKey.HomeSearch))
        assertEquals(ListState.Loaded(fakePage), cache.stateOf(SessionListKey.HomeFeed))
    }

    @Test
    fun 首页与浏览页的缓存互不串格() {
        val cache = SessionCache()
        val rankPage = fakePage.copy(currentPage = 3)
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakePage))
        cache.remember(browseKey(), ListState.Loaded(rankPage))

        assertEquals(ListState.Loaded(fakePage), cache.stateOf(SessionListKey.HomeFeed))
        assertEquals(ListState.Loaded(rankPage), cache.stateOf(browseKey()))
    }

    @Test
    fun 侧栏数据整份替换并且能一次清空() {
        val cache = SessionCache()
        assertTrue(cache.homeSide.isEmpty)

        cache.homeSide = HomeSideData(
            hotTags = listOf("热血"),
            serialUpdates = fakePage.items,
            randomPicks = fakePage.items,
        )
        assertEquals(listOf("热血"), cache.homeSide.hotTags)
        assertFalse(cache.homeSide.isEmpty)

        cache.clear()

        assertTrue(cache.homeSide.isEmpty)
        assertEquals(ListState.Loading, cache.stateOf(SessionListKey.HomeFeed))
    }
}
