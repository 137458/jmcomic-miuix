package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 折叠状态机的纯逻辑：阈值、滞回、锁定与吸附。 */
class SearchHeaderCollapseTest {

    private val height = 120f
    private val collapseThreshold = 24f
    private val expandThreshold = 16f

    private fun scroll(
        state: SearchHeaderCollapseState,
        delta: Float,
        locked: Boolean = false,
    ) = SearchHeaderCollapseLogic.onScroll(
        state = state,
        deltaPx = delta,
        heightPx = height,
        locked = locked,
        collapseThresholdPx = collapseThreshold,
        expandThresholdPx = expandThreshold,
    )

    @Test
    fun `向下滚动不足折叠阈值时保持展开`() {
        val next = scroll(SearchHeaderCollapseState(), -20f)

        assertEquals(SearchHeaderIntent.Expanded, next.intent)
        assertEquals(0f, next.progress)
        assertEquals(20f, next.collapseDebtPx)
    }

    @Test
    fun `越过折叠阈值后开始跟手收起`() {
        val next = scroll(SearchHeaderCollapseState(), -30f)

        assertEquals(SearchHeaderIntent.Collapsed, next.intent)
        assertEquals(0.25f, next.progress)
        assertEquals(0f, next.collapseDebtPx)
    }

    @Test
    fun `同向滚动累计进度并钳制在 1`() {
        val collapsedOnce = scroll(SearchHeaderCollapseState(), -30f)
        val half = scroll(collapsedOnce, -60f)
        val full = scroll(half, -120f)

        assertEquals(0.75f, half.progress)
        assertEquals(1f, full.progress)
        assertEquals(SearchHeaderIntent.Collapsed, full.intent)
    }

    @Test
    fun `向上滚动不足展开阈值时保持收起`() {
        val collapsed = SearchHeaderCollapseState(
            progress = 1f,
            intent = SearchHeaderIntent.Collapsed,
        )
        val firstSmall = scroll(collapsed, 10f)
        val secondSmall = scroll(firstSmall, 10f)

        assertEquals(1f, firstSmall.progress)
        assertEquals(SearchHeaderIntent.Collapsed, firstSmall.intent)
        assertEquals(SearchHeaderIntent.Expanded, secondSmall.intent)
        assertEquals(1f - 10f / height, secondSmall.progress, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `小幅反向抖动先抵消滞回债而不是直接翻转`() {
        val pushedDown = scroll(SearchHeaderCollapseState(), -20f)
        val jitterUp = scroll(pushedDown, 10f)
        val pushedAgain = scroll(jitterUp, -10f)

        assertEquals(SearchHeaderIntent.Expanded, pushedAgain.intent)
        assertEquals(0f, pushedAgain.progress)
        assertEquals(20f, pushedAgain.collapseDebtPx)

        val crossing = scroll(pushedAgain, -5f)
        assertEquals(SearchHeaderIntent.Collapsed, crossing.intent)
        assertEquals(5f / height, crossing.progress, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `焦点锁定期间滚动不折叠`() {
        val locked = SearchHeaderCollapseState(
            progress = 0.5f,
            intent = SearchHeaderIntent.Collapsed,
        )
        val next = scroll(locked, -100f, locked = true)

        assertEquals(SearchHeaderIntent.Expanded, next.intent)
        assertEquals(0.5f, next.progress)
        assertEquals(0f, next.collapseDebtPx)
        assertEquals(0f, next.expandDebtPx)
    }

    @Test
    fun `锁定时的吸附目标始终为展开`() {
        val state = SearchHeaderCollapseState(progress = 0.9f, intent = SearchHeaderIntent.Collapsed)

        assertEquals(
            0f,
            SearchHeaderCollapseLogic.settleTarget(state, velocityPxPerSecond = -800f, locked = true),
        )
    }

    @Test
    fun `滚动停止吸附按速度与进度判定`() {
        val midCollapsed = SearchHeaderCollapseState(progress = 0.6f, intent = SearchHeaderIntent.Collapsed)
        val midExpanded = SearchHeaderCollapseState(progress = 0.4f, intent = SearchHeaderIntent.Expanded)
        val shallowButFlingDown = SearchHeaderCollapseState(
            progress = 0.2f,
            intent = SearchHeaderIntent.Expanded,
        )

        assertEquals(1f, SearchHeaderCollapseLogic.settleTarget(midCollapsed, 0f, locked = false))
        assertEquals(0f, SearchHeaderCollapseLogic.settleTarget(midExpanded, 0f, locked = false))
        assertEquals(
            1f,
            SearchHeaderCollapseLogic.settleTarget(shallowButFlingDown, -600f, locked = false),
        )
        assertEquals(
            0f,
            SearchHeaderCollapseLogic.settleTarget(midCollapsed, 600f, locked = false),
        )
    }

    @Test
    fun `强制展开重置进度与滞回债`() {
        val collapsed = SearchHeaderCollapseState(
            progress = 0.8f,
            intent = SearchHeaderIntent.Collapsed,
            collapseDebtPx = 12f,
            expandDebtPx = 7f,
        )

        val expanded = SearchHeaderCollapseLogic.forceExpanded(collapsed)

        assertEquals(0f, expanded.progress)
        assertEquals(SearchHeaderIntent.Expanded, expanded.intent)
        assertEquals(0f, expanded.collapseDebtPx)
        assertEquals(0f, expanded.expandDebtPx)
    }

    @Test
    fun `无效高度与零增量不改变状态`() {
        val state = SearchHeaderCollapseState(progress = 0.3f)

        val zeroDelta = scroll(state, 0f)
        val zeroHeight = SearchHeaderCollapseLogic.onScroll(
            state = state,
            deltaPx = -40f,
            heightPx = 0f,
            locked = false,
            collapseThresholdPx = collapseThreshold,
            expandThresholdPx = expandThreshold,
        )

        assertEquals(state, zeroDelta)
        assertTrue(state == zeroHeight)
    }
}
