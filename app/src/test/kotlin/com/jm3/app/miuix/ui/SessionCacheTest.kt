package com.jm3.app.miuix.ui

import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.toFeed
import java.time.LocalDate
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

    /** 缓存里存的是整份分页状态：页面按 [fakePage] 的第一页装成 feed，刷成 remember 后的快照形态。 */
    private val fakeFeed = fakePage.toFeed()

    private fun browseKey(
        mode: BrowseMode = BrowseMode.CATEGORY,
        category: String = "all",
        order: String = "mr",
        time: String = "all",
    ) = SessionListKey.Browse(BrowsePageKey(mode, category, order, time))

    @Test
    fun 加载成功的结果能按身份取回() {
        val cache = SessionCache()

        cache.remember(browseKey(), ListState.Loaded(fakeFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOf(browseKey()))
        assertTrue(cache.hasPage(browseKey()))
    }

    @Test
    fun 筛选条件变了就不认旧结果页面要重新请求() {
        val cache = SessionCache()
        cache.remember(browseKey(category = "all"), ListState.Loaded(fakeFeed))

        assertFalse(cache.hasPage(browseKey(category = "doujin")))
        assertEquals(ListState.Loading, cache.stateOf(browseKey(category = "doujin")))
    }

    @Test
    fun 分类与榜单两种模式各存各的互不覆盖() {
        val cache = SessionCache()
        val rankFeed = fakePage.copy(currentPage = 2).toFeed()
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))
        cache.remember(browseKey(mode = BrowseMode.RANK), ListState.Loaded(rankFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOf(browseKey()))
        assertEquals(ListState.Loaded(rankFeed), cache.stateOf(browseKey(mode = BrowseMode.RANK)))
    }

    @Test
    fun 加载失败不进缓存回来时按未加载处理() {
        val cache = SessionCache()
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))
        cache.remember(browseKey(), ListState.Failed("网络不可用"))

        assertFalse(cache.hasPage(browseKey()))
        assertEquals(ListState.Loading, cache.stateOf(browseKey()))
    }

    @Test
    fun 清空搜索结果不影响发现流那一格() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        cache.remember(SessionListKey.HomeSearch, ListState.Loaded(fakeFeed))

        cache.forget(SessionListKey.HomeSearch)

        assertNull(cache.stateOrNull(SessionListKey.HomeSearch))
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOf(SessionListKey.HomeFeed))
    }

    @Test
    fun 首页与浏览页的缓存互不串格() {
        val cache = SessionCache()
        val rankFeed = fakePage.copy(currentPage = 3).toFeed()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        cache.remember(browseKey(), ListState.Loaded(rankFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOf(SessionListKey.HomeFeed))
        assertEquals(ListState.Loaded(rankFeed), cache.stateOf(browseKey()))
    }

    @Test
    fun 分页模式变化只作废列表结果并保留侧栏数据() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))
        cache.homeSide = HomeSideData(
            hotTags = listOf("热血"),
            serialUpdates = fakeFeed.items,
            randomPicks = fakeFeed.items,
        )

        cache.forgetPages()

        assertEquals(ListState.Loading, cache.stateOf(SessionListKey.HomeFeed))
        assertFalse(cache.hasPage(browseKey()))
        assertEquals(listOf("热血"), cache.homeSide.hotTags)
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

    @Test
    fun 签到状态按账号缓存且退出清空后不串号() {
        val cache = SessionCache()
        val today = LocalDate.of(2026, 11, 12)
        val statusA = DailyCheckInStatus(1, "A", "1/7", 1, 7)
        val statusB = DailyCheckInStatus(2, "B", "2/7", 2, 7)

        cache.rememberCheckIn("1001", statusA, today)
        cache.rememberCheckIn("1002", statusB, null)

        assertEquals(SessionCache.CachedCheckInState(statusA, today), cache.checkInOf("1001"))
        assertEquals(SessionCache.CachedCheckInState(statusB, null), cache.checkInOf("1002"))

        // 切换分页模式只作废列表，不应清掉账号签到态
        cache.forgetPages()
        assertEquals(SessionCache.CachedCheckInState(statusA, today), cache.checkInOf("1001"))
        // 跨过午夜后，昨天缓存的签到状态必须失效，不能继续复用昨天日历
        assertNull(cache.checkInForToday("1001", today.plusDays(1)))
        assertEquals(SessionCache.CachedCheckInState(statusA, today), cache.checkInForToday("1001", today))

        cache.clear()
        assertNull(cache.checkInOf("1001"))
        assertNull(cache.checkInOf("1002"))
    }

    @Test
    fun 标签检索缓存按规范化词与排序分格并在切换分页模式时失效() {
        val cache = SessionCache()
        val keyLatest = browseTagCacheKey("  全彩 ", BrowseOrder.LATEST)
        val keyViewed = browseTagCacheKey("全彩", BrowseOrder.MOST_VIEWED)
        assertNull(browseTagCacheKey("   \t", BrowseOrder.LATEST))
        assertEquals(SessionListKey.BrowseTag("全彩", BrowseOrder.LATEST), keyLatest)
        assertEquals(SessionListKey.BrowseTag("全彩", BrowseOrder.MOST_VIEWED), keyViewed)

        val latestFeed = fakePage.copy(currentPage = 1).toFeed()
        val viewedFeed = fakePage.copy(currentPage = 2).toFeed()
        cache.remember(keyLatest!!, ListState.Loaded(latestFeed))
        cache.remember(keyViewed!!, ListState.Loaded(viewedFeed))

        assertEquals(ListState.Loaded(latestFeed), cache.stateOf(keyLatest))
        assertEquals(ListState.Loaded(viewedFeed), cache.stateOf(keyViewed))

        cache.forgetPages()
        assertFalse(cache.hasPage(keyLatest))
        assertEquals(ListState.Loading, cache.stateOf(keyLatest))
    }
}
