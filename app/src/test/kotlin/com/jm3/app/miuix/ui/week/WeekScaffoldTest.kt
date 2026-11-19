package com.jm3.app.miuix.ui.week

import androidx.compose.runtime.mutableIntStateOf
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.model.SearchResult
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicks
import com.jm3.core.model.WeeklyPicksDetail
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 每周必看共享骨架：取期数、按期取详情、切期、刷新、失败落点。
 *
 * 网关换成注入的 lambda，只吐三种结局；断言落在业务输出上（期数、当期、选中项、失败计划、提示文案、
 * 每个请求落在哪一期）。作用域走 Unconfined，请求体不切调度器，需要观察「在途」时用闸门挂起。
 */
class WeekScaffoldTest {

    private fun period(id: String, time: String = "") = WeeklyPeriod(id = id, title = "第${id}期", time = time)

    private fun work(id: String) = SearchResult(
        id = id,
        title = "work-$id",
        authors = emptyList(),
        tags = emptyList(),
        coverUrl = null,
    )

    private fun detail(vararg ids: String) = WeeklyPicksDetail(
        total = ids.size,
        items = ids.map(::work),
    )

    private val threePeriods = listOf(period("p1", "11-01"), period("p2", "11-08"), period("p3", "11-15"))

    private inner class Harness(
        periodsFailureStyle: WeekPeriodsFailureStyle = WeekPeriodsFailureStyle.KeepContentNotice,
        detailLoadingStyle: WeekDetailLoadingStyle = WeekDetailLoadingStyle.ClearContent,
        refreshReset: WeekRefreshReset = WeekRefreshReset.WithPeriods,
        selectedIndex: Int = 0,
    ) {
        var picksAnswer: SessionOutcome<WeeklyPicks> = SessionOutcome.Ok(WeeklyPicks(threePeriods))
        var detailAnswer: (String) -> SessionOutcome<WeeklyPicksDetail> = { id ->
            SessionOutcome.Ok(detail("w-" + id))
        }

        val picksRequests = mutableListOf<Unit>()
        val detailRequests = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var picksGate: CompletableDeferred<Unit>? = null
        var detailGate: CompletableDeferred<Unit>? = null

        val state = WeekScaffoldState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadPicks = {
                picksRequests += Unit
                picksGate?.await()
                picksAnswer
            },
            loadPicksDetail = { periodId ->
                detailRequests += periodId
                detailGate?.await()
                detailAnswer(periodId)
            },
            showMessage = { message -> messages += message },
            periodsFailureStyle = periodsFailureStyle,
            detailLoadingStyle = detailLoadingStyle,
            refreshReset = refreshReset,
            selectedIndex = mutableIntStateOf(selectedIndex),
        )
    }

    @Test
    fun 初始态是加载中且期数为空() {
        val harness = Harness()

        val state = harness.state

        assertTrue(state.periodsLoading)
        assertTrue(state.periods.isEmpty())
        assertNull(state.detail)
        assertFalse(state.detailLoading)
        assertNull(state.periodsFailure)
        assertNull(state.detailFailure)
        assertFalse(state.refreshing)
        assertEquals(0, state.selected)
        assertNull(state.currentPeriod)
        assertTrue(harness.picksRequests.isEmpty())
        assertTrue(harness.detailRequests.isEmpty())
    }

    @Test
    fun 取期数成功后自动取当期详情() {
        val harness = Harness()

        harness.state.loadPeriods()

        val state = harness.state
        assertEquals(listOf("p1", "p2", "p3"), state.periods.map { it.id })
        assertEquals(0, state.selected)
        assertEquals("p1", state.currentPeriod?.id)
        assertFalse(state.periodsLoading)
        assertFalse(state.detailLoading)
        assertEquals(listOf("p1"), harness.detailRequests)
        assertEquals(listOf("w-p1"), state.detail?.items?.map { it.id })
        assertEquals(1, harness.picksRequests.size)
    }

    @Test
    fun 取期数成功后把越界选中项收回最后一期() {
        val harness = Harness(selectedIndex = 9)

        harness.state.loadPeriods()

        assertEquals(2, harness.state.selected)
        assertEquals("p3", harness.state.currentPeriod?.id)
        assertEquals(listOf("p3"), harness.detailRequests)
    }

    @Test
    fun 切期立刻清掉上一期内容() {
        val harness = Harness(detailLoadingStyle = WeekDetailLoadingStyle.ClearContent)
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.detailGate = gate

        harness.state.selectPeriod(2)

        val state = harness.state
        assertEquals(2, state.selected)
        assertTrue(state.detailLoading)
        assertNull(state.detail)
        assertEquals(listOf("p1", "p3"), harness.detailRequests)

        gate.complete(Unit)

        assertEquals(listOf("w-p3"), state.detail?.items?.map { it.id })
        assertFalse(state.detailLoading)
    }

    @Test
    fun 切到同一期不重复取详情() {
        val harness = Harness()
        harness.state.loadPeriods()

        harness.state.selectPeriod(0)

        assertEquals(1, harness.detailRequests.size)
        assertEquals(0, harness.state.selected)
    }

    @Test
    fun 快速切期时旧响应不覆盖当前期() {
        val harness = Harness()
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.detailGate = gate

        harness.state.selectPeriod(1)
        harness.state.selectPeriod(2)
        gate.complete(Unit)

        assertEquals(listOf("p1", "p2", "p3"), harness.detailRequests)
        assertEquals("p3", harness.state.currentPeriod?.id)
        assertEquals(listOf("w-p3"), harness.state.detail?.items?.map { it.id })
    }

    @Test
    fun 详情请求失败给出详情失败态() {
        val harness = Harness()
        harness.state.loadPeriods()
        harness.detailAnswer = { SessionOutcome.Failed("boom", IllegalStateException("boom")) }

        harness.state.selectPeriod(1)

        val failure = harness.state.detailFailure
        assertNotNull(failure)
        assertEquals(ScreenLoadPhase.Failed, failure.phase)
        assertEquals("加载失败", failure.message)
        assertFalse(harness.state.detailLoading)
        assertNull(harness.state.periodsFailure)
    }

    @Test
    fun 详情失效落成去登录态() {
        val harness = Harness()
        harness.state.loadPeriods()
        harness.detailAnswer = { SessionOutcome.Expired }

        harness.state.selectPeriod(1)

        val failure = harness.state.detailFailure
        assertNotNull(failure)
        assertEquals(ScreenLoadPhase.Expired, failure.phase)
        assertEquals(LOGIN_EXPIRED_MESSAGE, failure.message)
    }

    @Test
    fun 详情失败后重试成功写入内容() {
        val harness = Harness()
        harness.state.loadPeriods()
        harness.detailAnswer = { SessionOutcome.Failed("boom", IllegalStateException("boom")) }
        harness.state.selectPeriod(1)

        harness.detailAnswer = { id -> SessionOutcome.Ok(detail("fixed-" + id)) }
        harness.state.retryDetail()

        assertNull(harness.state.detailFailure)
        assertEquals(listOf("fixed-p2"), harness.state.detail?.items?.map { it.id })
        assertEquals(listOf("p1", "p2", "p2"), harness.detailRequests)
    }

    @Test
    fun 期数失败且没有期数时占屏并带兜底文案() {
        val harness = Harness(periodsFailureStyle = WeekPeriodsFailureStyle.KeepContentNotice)
        harness.picksAnswer = SessionOutcome.Failed("boom", IllegalStateException("boom"))

        harness.state.loadPeriods()

        val failure = harness.state.periodsFailure
        assertNotNull(failure)
        assertEquals(ScreenLoadPhase.Failed, failure.phase)
        assertEquals("加载失败", failure.message)
        assertFalse(harness.state.periodsLoading)
        assertTrue(harness.messages.isEmpty())
        assertTrue(harness.detailRequests.isEmpty())
    }

    @Test
    fun 期数失效且没有期数时落成去登录态() {
        val harness = Harness(periodsFailureStyle = WeekPeriodsFailureStyle.KeepContentNotice)
        harness.picksAnswer = SessionOutcome.Expired

        harness.state.loadPeriods()

        val failure = harness.state.periodsFailure
        assertNotNull(failure)
        assertEquals(ScreenLoadPhase.Expired, failure.phase)
        assertEquals(LOGIN_EXPIRED_MESSAGE, failure.message)
    }

    @Test
    fun 期数刷新失败但有旧期数时只弹提示不清内容() {
        val harness = Harness(periodsFailureStyle = WeekPeriodsFailureStyle.KeepContentNotice)
        harness.state.loadPeriods()
        harness.picksAnswer = SessionOutcome.Failed("boom", IllegalStateException("boom"))

        harness.state.refresh()

        assertEquals(listOf("p1", "p2", "p3"), harness.state.periods.map { it.id })
        assertNull(harness.state.periodsFailure)
        assertNull(harness.state.detailFailure)
        assertEquals(listOf("刷新失败：加载失败"), harness.messages)
    }

    @Test
    fun 期数失败但有期数时把落点换成详情槽位且不弹提示() {
        val harness = Harness(periodsFailureStyle = WeekPeriodsFailureStyle.SplitAcrossSlots)
        harness.state.loadPeriods()
        // 先让当前期失败，详情槽位上有位置可占
        harness.detailAnswer = { SessionOutcome.Failed("boom", IllegalStateException("boom")) }
        harness.state.selectPeriod(1)
        harness.picksAnswer = SessionOutcome.Failed("boom", IllegalStateException("boom"))

        harness.state.refresh()

        val failure = harness.state.detailFailure
        assertNotNull(failure)
        assertEquals(ScreenLoadPhase.Failed, failure.phase)
        assertEquals("加载失败", failure.message)
        assertNull(harness.state.periodsFailure)
        assertEquals(listOf("p1", "p2", "p3"), harness.state.periods.map { it.id })
        assertTrue(harness.messages.isEmpty())
    }

    @Test
    fun 期数失败且已有详情时两个槽位都不占() {
        val harness = Harness(periodsFailureStyle = WeekPeriodsFailureStyle.SplitAcrossSlots)
        harness.state.loadPeriods()
        harness.picksAnswer = SessionOutcome.Failed("boom", IllegalStateException("boom"))

        harness.state.refresh()

        assertNull(harness.state.periodsFailure)
        assertNull(harness.state.detailFailure)
        assertEquals(listOf("w-p1"), harness.state.detail?.items?.map { it.id })
        assertTrue(harness.messages.isEmpty())
    }

    @Test
    fun 空期数列表时收起旧详情() {
        val harness = Harness()
        harness.state.loadPeriods()
        harness.picksAnswer = SessionOutcome.Ok(WeeklyPicks(emptyList()))

        harness.state.refresh()

        val state = harness.state
        assertTrue(state.periods.isEmpty())
        assertNull(state.detail)
        assertFalse(state.detailLoading)
        assertNull(state.currentPeriod)
        assertFalse(state.refreshing)
    }

    @Test
    fun 刷新重取期数但不显示整屏加载态() {
        val harness = Harness(refreshReset = WeekRefreshReset.WithPeriods)
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.picksGate = gate

        harness.state.refresh()

        assertTrue(harness.state.refreshing)
        assertFalse(harness.state.periodsLoading)
        assertEquals(2, harness.picksRequests.size)

        gate.complete(Unit)

        assertFalse(harness.state.refreshing)
    }

    @Test
    fun 刷新指示器在期数请求结束时收起() {
        val harness = Harness(refreshReset = WeekRefreshReset.WithPeriods)
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.detailGate = gate

        harness.state.refresh()

        assertFalse(harness.state.refreshing)
        assertTrue(harness.state.detailLoading)

        gate.complete(Unit)

        assertEquals(listOf("w-p1"), harness.state.detail?.items?.map { it.id })
        assertFalse(harness.state.detailLoading)
    }

    @Test
    fun 刷新指示器等详情请求结束才收起() {
        val harness = Harness(
            refreshReset = WeekRefreshReset.WithDetail,
            detailLoadingStyle = WeekDetailLoadingStyle.KeepContent,
        )
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.detailGate = gate

        harness.state.refresh()

        // 期数已经回来，但这一皮肤要等详情回来才收指示器
        assertTrue(harness.state.refreshing)
        assertFalse(harness.state.detailLoading)
        assertEquals(listOf("w-p1"), harness.state.detail?.items?.map { it.id })

        gate.complete(Unit)

        assertFalse(harness.state.refreshing)
    }

    @Test
    fun 清空式皮肤在刷新期间就拿掉旧内容() {
        val harness = Harness(
            detailLoadingStyle = WeekDetailLoadingStyle.ClearContent,
            refreshReset = WeekRefreshReset.WithDetail,
        )
        harness.state.loadPeriods()
        val gate = CompletableDeferred<Unit>()
        harness.detailGate = gate

        harness.state.refresh()

        assertTrue(harness.state.detailLoading)
        assertNull(harness.state.detail)
        assertTrue(harness.state.refreshing)
    }
}
