package com.jm3.app.miuix.ui.search

import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 搜索状态机：首页两块皮肤与搜索页两块皮肤共用的那一份。
 *
 * 这里只钉纯转移与纯判定——输入面四态怎么走、提交输入分到哪一支、排序与时间取值怎么退回默认、
 * 标题与摘要的前缀、结局翻译、跳页夹取。取数落点（成功/失败/重试）由 loader 承担，
 * 已有 list/SessionListLoaderTest 覆盖，因此这里只验证交给 loader 之前的一切判定。
 */
class SearchScaffoldTest {

    private fun result(id: String, title: String = "作品$id") = SearchResult(
        id = id,
        title = title,
        authors = listOf("作者"),
        tags = listOf("标签"),
        coverUrl = null,
    )

    private fun feed(page: Int, pageCount: Int?, hasMore: Boolean) = PagedFeed<SearchResult>(
        items = listOf(result("1")),
        page = page,
        pageCount = pageCount,
        hasMore = hasMore,
    )

    // ---- 输入面状态转移 ----

    @Test
    fun 初始态停在发现流且按最新排序() {
        val state = SearchInputState()

        assertEquals("", state.query)
        assertEquals("", state.activeQuery)
        assertFalse(state.byTag)
        assertFalse(state.expanded)
        assertFalse(state.searching)
        assertEquals(BrowseOrder.LATEST, state.order)
        assertEquals("mr", state.orderValue)
        assertEquals(SearchCriteria("", byTag = false, order = BrowseOrder.LATEST), state.criteria)
    }

    @Test
    fun 输入只改输入框不触发检索() {
        val typed = SearchInputState().typed("猫")

        assertEquals("猫", typed.query)
        assertEquals("", typed.activeQuery)
        assertFalse(typed.searching)
    }

    @Test
    fun 执行搜索把条件落到已执行位并记住排序() {
        val started = SearchInputState().typed("猫")
            .started(SearchCriteria(query = "猫", byTag = true, order = BrowseOrder.MOST_VIEWED))

        assertEquals("猫", started.query)
        assertEquals("猫", started.activeQuery)
        assertTrue(started.byTag)
        assertTrue(started.searching)
        assertEquals(BrowseOrder.MOST_VIEWED, started.order)
        assertEquals("mv", started.orderValue)
        assertEquals(SearchCriteria("猫", byTag = true, order = BrowseOrder.MOST_VIEWED), started.criteria)
    }

    @Test
    fun 执行搜索只叠加缓存标记不复位已有标记() {
        val cached = SearchInputState(restoredResults = true)
            .started(SearchCriteria("猫"), keepCachedResults = false)

        assertTrue(cached.restoredResults)
        assertTrue(
            SearchInputState().started(SearchCriteria("猫"), keepCachedResults = true).restoredResults,
        )
        assertFalse(SearchInputState().started(SearchCriteria("猫")).restoredResults)
    }

    @Test
    fun 清空搜索回到发现流但排序不跟着复位() {
        val cleared = SearchInputState()
            .typed("猫")
            .started(SearchCriteria(query = "猫", byTag = true, order = BrowseOrder.MOST_LIKED))
            .cleared()

        assertEquals("", cleared.query)
        assertEquals("", cleared.activeQuery)
        assertFalse(cleared.byTag)
        assertFalse(cleared.searching)
        assertEquals(BrowseOrder.MOST_LIKED, cleared.order)
    }

    @Test
    fun 缓存还在时关键词为空也算搜索态() {
        val restored = SearchInputState(restoredResults = true)

        assertTrue(restored.searching)

        val dropped = restored.searchDropped()

        assertFalse(dropped.searching)
        assertFalse(dropped.restoredResults)
    }

    @Test
    fun 分页模式切换只丢缓存标记不动输入框() {
        val dropped = SearchInputState()
            .typed("猫")
            .started(SearchCriteria("猫"), keepCachedResults = true)
            .searchDropped()

        assertEquals("猫", dropped.query)
        assertEquals("猫", dropped.activeQuery)
        assertFalse(dropped.restoredResults)
        // 关键词还在，就仍是搜索态：分页模式切换只丢「手里还留着上次结果」这一标记
        assertTrue(dropped.searching)
    }

    @Test
    fun 展开态与排序各自独立转移() {
        val expanded = SearchInputState().expandedTo(true)
        assertTrue(expanded.expanded)

        val reordered = expanded
            .started(SearchCriteria("猫"))
            .orderChanged(BrowseOrder.MOST_IMAGES)

        assertEquals("猫", reordered.activeQuery)
        assertEquals(BrowseOrder.MOST_IMAGES, reordered.order)
        assertTrue(reordered.expanded)
    }

    // ---- 提交输入的三分支 ----

    @Test
    fun 空白输入被忽略() {
        assertEquals(SearchSubmission.Ignored, resolveSearchSubmission("   ", BrowseOrder.LATEST))
        assertEquals(SearchSubmission.Ignored, resolveSearchSubmission("", BrowseOrder.LATEST))
    }

    @Test
    fun 纯数字输入走车号直达() {
        val submission = resolveSearchSubmission(" 123456 ", BrowseOrder.LATEST)

        val album = assertIs<SearchSubmission.OpenAlbum>(submission)
        assertEquals("123456", album.albumId)
    }

    @Test
    fun 关键词输入带着当前排序开搜索() {
        val submission = resolveSearchSubmission(" 猫娘 ", BrowseOrder.MOST_VIEWED)

        val search = assertIs<SearchSubmission.Search>(submission)
        assertEquals(SearchCriteria("猫娘", byTag = false, order = BrowseOrder.MOST_VIEWED), search.criteria)
    }

    @Test
    fun 带章节后缀的输入按关键词搜索而不是车号() {
        val submission = resolveSearchSubmission("123456-1", BrowseOrder.LATEST)

        val search = assertIs<SearchSubmission.Search>(submission)
        assertEquals("123456-1", search.criteria.query)
        assertEquals(SearchScope.SITE, search.criteria.scope)
    }

    // ---- 建档值解析与文案前缀 ----

    @Test
    fun 排序与时间的存档值认不出时退回默认() {
        assertEquals(BrowseOrder.MOST_LIKED, searchOrderOf("tf"))
        assertEquals(BrowseOrder.LATEST, searchOrderOf("不存在"))
        assertEquals(BrowseTime.WEEK, searchTimeOf("w"))
        assertEquals(BrowseTime.ALL, searchTimeOf("不存在"))
    }

    @Test
    fun 站内检索标题用搜索其余域用标签名() {
        assertEquals("搜索：猫", searchPageTitle("猫", SearchScope.SITE))
        assertEquals("标签：猫", searchPageTitle("猫", SearchScope.TAG))
        assertEquals("作者：猫", searchPageTitle("猫", SearchScope.AUTHOR))
    }

    @Test
    fun 摘要前缀按标签位区分引号与标签() {
        assertEquals("标签：猫", searchHeadline("猫", byTag = true))
        assertEquals("“猫”", searchHeadline("猫", byTag = false))
    }

    // ---- 结局翻译 ----

    @Test
    fun 成功结局翻成与总页数一致的分页切片() {
        val page = SearchPage(
            currentPage = 2,
            totalPages = 5,
            items = listOf(result("1"), result("2")),
            totalItems = 42,
        )

        val slice = assertIs<SessionOutcome.Ok<PageSlice<SearchResult>>>(
            SessionOutcome.Ok(page).toSearchPageSlice(),
        ).value

        assertEquals(listOf("1", "2"), slice.items.map { it.id })
        assertEquals(2, slice.page)
        assertEquals(5, slice.pageCount)
        assertEquals(42, slice.total)
        assertTrue(slice.hasMore)
    }

    @Test
    fun 末页与未报总数的成功结局照实翻() {
        val page = SearchPage(currentPage = 5, totalPages = 5, items = listOf(result("1")), totalItems = 0)

        val slice = assertIs<SessionOutcome.Ok<PageSlice<SearchResult>>>(
            SessionOutcome.Ok(page).toSearchPageSlice(),
        ).value

        assertFalse(slice.hasMore)
        assertNull(slice.total)
    }

    @Test
    fun 会话失效结局原样透传() {
        assertSame(SessionOutcome.Expired, SessionOutcome.Expired.toSearchPageSlice())
    }

    @Test
    fun 失败结局原样透传并保留服务端消息() {
        val failure = SessionOutcome.Failed("raw server error")

        val passed = assertIs<SessionOutcome.Failed>(failure.toSearchPageSlice())

        assertEquals("raw server error", passed.message)
        assertNull(passed.cause)
    }

    // ---- 跳页夹取 ----

    @Test
    fun 夹取后同页就不跳() {
        assertNull(clampedPageJumpTarget(feed(page = 2, pageCount = 5, hasMore = true), 2))
    }

    @Test
    fun 目标页超出总页数时夹到最后一页() {
        assertEquals(
            5,
            clampedPageJumpTarget(feed(page = 2, pageCount = 5, hasMore = true), 9),
        )
    }

    @Test
    fun 总页数未知且没有下一页时不往后跳() {
        val unknown = feed(page = 2, pageCount = null, hasMore = false)

        assertNull(clampedPageJumpTarget(unknown, 3))
        assertEquals(3, clampedPageJumpTarget(unknown, 3, requireMoreWhenUnknown = false))
    }

    @Test
    fun 搜索页口径允许在总页数未知时往后跳() {
        val unknown = feed(page = 2, pageCount = null, hasMore = false)

        assertEquals(3, clampedPageJumpTarget(unknown, target = 3, requireMoreWhenUnknown = false))
    }

    @Test
    fun 合法校验口径传原始目标页() {
        val feedable = feed(page = 3, pageCount = null, hasMore = true)

        assertEquals(4, couiPageJumpTarget(feedable, 4))
        assertNull(couiPageJumpTarget(feedable, 3))
        assertNull(couiPageJumpTarget(feedable, 0))
        assertNull(couiPageJumpTarget(feed(page = 1, pageCount = 3, hasMore = true), 4))
    }
}
