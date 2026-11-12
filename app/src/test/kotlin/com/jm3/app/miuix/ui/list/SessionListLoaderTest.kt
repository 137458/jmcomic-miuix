package com.jm3.app.miuix.ui.list

import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 列表加载编排：代次守卫、在途标记、翻页游标与失败落点。 */
class SessionListLoaderTest {

    private data class Item(val id: String)

    private class Harness(scope: CoroutineScope) {
        val requestedPages = mutableListOf<Int>()
        val notices = mutableListOf<String>()
        var nextOutcome: SessionOutcome<PageSlice<Item>> = SessionOutcome.Failed(message = "网络不通")
        var gate: CompletableDeferred<Unit>? = null
        val loader = SessionListLoader(
            scope = scope,
            idOf = { item: Item -> item.id },
            failureMessage = "观看记录加载失败",
            fetchPage = { page ->
                requestedPages += page
                gate?.await()
                nextOutcome
            },
            notice = { notices += it },
        )
    }

    private fun ok(vararg ids: String, hasMore: Boolean = false, page: Int = 0): SessionOutcome<PageSlice<Item>> =
        SessionOutcome.Ok(PageSlice(items = ids.map { Item(it) }, hasMore = hasMore, page = page))

    @Test
    fun 首屏加载成功填充条目并置为已加载() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b")

        harness.loader.loadFirstPage()

        assertEquals(listOf(1), harness.requestedPages)
        assertEquals(listOf("a", "b"), harness.loader.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertFalse(harness.loader.refreshing)
        scope.cancel()
    }

    @Test
    fun 首屏失败且无内容时整屏失败并带屏内文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = SessionOutcome.Failed(message = "网络不通")

        harness.loader.loadFirstPage()

        val state = harness.loader.state as ListLoadState.Failed
        assertFalse(state.sessionExpired)
        assertTrue(state.message.isNotBlank())
        assertTrue(harness.notices.isEmpty())
        scope.cancel()
    }

    @Test
    fun 会话失效且无内容时给出登录态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = SessionOutcome.Expired

        harness.loader.loadFirstPage()

        val state = harness.loader.state as ListLoadState.Failed
        assertTrue(state.sessionExpired)
        scope.cancel()
    }

    @Test
    fun 追加成功推进游标并按条目去重() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b", hasMore = true)
        harness.loader.loadFirstPage()
        harness.nextOutcome = ok("b", "c", hasMore = true)

        harness.loader.loadMore()

        assertEquals(listOf(1, 2), harness.requestedPages)
        assertEquals(listOf("a", "b", "c"), harness.loader.feed.items.map { it.id })
        assertEquals(2, harness.loader.feed.page)
        scope.cancel()
    }

    @Test
    fun 没有下一页时不再发请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", hasMore = false)
        harness.loader.loadFirstPage()

        harness.loader.loadMore()

        assertEquals(listOf(1), harness.requestedPages)
        scope.cancel()
    }

    @Test
    fun 追加失败保留内容并留出页脚重试() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b", hasMore = true)
        harness.loader.loadFirstPage()
        harness.nextOutcome = SessionOutcome.Failed(message = "网络不通")

        harness.loader.loadMore()

        assertEquals(listOf("a", "b"), harness.loader.feed.items.map { it.id })
        assertTrue(harness.loader.feed.loadMoreFailed)
        assertFalse(harness.loader.feed.loadingMore)
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertEquals(1, harness.notices.size)
        assertTrue(harness.notices.single().startsWith("加载失败："))
        scope.cancel()
    }

    @Test
    fun 跳页失败保留旧内容并提示() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b", hasMore = true, page = 1)
        harness.loader.loadFirstPage()
        harness.nextOutcome = SessionOutcome.Failed(message = "网络错误")
        harness.notices.clear()

        harness.loader.goToPage(3)

        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertEquals(listOf("a", "b"), harness.loader.feed.items.map { it.id })
        assertEquals(1, harness.notices.size)
        scope.cancel()
    }

    @Test
    fun 刷新失败保留旧内容并收起刷新指示() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a")
        harness.loader.loadFirstPage()
        harness.nextOutcome = SessionOutcome.Failed(message = "网络不通")

        harness.loader.refresh()

        assertEquals(listOf("a"), harness.loader.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertFalse(harness.loader.refreshing)
        assertEquals(1, harness.notices.size)
        scope.cancel()
    }

    @Test
    fun 刷新成功替换列表并收起刷新指示() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b")
        harness.loader.loadFirstPage()
        harness.nextOutcome = ok("c")

        harness.loader.refresh()

        assertEquals(listOf("c"), harness.loader.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertFalse(harness.loader.refreshing)
        scope.cancel()
    }

    @Test
    fun 旧请求的结果被代次守卫丢弃() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        val firstGate = CompletableDeferred<Unit>()
        harness.gate = firstGate
        harness.nextOutcome = ok("old")
        harness.loader.loadFirstPage()

        harness.gate = null
        harness.nextOutcome = ok("fresh")
        harness.loader.refresh()
        firstGate.complete(Unit)

        assertEquals(listOf("fresh"), harness.loader.feed.items.map { it.id })
        scope.cancel()
    }

    @Test
    fun 点击分页跳页整页替换并记录页码() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b", hasMore = true, page = 1)
        harness.loader.loadFirstPage()
        harness.nextOutcome = ok("e", "f", hasMore = true, page = 3)

        harness.loader.goToPage(3)

        assertEquals(listOf(1, 3), harness.requestedPages)
        assertEquals(listOf("e", "f"), harness.loader.feed.items.map { it.id })
        assertEquals(3, harness.loader.feed.page)
        assertEquals(ListLoadState.Loaded, harness.loader.state)
        scope.cancel()
    }

    @Test
    fun 本地删除不触发回源() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = ok("a", "b")
        harness.loader.loadFirstPage()

        harness.loader.removeWhere { it.id == "a" }

        assertEquals(listOf("b"), harness.loader.feed.items.map { it.id })
        assertEquals(listOf(1), harness.requestedPages)
        scope.cancel()
    }

    @Test
    fun 失败后重试成功清空整屏失败态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(scope)
        harness.nextOutcome = SessionOutcome.Failed(message = "网络不通")
        harness.loader.loadFirstPage()
        harness.nextOutcome = ok("a")

        harness.loader.loadFirstPage()

        assertEquals(ListLoadState.Loaded, harness.loader.state)
        assertEquals(listOf("a"), harness.loader.feed.items.map { it.id })
        scope.cancel()
    }
}