package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.toFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页搜索区的会话缓存：搜索结果按 [SessionListKey.HomeSearch] 这一格存取，清空搜索词即作废。
 *
 * 界面是否切到搜索内容列由页面自己的查询词状态决定，这里只钉住缓存这一侧：存进去能原样取回、
 * 离开路由再回来能直接复用、清空后不再命中。
 */
class HomeSearchStateTest {

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
            )
        ),
        totalItems = 1,
    )

    /** 缓存里存的是整份分页状态：页面按 [fakePage] 的第一页装成 feed，刷成 remember 后的快照形态。 */
    private val fakeFeed = fakePage.toFeed()

    @Test
    fun 执行搜索并取得结果后搜索结果存入会话缓存() {
        val cache = SessionCache()
        val results = ListState.Loaded(fakeFeed)
        cache.remember(SessionListKey.HomeSearch, results)

        assertTrue(cache.hasPage(SessionListKey.HomeSearch))
        assertEquals(results, cache.stateOrNull(SessionListKey.HomeSearch))
    }

    @Test
    fun 从详情页返回时能够直接复用会话缓存中的搜索结果无需重新网络请求() {
        val cache = SessionCache()
        // 模拟搜索完成并存入会话缓存
        val initialResults = ListState.Loaded(fakeFeed)
        cache.remember(SessionListKey.HomeSearch, initialResults)

        // 模拟离开路由再返回：由会话缓存恢复结果
        val restoredResults = cache.stateOrNull(SessionListKey.HomeSearch)
        assertNotNull(restoredResults)
        assertEquals(initialResults, restoredResults)
        assertEquals(fakeFeed, cache.read(SessionListKey.HomeSearch))
    }

    @Test
    fun 清空搜索词时会话缓存被清空() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeSearch, ListState.Loaded(fakeFeed))
        cache.forget(SessionListKey.HomeSearch)

        assertNull(cache.stateOrNull(SessionListKey.HomeSearch))
        assertFalse(cache.hasPage(SessionListKey.HomeSearch))
    }
}
