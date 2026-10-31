package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import kotlin.test.BeforeTest
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

    @BeforeTest
    fun setUp() {
        HomeSessionCache.reset()
    }

    @Test
    fun 初始状态无搜索词时处于发现流模式() {
        val activeQuery = ""
        val results: HomeListState? = null
        assertFalse(isShowingSearchResults(activeQuery, results))
    }

    @Test
    fun 执行搜索并取得结果后处于搜索内容列模式() {
        val activeQuery = "火影"
        val results = HomeListState.Loaded(fakePage)
        HomeSessionCache.updateResults(results)

        assertTrue(isShowingSearchResults(activeQuery, results))
        assertTrue(HomeSessionCache.hasSearchResults)
        assertEquals(results, HomeSessionCache.results)
    }

    @Test
    fun 从详情页返回时即使results瞬态为null只要activeQuery非空仍处于搜索内容列模式() {
        // 旧逻辑如果按 results != null 判断，在恢复但 results 尚未被赋值或缓存空时会误判为 false（跳回主页）
        // 新逻辑必须保证 activeQuery 非空时绝不退回主页发现流
        val activeQuery = "火影"
        val results: HomeListState? = null
        assertTrue(isShowingSearchResults(activeQuery, results))
    }

    @Test
    fun 从详情页返回时能够直接复用HomeSessionCache中的搜索结果无需重新网络请求() {
        // 模拟搜索完成并存入会话缓存
        val activeQuery = "火影"
        val initialResults = HomeListState.Loaded(fakePage)
        HomeSessionCache.updateResults(initialResults)

        // 模拟离开路由再返回：由 HomeSessionCache 恢复结果
        val restoredResults = HomeSessionCache.results
        assertNotNull(restoredResults)
        assertEquals(initialResults, restoredResults)
        assertTrue(isShowingSearchResults(activeQuery, restoredResults))
    }

    @Test
    fun 清空搜索词时会话缓存被清空且退出搜索内容列模式() {
        HomeSessionCache.updateResults(HomeListState.Loaded(fakePage))
        HomeSessionCache.clearSearch()

        assertNull(HomeSessionCache.results)
        assertFalse(HomeSessionCache.hasSearchResults)

        val activeQuery = ""
        val results = HomeSessionCache.results
        assertFalse(isShowingSearchResults(activeQuery, results))
    }
}
