package com.jm3.app.miuix.ui

import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
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

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(browseKey()))
        assertTrue(cache.hasPage(browseKey()))
    }

    @Test
    fun 筛选条件变了就不认旧结果页面要重新请求() {
        val cache = SessionCache()
        cache.remember(browseKey(category = "all"), ListState.Loaded(fakeFeed))

        assertFalse(cache.hasPage(browseKey(category = "doujin")))
        assertNull(cache.stateOrNull(browseKey(category = "doujin")))
    }

    @Test
    fun 分类与榜单两种模式各存各的互不覆盖() {
        val cache = SessionCache()
        val rankFeed = fakePage.copy(currentPage = 2).toFeed()
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))
        cache.remember(browseKey(mode = BrowseMode.RANK), ListState.Loaded(rankFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(browseKey()))
        assertEquals(ListState.Loaded(rankFeed), cache.stateOrNull(browseKey(mode = BrowseMode.RANK)))
    }

    @Test
    fun 加载失败不进缓存回来时按未加载处理() {
        val cache = SessionCache()
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))
        cache.remember(browseKey(), ListState.Failed("网络不可用"))

        assertFalse(cache.hasPage(browseKey()))
        assertNull(cache.stateOrNull(browseKey()))
    }

    @Test
    fun 清空搜索结果不影响发现流那一格() {
        val cache = SessionCache()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        cache.remember(SessionListKey.HomeSearch, ListState.Loaded(fakeFeed))

        cache.forget(SessionListKey.HomeSearch)

        assertNull(cache.stateOrNull(SessionListKey.HomeSearch))
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(SessionListKey.HomeFeed))
    }

    @Test
    fun 首页与浏览页的缓存互不串格() {
        val cache = SessionCache()
        val rankFeed = fakePage.copy(currentPage = 3).toFeed()
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        cache.remember(browseKey(), ListState.Loaded(rankFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(SessionListKey.HomeFeed))
        assertEquals(ListState.Loaded(rankFeed), cache.stateOrNull(browseKey()))
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

        assertNull(cache.stateOrNull(SessionListKey.HomeFeed))
        assertFalse(cache.hasPage(browseKey()))
        assertEquals(listOf("热血"), cache.homeSide.hotTags)
    }

    @Test
    fun 分页模式变化作废所有账号的列表结果() {
        val cache = SessionCache()
        var uid: String? = "1001"
        cache.bindAccount { uid }
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        uid = "1002"
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))

        cache.forgetPages()

        assertNull(cache.stateOrNull(SessionListKey.HomeFeed))
        uid = "1001"
        assertNull(cache.stateOrNull(SessionListKey.HomeFeed))
    }

    @Test
    fun 列表快照按账号分仓换账号后不命中上一账号的结果() {
        val cache = SessionCache()
        var uid: String? = "1001"
        cache.bindAccount { uid }
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))

        assertTrue(cache.hasPage(browseKey()))
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(browseKey()))
        assertEquals(fakeFeed, cache.read(browseKey()))

        // 换账号：同一把键读到的是另一个仓，必须按未加载处理，页面才会重新首拉
        uid = "1002"
        assertFalse(cache.hasPage(browseKey()))
        assertNull(cache.stateOrNull(browseKey()))
        assertNull(cache.read(browseKey()))

        // 切回原账号仍命中自己那一格
        uid = "1001"
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(browseKey()))
        assertEquals(fakeFeed, cache.read(browseKey()))
    }

    @Test
    fun 未登录与登录账号的列表缓存互不串格() {
        val cache = SessionCache()
        var uid: String? = null
        cache.bindAccount { uid }
        cache.remember(SessionListKey.HomeFeed, ListState.Loaded(fakeFeed))
        assertTrue(cache.hasPage(SessionListKey.HomeFeed))

        uid = "1001"
        assertFalse(cache.hasPage(SessionListKey.HomeFeed))
        assertNull(cache.stateOrNull(SessionListKey.HomeFeed))

        uid = null
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(SessionListKey.HomeFeed))
    }

    @Test
    fun 账号读面取到的是实时值而不是构造期快照() {
        val cache = SessionCache()
        var uid: String? = "1001"
        // 绑定的是 lambda：账号切换发生在绑定之后，读写必须跟着走
        cache.bindAccount { uid }
        uid = "1002"
        cache.remember(browseKey(), ListState.Loaded(fakeFeed))

        uid = "1001"
        assertNull(cache.stateOrNull(browseKey()))
        uid = "1002"
        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(browseKey()))
    }

    @Test
    fun 独立搜索页缓存按查询词范围与排序口径分格() {
        val cache = SessionCache()
        val key = SessionListKey.Search(
            query = "火影",
            scope = SearchScope.SITE,
            order = BrowseOrder.LATEST,
            time = BrowseTime.ALL,
        )
        val otherOrder = key.copy(order = BrowseOrder.MOST_VIEWED)
        val otherScope = key.copy(scope = SearchScope.TAG)
        val otherTime = key.copy(time = BrowseTime.WEEK)
        val otherQuery = key.copy(query = "海贼")

        cache.remember(key, ListState.Loaded(fakeFeed))

        assertEquals(ListState.Loaded(fakeFeed), cache.stateOrNull(key))
        assertEquals(fakeFeed, cache.read(key))
        assertTrue(cache.hasPage(key))
        listOf(otherOrder, otherScope, otherTime, otherQuery).forEach { other ->
            assertFalse(cache.hasPage(other), "另一份查询语义不该命中：$other")
            assertNull(cache.stateOrNull(other), "另一份查询语义不该命中：$other")
        }
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
        assertNull(cache.stateOrNull(SessionListKey.HomeFeed))
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

        assertEquals(ListState.Loaded(latestFeed), cache.stateOrNull(keyLatest))
        assertEquals(ListState.Loaded(viewedFeed), cache.stateOrNull(keyViewed))

        cache.forgetPages()
        assertFalse(cache.hasPage(keyLatest))
        assertNull(cache.stateOrNull(keyLatest))
    }
}
