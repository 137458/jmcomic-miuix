package com.jm3.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 双页映射的业务契约。
 *
 * 采用「首页单页 + 其余两页一屏」的书籍排布：
 * - 第 1 页（封面）单独占一屏，右侧留白；
 * - 第 s 屏（s >= 1）装第 2s 与 2s+1 页；
 * - 末尾不足两页时单页居中、另一侧补空。
 *
 * 页码、进度与跳页一律用单页索引（1 基），跨页索引只是 pager 的临时下标；
 * 这样转屏、换设备或切换单页模式都不会把进度错开一页。
 */
class ReaderSpreadPolicyTest {

    @Test
    fun `只有六百dp以上横屏才允许双页`() {
        assertTrue(ReaderSpreadPolicy.supportsSpread(600, 400))
        assertTrue(ReaderSpreadPolicy.supportsSpread(900, 600))
        // 差 1dp 不算平板横屏
        assertFalse(ReaderSpreadPolicy.supportsSpread(599, 399))
        // 竖屏平板即使够宽也不允许：两页并排会把单页压得过窄
        assertFalse(ReaderSpreadPolicy.supportsSpread(600, 900))
        // 正方形窗口按竖屏处理
        assertFalse(ReaderSpreadPolicy.supportsSpread(600, 600))
    }

    @Test
    fun `跨页数量按首页单页加两页一屏计算`() {
        assertEquals(0, ReaderSpreadPolicy.spreadCount(0))
        assertEquals(1, ReaderSpreadPolicy.spreadCount(1))
        assertEquals(2, ReaderSpreadPolicy.spreadCount(2))
        assertEquals(2, ReaderSpreadPolicy.spreadCount(3))
        assertEquals(3, ReaderSpreadPolicy.spreadCount(4))
        assertEquals(3, ReaderSpreadPolicy.spreadCount(5))
        assertEquals(4, ReaderSpreadPolicy.spreadCount(6))
    }

    @Test
    fun `首页单独成屏`() {
        assertEquals(listOf(1), ReaderSpreadPolicy.pagesInSpread(0, 5))
        assertEquals(listOf(1), ReaderSpreadPolicy.pagesInSpread(0, 1))
    }

    @Test
    fun `首页之后每两页组成一屏`() {
        assertEquals(listOf(2, 3), ReaderSpreadPolicy.pagesInSpread(1, 5))
        assertEquals(listOf(4, 5), ReaderSpreadPolicy.pagesInSpread(2, 5))
    }

    @Test
    fun `末尾只剩一页时该屏只有一个单页`() {
        // 总页数为偶数时，首页之后剩余页数是奇数，最后一屏只有一页，另一侧补空
        assertEquals(listOf(4), ReaderSpreadPolicy.pagesInSpread(2, 4))
        assertEquals(listOf(2), ReaderSpreadPolicy.pagesInSpread(1, 2))
    }

    @Test
    fun `越界的跨页下标给空列表`() {
        assertEquals(emptyList(), ReaderSpreadPolicy.pagesInSpread(-1, 5))
        assertEquals(emptyList(), ReaderSpreadPolicy.pagesInSpread(3, 5))
        assertEquals(emptyList(), ReaderSpreadPolicy.pagesInSpread(0, 0))
    }

    @Test
    fun `单页索引到跨页索引按首页单页规则换算`() {
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(1, 5))
        assertEquals(1, ReaderSpreadPolicy.spreadIndexForPage(2, 5))
        assertEquals(1, ReaderSpreadPolicy.spreadIndexForPage(3, 5))
        assertEquals(2, ReaderSpreadPolicy.spreadIndexForPage(4, 5))
        assertEquals(2, ReaderSpreadPolicy.spreadIndexForPage(5, 5))
    }

    @Test
    fun `单页索引越界时夹进范围`() {
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(0, 5))
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(99, 1))
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(5, 0))
    }

    @Test
    fun `跨页锚点取该屏第一个单页`() {
        assertEquals(1, ReaderSpreadPolicy.anchorPageForSpread(0, 5))
        assertEquals(2, ReaderSpreadPolicy.anchorPageForSpread(1, 5))
        assertEquals(4, ReaderSpreadPolicy.anchorPageForSpread(2, 5))
        assertEquals(0, ReaderSpreadPolicy.anchorPageForSpread(9, 5))
    }

    @Test
    fun `任意单页换算到其所在屏的锚点页`() {
        // 跳第 5 页应落在含第 4、5 页的那一屏，锚点统一记第 4 页
        assertEquals(1, ReaderSpreadPolicy.anchorPageForPage(1, 5))
        assertEquals(2, ReaderSpreadPolicy.anchorPageForPage(2, 5))
        assertEquals(2, ReaderSpreadPolicy.anchorPageForPage(3, 5))
        assertEquals(4, ReaderSpreadPolicy.anchorPageForPage(4, 5))
        assertEquals(4, ReaderSpreadPolicy.anchorPageForPage(5, 5))
    }

    @Test
    fun `进度落盘与恢复的锚点始终落在原跨页内`() {
        val total = 6
        for (page in 1..total) {
            val anchor = ReaderSpreadPolicy.anchorPageForPage(page, total)
            val spread = ReaderSpreadPolicy.spreadIndexForPage(anchor, total)
            assertTrue(
                page in ReaderSpreadPolicy.pagesInSpread(spread, total),
                "第 $page 页恢复后应仍在第 $spread 屏内，锚点=$anchor",
            )
        }
    }

    @Test
    fun `转屏回单页再转回双页不会错开一页`() {
        val total = 5
        // 双页里读到第 5 页，落盘按单页索引记锚点第 4 页
        val saved = ReaderSpreadPolicy.anchorPageForPage(5, total)
        assertEquals(4, saved)
        // 转竖屏走单页：恢复第 4 页
        assertEquals(4, resolveRestoredPage(ReaderPageProgress(page = saved, total = total), total))
        // 再转回双页：第 4 页仍在原来那一屏（第 4、5 页）
        val spread = ReaderSpreadPolicy.spreadIndexForPage(saved, total)
        assertEquals(listOf(4, 5), ReaderSpreadPolicy.pagesInSpread(spread, total))
    }

    @Test
    fun `换章节后首页仍映射到第一屏`() {
        // 章节各自维护页数，首页永远单独成屏，不会因上一章页数为奇数而错位
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(1, 7))
        assertEquals(0, ReaderSpreadPolicy.spreadIndexForPage(1, 8))
        assertEquals(listOf(1), ReaderSpreadPolicy.pagesInSpread(0, 7))
    }

    @Test
    fun `跨屏步进按屏推进并夹在首尾`() {
        assertEquals(2, ReaderSpreadPolicy.stepAnchor(1, 1, 5))
        assertEquals(4, ReaderSpreadPolicy.stepAnchor(2, 1, 5))
        assertEquals(4, ReaderSpreadPolicy.stepAnchor(4, 1, 5))
        assertEquals(2, ReaderSpreadPolicy.stepAnchor(4, -1, 5))
        assertEquals(1, ReaderSpreadPolicy.stepAnchor(2, -1, 5))
        assertEquals(1, ReaderSpreadPolicy.stepAnchor(1, -1, 5))
        assertEquals(1, ReaderSpreadPolicy.stepAnchor(1, 0, 5))
        assertEquals(0, ReaderSpreadPolicy.stepAnchor(1, 1, 0))
    }

    @Test
    fun `从屏内第二页步进不会卡在同一屏`() {
        // 锚点 4、当前显示第 4、5 页：向后一步必须跳到下一屏而不是仍停在 4/5
        assertEquals(6, ReaderSpreadPolicy.stepAnchor(5, 1, 7))
        assertEquals(1, ReaderSpreadPolicy.stepAnchor(3, -1, 7))
    }

    @Test
    fun `HorizontalPager 页键隔离章节、单双页模式与具体页位置`() {
        val k1Single = ReaderSpreadPolicy.pagerKey("ch-1", spreadActive = false, pageCount = 5, index = 0, singlePageIndex = 1)
        val k1Spread = ReaderSpreadPolicy.pagerKey("ch-1", spreadActive = true, pageCount = 5, index = 0)
        val k2Single = ReaderSpreadPolicy.pagerKey("ch-2", spreadActive = false, pageCount = 5, index = 0, singlePageIndex = 1)
        val spreadPair = ReaderSpreadPolicy.pagerKey("ch-1", spreadActive = true, pageCount = 5, index = 1)
        val outA = ReaderSpreadPolicy.pagerKey("ch-1", spreadActive = true, pageCount = 5, index = 9)
        val outB = ReaderSpreadPolicy.pagerKey("ch-1", spreadActive = true, pageCount = 5, index = 10)

        assertEquals(6, setOf(k1Single, k1Spread, k2Single, spreadPair, outA, outB).size)
        assertTrue(spreadPair.contains("2-3"))
        assertTrue(k1Single.contains("1"))
    }
}
