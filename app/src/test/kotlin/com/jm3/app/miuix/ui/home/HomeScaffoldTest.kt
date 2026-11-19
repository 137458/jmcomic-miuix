package com.jm3.app.miuix.ui.home

import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页状态编排骨架里可脱离 Compose 判定的那一层。
 *
 * 两块皮肤的差异都集中在 [HomeScaffoldParams] 的五个策略上，因此这里逐一验证每条策略
 * 在「分页模式切换、点击跳页、随机看一部」三件事上的具体输出，以及参数对象本身的稳定相等性
 * （它同时是 remember 的键，值相同必须相等，否则页面每帧重启效应）。
 */
class HomeScaffoldTest {

    private fun result(id: String) = SearchResult(
        id = id,
        title = "作品$id",
        authors = emptyList(),
        tags = emptyList(),
        coverUrl = null,
    )

    private fun feed(page: Int, pageCount: Int?, hasMore: Boolean) = PagedFeed<SearchResult>(
        items = listOf(result("1")),
        page = page,
        pageCount = pageCount,
        hasMore = hasMore,
    )

    private val miuixParams = HomeScaffoldParams(
        searchLoad = HomeSearchLoad.Sticky,
        jump = HomePageJump.Clamped,
        paginationReload = HomePaginationReload.BothLoaders,
        sideLoad = HomeSideLoad.Sequential,
        loadRankTrack = false,
        scrollPolicy = HomeScrollPolicy.ExactPage,
        searchFailureMessage = "网络错误",
        feedFailureMessage = "加载失败",
    )

    private val couiParams = HomeScaffoldParams(
        searchLoad = HomeSearchLoad.Rebuilt,
        jump = HomePageJump.CouiGuarded,
        paginationReload = HomePaginationReload.ActiveOnly,
        sideLoad = HomeSideLoad.Parallel,
        loadRankTrack = true,
        scrollPolicy = HomeScrollPolicy.AnyLoaded,
        searchFailureMessage = "网络错误",
        feedFailureMessage = "加载失败",
    )

    @Test
    fun 相同取值的参数对象相等() {
        val rebuilt = miuixParams.copy(
            searchLoad = HomeSearchLoad.Sticky,
            jump = HomePageJump.Clamped,
        )

        assertEquals(miuixParams, rebuilt)
        assertEquals(miuixParams.hashCode(), rebuilt.hashCode())
    }

    @Test
    fun 两块皮肤的差异参数各自独立() {
        assertTrue(couiParams.loadRankTrack)
        assertEquals(HomeSearchLoad.Rebuilt, couiParams.searchLoad)
        assertEquals(HomePageJump.CouiGuarded, couiParams.jump)
        assertEquals(HomePaginationReload.ActiveOnly, couiParams.paginationReload)
        assertEquals(HomeSideLoad.Parallel, couiParams.sideLoad)
        assertEquals(HomeScrollPolicy.AnyLoaded, couiParams.scrollPolicy)
        assertFalse(couiParams == miuixParams)
    }

    @Test
    fun 米柚皮肤切分页模式时两个载入器都重取() {
        val searching = homeReloadPlan(searching = true, policy = HomePaginationReload.BothLoaders)

        assertTrue(searching.searchFirstPage)
        assertTrue(searching.feedFirstPage)
        assertFalse(searching.dropSearchCache)
    }

    @Test
    fun 米柚皮肤非搜索态切分页模式时只重取发现流并清搜索缓存() {
        val idle = homeReloadPlan(searching = false, policy = HomePaginationReload.BothLoaders)

        assertFalse(idle.searchFirstPage)
        assertTrue(idle.feedFirstPage)
        assertTrue(idle.dropSearchCache)
    }

    @Test
    fun 酷衣皮肤切分页模式时只重取激活的那一个() {
        val searching = homeReloadPlan(searching = true, policy = HomePaginationReload.ActiveOnly)
        val idle = homeReloadPlan(searching = false, policy = HomePaginationReload.ActiveOnly)

        assertTrue(searching.searchFirstPage)
        assertFalse(searching.feedFirstPage)
        assertFalse(idle.searchFirstPage)
        assertTrue(idle.feedFirstPage)
        assertFalse(searching.dropSearchCache)
        assertFalse(idle.dropSearchCache)
    }

    @Test
    fun 夹取口径挡掉同页并把越界目标收到末页() {
        val known = feed(page = 2, pageCount = 5, hasMore = true)

        assertNull(homeJumpTarget(known, target = 2, policy = HomePageJump.Clamped))
        assertEquals(5, homeJumpTarget(known, target = 9, policy = HomePageJump.Clamped))
        assertEquals(3, homeJumpTarget(known, target = 3, policy = HomePageJump.Clamped))
    }

    @Test
    fun 夹取口径挡掉总页数未知又没有下一页的往后跳() {
        val unknown = feed(page = 2, pageCount = null, hasMore = false)

        assertNull(homeJumpTarget(unknown, target = 3, policy = HomePageJump.Clamped))
        // 往前翻不需要下一页，仍然允许
        assertEquals(1, homeJumpTarget(unknown, target = 1, policy = HomePageJump.Clamped))
    }

    @Test
    fun 酷衣口径只校验合法性并保留原目标页() {
        val unknown = feed(page = 3, pageCount = null, hasMore = true)

        assertEquals(4, homeJumpTarget(unknown, target = 4, policy = HomePageJump.CouiGuarded))
        assertNull(homeJumpTarget(unknown, target = 3, policy = HomePageJump.CouiGuarded))
        assertNull(homeJumpTarget(unknown, target = 0, policy = HomePageJump.CouiGuarded))
        assertNull(
            homeJumpTarget(
                feed(page = 1, pageCount = 3, hasMore = true),
                target = 4,
                policy = HomePageJump.CouiGuarded,
            ),
        )
    }

    @Test
    fun 随机池优先随机推荐空则退到当前列表() {
        val picks = listOf(result("p1"), result("p2"))
        val items = listOf(result("f1"))

        assertEquals(picks, homeRandomPool(picks, items))
        assertEquals(items, homeRandomPool(emptyList(), items))
        assertTrue(homeRandomPool(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun 首页共享常量保持原值() {
        assertEquals(8, HOT_TAG_COLLAPSED)
        assertEquals(12, SERIAL_CAROUSEL_LIMIT)
    }
}
