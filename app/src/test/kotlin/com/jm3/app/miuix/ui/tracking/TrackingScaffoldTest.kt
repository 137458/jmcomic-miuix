// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.tracking

import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.core.model.TrackingItem
import com.jm3.core.model.TrackingPage
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

/** 假的追踪网关：页结局与取消结局由用例摆放，调用参数按顺序记账。 */
private class FakeTracking : TrackingPort {
    /** 没有按页码摆放结局时的默认页。 */
    var pageOutcome: SessionOutcome<TrackingPage> =
        SessionOutcome.Ok(TrackingPage(total = 0, items = emptyList()))

    /** 按页码摆放的页结局。 */
    val pageOutcomes = mutableMapOf<Int, SessionOutcome<TrackingPage>>()
    val requestedPages = mutableListOf<Int>()

    /** 取消/恢复追踪的结局队列，用完后回落到 [setOutcome]。 */
    val setOutcomes = mutableListOf<SessionOutcome<Unit>>()
    var setOutcome: SessionOutcome<Unit> = SessionOutcome.Ok(Unit)

    /** 非空时取消请求会挂住，直到用例放行：用来验证在途守卫。 */
    var setGate: CompletableDeferred<SessionOutcome<Unit>>? = null
    val toggledIds = mutableListOf<String>()

    override suspend fun page(page: Int): SessionOutcome<TrackingPage> {
        requestedPages += page
        return pageOutcomes[page] ?: pageOutcome
    }

    override suspend fun setTracking(albumId: String): SessionOutcome<Unit> {
        toggledIds += albumId
        setGate?.let { return it.await() }
        return setOutcomes.removeFirstOrNull() ?: setOutcome
    }
}

/** 假的提示口：带动作的提示按用例给的答复返回。 */
private class FakeNotices(var actionPerformed: Boolean = false) : TrackingNotices {
    val plain = mutableListOf<String>()
    val withAction = mutableListOf<Pair<String, String>>()

    override suspend fun show(message: String) {
        plain += message
    }

    override suspend fun showWithAction(message: String, actionLabel: String): Boolean {
        withAction += message to actionLabel
        return actionPerformed
    }
}

private fun item(id: String): TrackingItem =
    TrackingItem(id = id, name = "作品" + id, coverUrl = null, updateAt = null)

private fun okPage(vararg ids: String, total: Int = 0): SessionOutcome<TrackingPage> =
    SessionOutcome.Ok(
        TrackingPage(
            total = if (total > 0) total else ids.size,
            items = ids.map { item(it) },
        ),
    )

/**
 * 追踪骨架的行为用例：装载三态、每页条数记账、分页收敛、取消与撤销的每种结局。
 *
 * 全部走假端口，只断言业务输出与状态变化；皮肤侧的视觉不在这一层。
 */

/**
 * 独立测试作用域：与 runTest 共用同一个调度器，advanceUntilIdle() 才能推进它。
 *
 * TestScope.scope 上的任务在当前 kotlinx-coroutines-test 版本里不会被 advanceUntilIdle() 推进，
 * 用它会让装载、取消、删除这些协程体一次都不跑。
 */
private fun TestScope.harnessScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

class TrackingScaffoldTest {

    private fun state(
        scope: CoroutineScope,
        tracking: FakeTracking = FakeTracking(),
        notices: FakeNotices = FakeNotices(),
        hintGrowsWithLaterPages: Boolean = false,
        undoReload: TrackingUndoReload = TrackingUndoReload.FirstPage,
        onUnfollowed: () -> Unit = {},
    ): TrackingScaffoldState = TrackingScaffoldState(
        scope = scope,
        tracking = tracking,
        notices = notices,
        hintGrowsWithLaterPages = hintGrowsWithLaterPages,
        undoReload = undoReload,
        onUnfollowed = onUnfollowed,
    )

    @Test
    fun `初始状态是整屏加载态且没有在途动作`() = runTest {
        val scope = harnessScope()
        val subject = state(scope)

        assertEquals(emptyList<TrackingItem>(), subject.snapshot.value.feed.items)
        assertEquals(ListLoadState.Loading, subject.snapshot.value.state)
        assertNull(subject.removingId)
        assertEquals(0, subject.removedCount)
        assertEquals(0, subject.pageSizeHint)
    }

    @Test
    fun `首屏装载成功落定条目与实际条数`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 5)
        val subject = state(scope, tracking)

        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(listOf(1), tracking.requestedPages)
        assertEquals(listOf("a1", "a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(2, subject.pageSizeHint)
        assertEquals(5, subject.snapshot.value.feed.total)
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
        assertTrue(subject.snapshot.value.feed.hasMore)
    }

    @Test
    fun `空列表装载成功时判定没有下一页`() = runTest {
        val scope = harnessScope()
        val subject = state(scope)

        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(emptyList<TrackingItem>(), subject.snapshot.value.feed.items)
        assertFalse(subject.snapshot.value.feed.hasMore)
        assertEquals(0, subject.pageSizeHint)
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
    }

    @Test
    fun `首屏普通失败整屏给追踪列表兜底文案且不弹提示`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = SessionOutcome.Failed("网络炸了")
        val notices = FakeNotices()
        val subject = state(scope, tracking, notices)

        subject.loadFirstPage()
        advanceUntilIdle()

        val failed = subject.snapshot.value.state as ListLoadState.Failed
        assertEquals(TRACKING_FAILURE_MESSAGE, failed.message)
        assertFalse(failed.sessionExpired)
        assertEquals(emptyList<String>(), notices.plain)
    }

    @Test
    fun `首屏会话失效整屏给登录文案并标记失效`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = SessionOutcome.Expired
        val subject = state(scope, tracking)

        subject.loadFirstPage()
        advanceUntilIdle()

        val failed = subject.snapshot.value.state as ListLoadState.Failed
        assertEquals(SessionOutcome.Expired.message, failed.message)
        assertTrue(failed.sessionExpired)
    }

    @Test
    fun `首屏失败后重试重新拉第一页并装载成功`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = SessionOutcome.Failed("网络炸了")
        val subject = state(scope, tracking)

        subject.loadFirstPage()
        advanceUntilIdle()
        assertTrue(subject.snapshot.value.state is ListLoadState.Failed)

        tracking.pageOutcome = okPage("a1", total = 1)
        subject.loadFirstPage()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), tracking.requestedPages)
        assertEquals(listOf("a1"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
    }

    @Test
    fun `分页目标页收敛成相邻页且当前页不重发`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 5)
        val subject = state(scope, tracking)
        subject.loadFirstPage()
        advanceUntilIdle()

        assertNull(subject.pageToLoad(1))
        assertEquals(2, subject.pageToLoad(5))
        assertNull(subject.pageToLoad(0))
    }

    @Test
    fun `HyperOS 口径下后续页不放大每页条数提示`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcomes[1] = okPage("a1", "a2", total = 5)
        tracking.pageOutcomes[2] = okPage("b1", "b2", "b3", total = 5)
        val subject = state(scope, tracking, hintGrowsWithLaterPages = false)

        subject.loadFirstPage()
        advanceUntilIdle()
        subject.loadMore()
        advanceUntilIdle()

        assertEquals(2, subject.pageSizeHint)
        assertEquals(
            listOf("a1", "a2", "b1", "b2", "b3"),
            subject.snapshot.value.feed.items.map { it.id },
        )
        assertEquals(2, subject.snapshot.value.feed.page)
    }

    @Test
    fun `ColorOS 口径下后续页放大每页条数提示`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcomes[1] = okPage("a1", "a2", total = 5)
        tracking.pageOutcomes[2] = okPage("b1", "b2", "b3", total = 5)
        val subject = state(scope, tracking, hintGrowsWithLaterPages = true)

        subject.loadFirstPage()
        advanceUntilIdle()
        subject.loadMore()
        advanceUntilIdle()

        assertEquals(3, subject.pageSizeHint)
        assertEquals(
            listOf("a1", "a2", "b1", "b2", "b3"),
            subject.snapshot.value.feed.items.map { it.id },
        )
    }

    @Test
    fun `加载更多失败时保留已有条目并提示加载失败`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcomes[1] = okPage("a1", "a2", total = 5)
        tracking.pageOutcomes[2] = SessionOutcome.Failed("网络炸了")
        val notices = FakeNotices()
        val subject = state(scope, tracking, notices)

        subject.loadFirstPage()
        advanceUntilIdle()
        subject.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(ListLoadState.Loaded, subject.snapshot.value.state)
        assertTrue(subject.snapshot.value.feed.loadMoreFailed)
        assertEquals(1, notices.plain.size)
        assertTrue(notices.plain.single().startsWith("加载失败："))
    }

    @Test
    fun `取消追踪成功抹掉该行并给出撤销入口`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 2)
        val notices = FakeNotices()
        val subject = state(scope, tracking, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf("a1"), tracking.toggledIds)
        assertEquals(listOf("a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(1, subject.removedCount)
        assertNull(subject.removingId)
        assertEquals(listOf(TRACKING_UNFOLLOWED_MESSAGE to TRACKING_UNDO_LABEL), notices.withAction)
        assertEquals(emptyList<String>(), notices.plain)
        assertEquals(listOf(1), tracking.requestedPages)
    }

    @Test
    fun `取消追踪普通失败时提示操作失败并保留该行`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 2)
        tracking.setOutcome = SessionOutcome.Failed("网络炸了")
        val notices = FakeNotices()
        val subject = state(scope, tracking, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf("操作失败：请稍后重试"), notices.plain)
        assertEquals(emptyList<Pair<String, String>>(), notices.withAction)
        assertEquals(listOf("a1", "a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(0, subject.removedCount)
        assertNull(subject.removingId)
    }

    @Test
    fun `取消追踪会话失效时提示登录文案并保留该行`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 2)
        tracking.setOutcome = SessionOutcome.Expired
        val notices = FakeNotices()
        val subject = state(scope, tracking, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf(SessionOutcome.Expired.message), notices.plain)
        assertEquals(listOf("a1", "a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(0, subject.removedCount)
    }

    @Test
    fun `取消在途时重复点击被忽略`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 2)
        val gate = CompletableDeferred<SessionOutcome<Unit>>()
        tracking.setGate = gate
        val subject = state(scope, tracking)
        subject.loadFirstPage()
        advanceUntilIdle()
        val first = subject.snapshot.value.feed.items.first()

        subject.unfollow(first)
        advanceUntilIdle()
        assertEquals("a1", subject.removingId)

        subject.unfollow(first)
        advanceUntilIdle()
        assertEquals(listOf("a1"), tracking.toggledIds)

        gate.complete(SessionOutcome.Ok(Unit))
        advanceUntilIdle()
        assertNull(subject.removingId)
        assertEquals(1, subject.removedCount)
    }

    @Test
    fun `取消成功后触发皮肤钩子`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", total = 1)
        var haptics = 0
        val subject = state(scope, tracking, onUnfollowed = { haptics += 1 })
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(1, haptics)
    }

    @Test
    fun `撤销成功按 FirstPage 口径整页回源到第一页`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 5)
        val notices = FakeNotices(actionPerformed = true)
        val subject = state(
            scope = scope,
            tracking = tracking,
            notices = notices,
            undoReload = TrackingUndoReload.FirstPage,
        )
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf("a1", "a1"), tracking.toggledIds)
        assertEquals(listOf(1, 1), tracking.requestedPages)
        assertEquals(listOf("a1", "a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(0, subject.removedCount)
        assertEquals(1, subject.snapshot.value.feed.page)
        assertFalse(subject.snapshot.value.refreshing)
    }

    @Test
    fun `撤销成功按 CurrentPage 口径重拉取消时所在的页`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcomes[1] = okPage("a1", "a2", total = 5)
        tracking.pageOutcomes[2] = okPage("b1", "b2", "b3", total = 5)
        val notices = FakeNotices(actionPerformed = true)
        val subject = state(
            scope = scope,
            tracking = tracking,
            notices = notices,
            undoReload = TrackingUndoReload.CurrentPage,
        )
        subject.loadFirstPage()
        advanceUntilIdle()
        subject.goToPage(2)
        advanceUntilIdle()
        assertEquals(2, subject.snapshot.value.feed.page)

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 2), tracking.requestedPages)
        assertEquals(listOf("b1", "b2", "b3"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(2, subject.snapshot.value.feed.page)
        assertEquals(0, subject.removedCount)
        assertFalse(subject.snapshot.value.refreshing)
    }

    @Test
    fun `撤销普通失败时提示撤销失败且列表保持已抹除`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 5)
        tracking.setOutcomes += SessionOutcome.Ok(Unit)
        tracking.setOutcomes += SessionOutcome.Failed("网络炸了")
        val notices = FakeNotices(actionPerformed = true)
        val subject = state(scope, tracking, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf(TRACKING_UNFOLLOWED_MESSAGE to TRACKING_UNDO_LABEL), notices.withAction)
        assertEquals(listOf("撤销失败：请稍后重试"), notices.plain)
        assertEquals(listOf("a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(1, subject.removedCount)
        assertEquals(listOf(1), tracking.requestedPages)
    }

    @Test
    fun `撤销会话失效时提示登录文案且不回源`() = runTest {
        val scope = harnessScope()
        val tracking = FakeTracking()
        tracking.pageOutcome = okPage("a1", "a2", total = 5)
        tracking.setOutcomes += SessionOutcome.Ok(Unit)
        tracking.setOutcomes += SessionOutcome.Expired
        val notices = FakeNotices(actionPerformed = true)
        val subject = state(scope, tracking, notices)
        subject.loadFirstPage()
        advanceUntilIdle()

        subject.unfollow(subject.snapshot.value.feed.items.first())
        advanceUntilIdle()

        assertEquals(listOf(SessionOutcome.Expired.message), notices.plain)
        assertEquals(listOf("a2"), subject.snapshot.value.feed.items.map { it.id })
        assertEquals(listOf(1), tracking.requestedPages)
    }
}