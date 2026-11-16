package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EdgePageTurn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 跳页与边缘翻页的页码换算：单页页码 → 跨页锚点 → pager 下标 / 列表下标。
 *
 * 5 页章节边界最全：第 1 页单独成屏，2-3 与 4-5 各一屏（共 3 屏），末页落在偶数位上；
 * 10 页章节覆盖「整屏成对、末页另起一屏」的常规情形。
 */
class ReaderNavigationLogicTest {

    @Test
    fun 双页跳页先换算到所在跨页的锚点页() {
        // 第 2、3 页同屏，锚点统一记第 2 页；第 4、5 页同屏，锚点记第 4 页
        assertEquals(1, planJumpPage(1, 5, spreadActive = true))
        assertEquals(2, planJumpPage(2, 5, spreadActive = true))
        assertEquals(2, planJumpPage(3, 5, spreadActive = true))
        assertEquals(4, planJumpPage(4, 5, spreadActive = true))
        assertEquals(4, planJumpPage(5, 5, spreadActive = true))
    }

    @Test
    fun 双页跳页在偶数页数与末页上同样落到锚点() {
        // 10 页：2-3 / 4-5 / 6-7 / 8-9 / 10 单独一屏
        assertEquals(8, planJumpPage(9, 10, spreadActive = true))
        assertEquals(10, planJumpPage(10, 10, spreadActive = true))
    }

    @Test
    fun 单页跳页页码原样保留() {
        assertEquals(1, planJumpPage(1, 10, spreadActive = false))
        assertEquals(7, planJumpPage(7, 10, spreadActive = false))
        assertEquals(10, planJumpPage(10, 10, spreadActive = false))
    }

    @Test
    fun 跳页换算不替会话做夹取() {
        // 越界页码原样带到 requestJump，由会话按 1..pageCount 夹取
        assertEquals(12, planJumpPage(12, 10, spreadActive = false))
        // 空章双页下没有跨页可落，锚点为 0
        assertEquals(0, planJumpPage(0, 0, spreadActive = true))
    }

    @Test
    fun `双页下 pager 目标取跨页下标`() {
        // 5 页 3 屏：第 1 页在第 0 屏，第 3 页在第 1 屏，第 5 页在第 2 屏
        assertEquals(0, planPagerIndex(1, 5, spreadActive = true, pagerPageCount = 3))
        assertEquals(1, planPagerIndex(3, 5, spreadActive = true, pagerPageCount = 3))
        assertEquals(2, planPagerIndex(5, 5, spreadActive = true, pagerPageCount = 3))
    }

    @Test
    fun `双页下末页另起一屏时 pager 下标跟着屏走`() {
        // 10 页 6 屏：第 9 页在第 4 屏，第 10 页独占第 5 屏
        assertEquals(4, planPagerIndex(9, 10, spreadActive = true, pagerPageCount = 6))
        assertEquals(5, planPagerIndex(10, 10, spreadActive = true, pagerPageCount = 6))
    }

    @Test
    fun `单页下 pager 目标是页码减一`() {
        assertEquals(0, planPagerIndex(1, 10, spreadActive = false, pagerPageCount = 10))
        assertEquals(4, planPagerIndex(5, 10, spreadActive = false, pagerPageCount = 10))
        assertEquals(9, planPagerIndex(10, 10, spreadActive = false, pagerPageCount = 10))
    }

    @Test
    fun `pager 目标越界时夹到端点屏`() {
        // pager 页数还停在旧章（更少）时，目标不能落到不存在的屏
        assertEquals(2, planPagerIndex(10, 10, spreadActive = false, pagerPageCount = 3))
        assertEquals(0, planPagerIndex(1, 4, spreadActive = true, pagerPageCount = 2))
        assertEquals(0, planPagerIndex(0, 0, spreadActive = false, pagerPageCount = 0))
        assertEquals(0, planPagerIndex(3, 5, spreadActive = true, pagerPageCount = 0))
    }

    @Test
    fun 纵向列表目标是页码减一() {
        assertEquals(0, planListIndex(1))
        assertEquals(4, planListIndex(5))
        assertEquals(11, planListIndex(12))
    }

    @Test
    fun `边缘翻下一页时 pager 下标加一`() {
        assertEquals(1, planEdgeTurnPagerIndex(0, EdgePageTurn.Next, pagerPageCount = 10))
        assertEquals(3, planEdgeTurnPagerIndex(2, EdgePageTurn.Next, pagerPageCount = 10))
    }

    @Test
    fun `边缘翻上一页时 pager 下标减一`() {
        assertEquals(1, planEdgeTurnPagerIndex(2, EdgePageTurn.Previous, pagerPageCount = 10))
        assertEquals(8, planEdgeTurnPagerIndex(9, EdgePageTurn.Previous, pagerPageCount = 10))
    }

    @Test
    fun `首页与末屏继续翻页都夹在有效范围内`() {
        assertEquals(0, planEdgeTurnPagerIndex(0, EdgePageTurn.Previous, pagerPageCount = 10))
        assertEquals(2, planEdgeTurnPagerIndex(2, EdgePageTurn.Next, pagerPageCount = 3))
        assertEquals(0, planEdgeTurnPagerIndex(0, EdgePageTurn.Next, pagerPageCount = 1))
        // 双页 7 页共 4 屏：末屏继续翻下一页仍停在末屏
        assertEquals(3, planEdgeTurnPagerIndex(3, EdgePageTurn.Next, pagerPageCount = 4))
        assertEquals(2, planEdgeTurnPagerIndex(3, EdgePageTurn.Previous, pagerPageCount = 4))
    }
}
