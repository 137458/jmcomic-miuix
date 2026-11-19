// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.watchhistory

import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.core.model.SearchResult
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 假的观看历史网关：页结局与删除结局由用例摆放，调用参数按顺序记账。 */
private class FakeWatchHistory : WatchHistoryPort {
    /** 没有按页码摆放结局时的默认页。 */
    var pageOutcome: SessionOutcome<List<SearchResult>> = SessionOutcome.Ok(emptyList())

    /** 按页码摆放的页结局。 */
    val pageOutcomes = mutableMapOf<Int, SessionOutcome<List<SearchResult>>>()
    val requestedPages = mutableListOf<Int>()

    /** 删除结局队列，用完后回落到 [deleteOutcome]。 */
    val deleteOutcomes = mutableListOf<SessionOutcome<Unit>>()
    var deleteOutcome: SessionOutcome<Unit> = SessionOutcome.Ok(Unit)

    /** 非空时删除请求会挂住，直到用例放行：用来验证在途守卫。 */
    var deleteGate: CompletableDeferred<SessionOutcome<Unit>>? = null
    val deletedIds = mutableListOf<String>()

    override suspend fun page(page: Int): SessionOutcome<List<SearchResult>> {
        requestedPages += page
        return pageOutcomes[page] ?: pageOutcome
    }

    override suspend fun delete(id: String): SessionOutcome<Unit> {
        deletedIds += id
        deleteGate?.let { return it.await() }
        return deleteOutcomes.removeFirstOrNull() ?: deleteOutcome
    }
}

/** 假的提示口：只记一句话提示。 */
private class FakeWatchNotices : WatchHistoryNotices {
    val plain = mutableListOf<String>()

    override suspend fun show(message: String) {
        plain += message
    }
}

private fun work(id: String, title: String = "作品" + id): SearchResult = SearchResult(
    id = id,
    title = title,
    authors = emptyList(),
    tags = emptyList(),
    coverUrl = null,
)

private fun works(vararg ids: String): List<SearchResult> = ids.map { work(it) }

/**
 * 观看历史骨架的行为用例：装载三态、分页收敛、删除二次确认的挂起/取消/确认，以及删除的每种结局。
 *
 * 全部走假端口，只断言业务输出与状态变化；两皮肤的弹窗与栅格外观不在这一层。
 */

/**
 * 独立测试作用域：与 runTest 共用同一个调度器，advanceUntilIdle() 才能推进它。
 *
 * TestScope.scope 上的任务在当前 kotlinx-coroutines-test 版本里不会被 advanceUntilIdle() 推进，
 * 用它会让装载、取消、删除这些协程体一次都不跑。
 */
private fun TestScope.harnessScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

class WatchHistoryScaffoldTest {

    private fun state(
        scope: CoroutineScope,
        history: FakeWatchHistory = FakeWatchHistory(),
        notices: FakeWatchNotices = FakeWatchNotices(),
        onSessionExpired: () -> Unit = {},
    ): WatchHistoryScaffoldState = WatchHistoryScaffoldState(
        scope = scope,
        history = history,
        notices = notices,
        onSessionExpired = onSessionExpired,
    )

    @Test
    fun `初始状态是整屏加载态且没有待确认删除项`() = runTest {
        val scope = harnessScope()
        val subject = state(scope)

        assertEquals(emptyList<SearchResult>(), subject.snapshot.value.feed.items)
        assertEquals(ListLoadState.Loading, subject.snapshot.value.state)
        assertNull(subject.deletingId)
        assertNull(subject.pendingDeleteItem)
    }

    @Test
    fun `首屏装载成功落定条目且不满一页时没有下一页`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        val subject = state(scope, history)

        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(listOf(1), history.requestedPages)
        assertEquals(listOf("w1", "w2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
        assertFalse(subject.snapshot.value.feed.hasMore)
    }

    @Test
    fun `首屏装满一页时判定还有下一页`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(List(WATCH_PAGE_SIZE) { work("w" + it) })
        val subject = state(scope, history)

        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(WATCH_PAGE_SIZE, subject.snapshot.value.feed.items.size)
        assertTrue(subject.snapshot.value.feed.hasMore)
    }

    @Test
    fun `首屏普通失败整屏给观看记录兜底文案且不弹提示`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Failed("网络炸了")
        val notices = FakeWatchNotices()
        val subject = state(scope, history, notices)

        subject.loadFirstPage()
        advanceUntilIdle()

        val failed = subject.snapshot.value.state as ListLoadState.Failed
        assertEquals(WATCH_FAILURE_MESSAGE, failed.message)
        assertFalse(failed.sessionExpired)
        assertEquals(emptyList<String>(), notices.plain)
    }

    @Test
    fun `首屏会话失效整屏给登录文案并标记失效`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Expired
        val subject = state(scope, history)

        subject.loadFirstPage()
        advanceUntilIdle()

        val failed = subject.snapshot.value.state as ListLoadState.Failed
        assertEquals(SessionOutcome.Expired.message, failed.message)
        assertTrue(failed.sessionExpired)
    }

    @Test
    fun `首屏失败后重试重新拉第一页并装载成功`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Failed("网络炸了")
        val subject = state(scope, history)

        subject.loadFirstPage()
        advanceUntilIdle()
        assertTrue(subject.snapshot.value.state is ListLoadState.Failed)

        history.pageOutcome = SessionOutcome.Ok(works("w1"))
        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), history.requestedPages)
        assertEquals(listOf("w1"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
    }

    @Test
    fun `分页目标页收敛成相邻页且当前页不重发`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(List(WATCH_PAGE_SIZE) { work("w" + it) })
        val subject = state(scope, history)
        subject.loadFirstPage()
        advanceUntilIdle()

        assertNull(subject.pageToLoad(1))
        assertEquals(2, subject.pageToLoad(5))
        assertNull(subject.pageToLoad(0))
    }

    @Test
    fun `点行尾删除只挂起确认不发生请求`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        val subject = state(scope, history)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w2"))
        advanceUntilIdle()

        assertEquals("w2", subject.pendingDeleteItem?.id)
        assertNull(subject.deletingId)
        assertEquals(emptyList<String>(), history.deletedIds)
    }

    @Test
    fun `取消删除清空待确认项且不发请求`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        val notices = FakeWatchNotices()
        val subject = state(scope, history, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w2"))
        subject.dismissDelete()
        advanceUntilIdle()

        assertNull(subject.pendingDeleteItem)
        assertNull(subject.deletingId)
        assertEquals(emptyList<String>(), history.deletedIds)
        assertEquals(emptyList<String>(), notices.plain)
        assertEquals(listOf("w1", "w2"), subject.snapshot.value.feed.items.map { it.id })
    }

    @Test
    fun `没有待确认项时确认删除不发请求`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1"))
        val subject = state(scope, history)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), history.deletedIds)
        assertNull(subject.deletingId)
        assertNull(subject.pendingDeleteItem)
    }

    @Test
    fun `确认删除成功后只抹掉该行并清空待确认项`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        val notices = FakeWatchNotices()
        val subject = state(scope, history, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w1"))
        subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("w1"), history.deletedIds)
        assertEquals(listOf("w2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(listOf(WATCH_DELETED_MESSAGE), notices.plain)
        assertNull(subject.pendingDeleteItem)
        assertNull(subject.deletingId)
        assertEquals(listOf(1), history.requestedPages)
    }

    @Test
    fun `确认删除普通失败时提示删除失败并保留该行`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        history.deleteOutcome = SessionOutcome.Failed("网络炸了")
        val notices = FakeWatchNotices()
        val subject = state(scope, history, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w1"))
        subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("删除失败：请稍后重试"), notices.plain)
        assertEquals(listOf("w1", "w2"), subject.snapshot.value.feed.items.map { it.id })
        assertNull(subject.pendingDeleteItem)
        assertNull(subject.deletingId)
    }

    @Test
    fun `确认删除会话失效时提示登录文案并打开登录`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        history.deleteOutcome = SessionOutcome.Expired
        val notices = FakeWatchNotices()
        var logins = 0
        val subject = state(
            scope = scope,
            history = history,
            notices = notices,
            onSessionExpired = { logins += 1 },
        )
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w1"))
        subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf(SessionOutcome.Expired.message), notices.plain)
        assertEquals(1, logins)
        assertEquals(listOf("w1", "w2"), subject.snapshot.value.feed.items.map { it.id })
    }

    @Test
    fun `删除在途时重复确认被忽略`() = runTest {
        val scope = harnessScope()
        val history = FakeWatchHistory()
        history.pageOutcome = SessionOutcome.Ok(works("w1", "w2"))
        val gate = CompletableDeferred<SessionOutcome<Unit>>()
        history.deleteGate = gate
        val notices = FakeWatchNotices()
        val subject = state(scope, history, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.requestDelete(work("w1"))
        subject.confirmDelete()
        advanceUntilIdle()
        assertEquals("w1", subject.deletingId)

        subject.requestDelete(work("w2"))
        subject.confirmDelete()
        advanceUntilIdle()
        assertEquals(listOf("w1"), history.deletedIds)

        gate.complete(SessionOutcome.Ok(Unit))
        advanceUntilIdle()

        assertEquals(listOf("w2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(listOf(WATCH_DELETED_MESSAGE), notices.plain)
        assertNull(subject.deletingId)
    }

    @Test
    fun `删除确认正文嵌入书名`() {
        assertEquals("确定要删除《作品w1》的观看记录吗？", watchDeleteMessage(work("w1")))
        assertEquals("确定要删除《海边的鲸娘》的观看记录吗？", watchDeleteMessage(work("w9", "海边的鲸娘")))
    }
}