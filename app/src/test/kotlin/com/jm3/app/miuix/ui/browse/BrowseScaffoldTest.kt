package com.jm3.app.miuix.ui.browse

import androidx.compose.runtime.mutableStateOf
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.BrowsePageKey
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.ListStateCache
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryNode
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 浏览页共享状态：筛选口径换算、主列表的三态与分页动作、标签检索词的进出、可选区块的降级。
 *
 * 网络只有一处桩：loader 的取页函数（[Harness.nextOutcome]）；其余都是真 loader、真状态机。
 */
class BrowseScaffoldTest {

    private fun result(id: String): SearchResult = SearchResult(
        id = id,
        title = "作品$id",
        authors = emptyList(),
        tags = emptyList(),
        coverUrl = null,
    )

    private fun page(
        vararg ids: String,
        hasMore: Boolean = false,
        page: Int = 1,
        pageCount: Int? = 3,
    ): SessionOutcome<PageSlice<SearchResult>> = SessionOutcome.Ok(
        PageSlice(
            items = ids.map(::result),
            hasMore = hasMore,
            page = page,
            pageCount = pageCount,
        ),
    )

    private class RecordingCache : ListStateCache<SearchResult> {
        private val entries = mutableMapOf<SessionListKey, PagedFeed<SearchResult>>()

        override fun read(key: SessionListKey): PagedFeed<SearchResult>? = entries[key]

        override fun write(key: SessionListKey, feed: PagedFeed<SearchResult>) {
            entries[key] = feed
        }

        override fun forget(key: SessionListKey) {
            entries.remove(key)
        }

        override fun hasPage(key: SessionListKey): Boolean = entries[key] != null
    }

    private class Harness(
        scope: CoroutineScope,
        val mode: BrowseMode = BrowseMode.CATEGORY,
        paginationMode: PaginationMode = PaginationMode.INFINITE,
        initial: BrowseFilters = BrowseFilters(),
        tag: String? = null,
        var categoryOutcome: SessionOutcome<CategoryTree> = SessionOutcome.Failed(message = "分类树没拿到"),
        var hotTagsOutcome: SessionOutcome<List<String>> = SessionOutcome.Failed(message = "热门标签没拿到"),
        cache: ListStateCache<SearchResult>? = null,
        cacheKey: SessionListKey? = null,
    ) {
        val requestedPages = mutableListOf<Int>()
        val notices = mutableListOf<String>()
        var nextOutcome: SessionOutcome<PageSlice<SearchResult>> =
            SessionOutcome.Ok(PageSlice(items = emptyList(), hasMore = false, page = 1))
        var gate: CompletableDeferred<Unit>? = null

        val fields = BrowseScaffoldFields(
            categoryValue = mutableStateOf(initial.categoryValue),
            orderValue = mutableStateOf(initial.orderValue),
            timeValue = mutableStateOf(initial.timeValue),
            tagKeyword = mutableStateOf(tag),
        )

        val loader = SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = "加载失败",
            fetchPage = { requestedPage ->
                requestedPages += requestedPage
                gate?.await()
                nextOutcome
            },
            notice = { notices += it },
            cache = cache,
            cacheKey = cacheKey,
        )

        val state = BrowseScaffoldState(
            mode = mode,
            scope = scope,
            fields = fields,
            loader = loader,
            readPaginationMode = { paginationMode },
            categoriesOutcome = { categoryOutcome },
            hotTagsOutcome = { hotTagsOutcome },
        )
    }

    /** 状态靠订阅 loader 快照推进：后台作用域随用例结束自动停，不会漏到别的用例。 */
    private fun TestScope.collectSnapshots(state: BrowseScaffoldState) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.observe() }
    }

    @Test
    fun 初始态是加载中且没有筛选增量与标签视图() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)

        assertEquals(ListState.Loading, harness.state.feedState)
        assertFalse(harness.state.refreshing)
        assertEquals(BrowseCategory.ALL, harness.state.category)
        assertEquals(BrowseOrder.LATEST, harness.state.order)
        assertEquals(BrowseTime.ALL, harness.state.time)
        assertNull(harness.state.categoryTree)
        assertTrue(harness.state.hotTags.isEmpty())
        assertFalse(harness.state.hasTagBlocks)
        assertNull(harness.state.tagKeyword)
        assertFalse(harness.state.clickPaging)
        assertEquals(BrowsePageKey(BrowseMode.CATEGORY, "0", "mr", "a"), harness.state.pageKey)
        scope.cancel()
    }

    @Test
    fun 分类模式的请求口径忽略时间榜单一侧忽略分类() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val filters = BrowseFilters(
            categoryValue = BrowseCategory.SHORT.apiValue,
            orderValue = BrowseOrder.MOST_LIKED.apiValue,
            timeValue = BrowseTime.TODAY.apiValue,
        )

        val categorySide = Harness(scope, mode = BrowseMode.CATEGORY, initial = filters).state
        assertEquals(
            BrowseFilter(
                category = BrowseCategory.SHORT,
                order = BrowseOrder.MOST_LIKED,
                time = BrowseTime.ALL,
            ),
            categorySide.filters.filter(BrowseMode.CATEGORY),
        )

        val rankSide = Harness(scope, mode = BrowseMode.RANK, initial = filters).state
        assertEquals(
            BrowseFilter(
                category = BrowseCategory.ALL,
                order = BrowseOrder.MOST_LIKED,
                time = BrowseTime.TODAY,
            ),
            rankSide.filters.filter(BrowseMode.RANK),
        )
        scope.cancel()
    }

    @Test
    fun 还原值不被认出来时三项都退回默认口径() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(
            scope,
            initial = BrowseFilters(categoryValue = "gone", orderValue = "gone", timeValue = "gone"),
        )

        assertEquals(BrowseCategory.ALL, harness.state.category)
        assertEquals(BrowseOrder.LATEST, harness.state.order)
        assertEquals(BrowseTime.ALL, harness.state.time)
        assertEquals(
            BrowseFilter(category = BrowseCategory.ALL, order = BrowseOrder.LATEST, time = BrowseTime.ALL),
            harness.state.filters.filter(BrowseMode.CATEGORY),
        )
        scope.cancel()
    }

    @Test
    fun 缓存键保留原始还原值不跟着兜底归一到默认项() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope, initial = BrowseFilters(categoryValue = "gone"))

        // 认不出来只影响请求口径；页面键仍认原始值，切回来才命中同一份缓存结果
        assertEquals("gone", harness.state.pageKey.category)
        assertEquals(BrowseCategory.ALL, harness.state.category)
        assertEquals(
            SessionListKey.Browse(BrowsePageKey(BrowseMode.CATEGORY, "gone", "mr", "a")),
            harness.state.cacheKey,
        )
        scope.cancel()
    }

    @Test
    fun 切换筛选项改写还原值并同步到页面键与请求口径() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)

        harness.state.selectCategory(BrowseCategory.DOUJIN.apiValue)
        harness.state.selectOrder(BrowseOrder.MOST_VIEWED.apiValue)
        harness.state.selectTime(BrowseTime.WEEK.apiValue)

        assertEquals("doujin", harness.state.pageKey.category)
        assertEquals("mv", harness.state.pageKey.order)
        assertEquals("w", harness.state.pageKey.time)
        assertEquals(BrowseCategory.DOUJIN, harness.state.category)
        assertEquals(BrowseOrder.MOST_VIEWED, harness.state.order)
        assertEquals(BrowseTime.WEEK, harness.state.time)
        assertEquals("doujin", harness.fields.categoryValue.value)
        scope.cancel()
    }

    @Test
    fun 点击分页模式由读数决定() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val scrolling = Harness(scope, paginationMode = PaginationMode.INFINITE)
        val paging = Harness(scope, paginationMode = PaginationMode.CLICK)

        assertFalse(scrolling.state.clickPaging)
        assertTrue(paging.state.clickPaging)
        scope.cancel()
    }

    @Test
    fun 首屏加载成功进入已加载态并带出列表() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", "b")

        harness.state.reload()
        advanceUntilIdle()

        assertEquals(listOf(1), harness.requestedPages)
        val loaded = assertIs<ListState.Loaded>(harness.state.feedState)
        assertEquals(listOf("a", "b"), loaded.feed.items.map { it.id })
        assertFalse(harness.state.refreshing)
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        scope.cancel()
    }

    @Test
    fun 首屏失败且没有内容时整屏给出可重试失败态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = SessionOutcome.Failed(message = "boom")

        harness.state.reload()
        advanceUntilIdle()

        val failed = assertIs<ListState.Failed>(harness.state.feedState)
        assertEquals("加载失败", failed.message)
        assertFalse(failed.sessionExpired)
        assertTrue(harness.notices.isEmpty())
        scope.cancel()
    }

    @Test
    fun 会话失效时失败态标出登录而不是普通重试() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = SessionOutcome.Expired

        harness.state.reload()
        advanceUntilIdle()

        val failed = assertIs<ListState.Failed>(harness.state.feedState)
        assertTrue(failed.sessionExpired)
        assertTrue(harness.notices.isEmpty())
        scope.cancel()
    }

    @Test
    fun 下拉刷新保留屏上内容只置刷新中() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a")
        harness.state.reload()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        harness.gate = gate
        harness.nextOutcome = page("a", "b")
        harness.state.refresh(byPull = true)
        advanceUntilIdle()

        assertTrue(harness.state.refreshing)
        assertEquals(listOf("a"), harness.state.snapshot.feed.items.map { it.id })
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.state.refreshing)
        assertEquals(listOf("a", "b"), harness.state.snapshot.feed.items.map { it.id })
        scope.cancel()
    }

    @Test
    fun 程序化重载走整屏加载手势不置刷新中() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a")
        harness.state.reload()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        harness.gate = gate
        harness.state.reload()
        advanceUntilIdle()

        assertFalse(harness.state.refreshing)
        assertEquals(ListState.Loading, harness.state.feedState)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1, 1), harness.requestedPages)
        assertIs<ListState.Loaded>(harness.state.feedState)
        scope.cancel()
    }

    @Test
    fun 触底加载把下一页追加在现有条目之后() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", hasMore = true, page = 1)
        harness.state.reload()
        advanceUntilIdle()

        harness.nextOutcome = page("b", hasMore = false, page = 2)
        harness.state.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), harness.requestedPages)
        val loaded = assertIs<ListState.Loaded>(harness.state.feedState)
        assertEquals(listOf("a", "b"), loaded.feed.items.map { it.id })
        assertEquals(2, loaded.feed.page)
        assertFalse(loaded.feed.hasMore)
        scope.cancel()
    }

    @Test
    fun 触底加载在没有下一页时不发请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", hasMore = false, page = 1)
        harness.state.reload()
        advanceUntilIdle()

        harness.state.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1), harness.requestedPages)
        scope.cancel()
    }

    @Test
    fun 点击分页停在原页时不发请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope, paginationMode = PaginationMode.CLICK)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", hasMore = true, page = 1, pageCount = 5)
        harness.state.reload()
        advanceUntilIdle()

        harness.state.jumpToPage(1)
        advanceUntilIdle()

        assertEquals(listOf(1), harness.requestedPages)
        assertFalse(harness.state.consumeScrollToTop())
        scope.cancel()
    }

    @Test
    fun 点击分页换页落地后消费一次归顶请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope, paginationMode = PaginationMode.CLICK)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", hasMore = true, page = 1, pageCount = 5)
        harness.state.reload()
        advanceUntilIdle()

        harness.nextOutcome = page("c", hasMore = true, page = 3, pageCount = 5)
        harness.state.jumpToPage(3)
        advanceUntilIdle()

        assertEquals(listOf(1, 3), harness.requestedPages)
        assertEquals(3, harness.state.snapshot.feed.page)
        assertTrue(harness.state.consumeScrollToTop())
        // 只消费一次：同一份快照不会再触发第二次滚动
        assertFalse(harness.state.consumeScrollToTop())
        scope.cancel()
    }

    @Test
    fun 点击分页失败时保留旧页且不归顶() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope, paginationMode = PaginationMode.CLICK)
        collectSnapshots(harness.state)
        harness.nextOutcome = page("a", hasMore = true, page = 1, pageCount = 5)
        harness.state.reload()
        advanceUntilIdle()

        harness.nextOutcome = SessionOutcome.Failed(message = "boom")
        harness.state.jumpToPage(3)
        advanceUntilIdle()

        assertEquals(1, harness.state.snapshot.feed.page)
        assertEquals(listOf("a"), harness.state.snapshot.feed.items.map { it.id })
        assertFalse(harness.state.consumeScrollToTop())
        assertEquals(listOf("加载失败：加载失败"), harness.notices)
        scope.cancel()
    }

    @Test
    fun 标签检索词去掉首尾空白后进入标签视图() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)

        harness.state.openTag("  猫耳  ")
        assertEquals("猫耳", harness.state.tagKeyword)
        assertEquals("猫耳", harness.fields.tagKeyword.value)

        harness.state.closeTag()
        assertNull(harness.state.tagKeyword)
        scope.cancel()
    }

    @Test
    fun 空白标签点不开也不会清掉当前标签() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope, tag = "猫耳")

        harness.state.openTag("   ")
        assertEquals("猫耳", harness.state.tagKeyword)
        harness.state.openTag("")
        assertEquals("猫耳", harness.state.tagKeyword)
        scope.cancel()
    }

    @Test
    fun 分类树加载成功后面板判定为有标签区块() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        val tree = CategoryTree(
            categories = listOf(CategoryNode(id = "1", name = "同人", slug = "doujin")),
            blocks = listOf(CategoryBlock(title = "主题", tags = listOf("猫耳"))),
        )
        harness.categoryOutcome = SessionOutcome.Ok(tree)

        harness.state.loadCategoryTree()

        assertEquals(tree, harness.state.categoryTree)
        assertTrue(harness.state.hasTagBlocks)
        scope.cancel()
    }

    @Test
    fun 分类树失败降级为空且不影响已加载的热门标签() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.hotTagsOutcome = SessionOutcome.Ok(listOf("猫耳", "尾巴"))
        harness.state.loadHotTags()

        harness.categoryOutcome = SessionOutcome.Failed(message = "分类树没拿到")
        harness.state.loadCategoryTree()

        assertNull(harness.state.categoryTree)
        assertFalse(harness.state.hasTagBlocks)
        assertEquals(listOf("猫耳", "尾巴"), harness.state.hotTags)
        scope.cancel()
    }

    @Test
    fun 热门标签失效时降级为空表而不报错() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.hotTagsOutcome = SessionOutcome.Expired

        harness.state.loadHotTags()

        assertTrue(harness.state.hotTags.isEmpty())
        assertNull(harness.state.categoryTree)
        scope.cancel()
    }

    @Test
    fun 缓存命中时首帧直接是已加载态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val cache = RecordingCache()
        val key = SessionListKey.Browse(BrowsePageKey(BrowseMode.CATEGORY, "0", "mr", "a"))
        cache.write(key, PagedFeed(items = listOf(result("a")), page = 1, hasMore = false))

        val harness = Harness(scope, cache = cache, cacheKey = key)
        collectSnapshots(harness.state)

        val loaded = assertIs<ListState.Loaded>(harness.state.feedState)
        assertEquals(listOf("a"), loaded.feed.items.map { it.id })
        scope.cancel()
    }
}
