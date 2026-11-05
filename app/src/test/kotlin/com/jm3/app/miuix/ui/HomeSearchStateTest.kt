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
    fun 初始状态无搜索词时处于发现流模式() {
        val activeQuery = ""
        val results: ListState? = null
        assertFalse(isShowingSearchResults(activeQuery, results))
    }

    @Test
    fun 执行搜索并取得结果后处于搜索内容列模式() {
        val cache = SessionCache()
        val activeQuery = "火影"
        val results = ListState.Loaded(fakeFeed)
        cache.remember(SessionListKey.HomeSearch, results)

        assertTrue(isShowingSearchResults(activeQuery, results))
        assertTrue(cache.hasPage(SessionListKey.HomeSearch))
        assertEquals(results, cache.stateOrNull(SessionListKey.HomeSearch))
    }

    @Test
    fun 从详情页返回时即使results瞬态为null只要activeQuery非空仍处于搜索内容列模式() {
        // 旧逻辑如果按 results != null 判断，在恢复但 results 尚未被赋值或缓存空时会误判为 false（跳回主页）
        // 新逻辑必须保证 activeQuery 非空时绝不退回主页发现流
        val activeQuery = "火影"
        val results: ListState? = null
        assertTrue(isShowingSearchResults(activeQuery, results))
    }

    @Test
    fun 从详情页返回时能够直接复用会话缓存中的搜索结果无需重新网络请求() {
        val cache = SessionCache()
        // 模拟搜索完成并存入会话缓存
        val activeQuery = "火影"
        val initialResults = ListState.Loaded(fakeFeed)
        cache.remember(SessionListKey.HomeSearch, initialResults)

        // 模拟离开路由再返回：由会话缓存恢复结果
        val restoredResults = cache.stateOrNull(SessionListKey.HomeSearch)
        assertNotNull(restoredResults)
        assertEquals(initialResults, restoredResults)
        assertTrue(isShowingSearchResults(activeQuery, restoredResults))
    }

    @Test
    fun 清空搜索词时会话缓存被清空且退出搜索内容列模式() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeSearch, ListState.Loaded(fakeFeed))
        cache.forget(SessionListKey.HomeSearch)

        assertNull(cache.stateOrNull(SessionListKey.HomeSearch))
        assertFalse(cache.hasPage(SessionListKey.HomeSearch))

        val activeQuery = ""
        val results = cache.stateOrNull(SessionListKey.HomeSearch)
        assertFalse(isShowingSearchResults(activeQuery, results))
    }
}
