package com.jm3.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPagePlanTest {

    @Test
    fun `空章计划停在第0页且跳页是空操作`() {
        val plan = ReaderPagePlan.initial(0)

        assertEquals(0, plan.pageCount)
        assertEquals(0, plan.currentPage)
        assertFalse(plan.isJumping)
        assertSame(plan, jumpTo(plan, 3))
        assertEquals(plan, visiblePage(plan, 5))
        assertEquals(0, stepPages(plan, 1))
        assertEquals(emptyList(), prefetchAhead(plan))
    }

    @Test
    fun `有图计划从第1页开始且未在跳页`() {
        val plan = ReaderPagePlan.initial(12)

        assertEquals(12, plan.pageCount)
        assertEquals(1, plan.currentPage)
        assertFalse(plan.isJumping)
        assertEquals(12, plan.lastPage)
    }

    @Test
    fun `跳页把页码夹进范围并立即显示目标页`() {
        val plan = ReaderPagePlan.initial(12)

        val forward = jumpTo(plan, 7)
        assertEquals(7, forward.currentPage)
        assertEquals(7, forward.pendingTarget)
        assertTrue(forward.isJumping)

        assertEquals(1, jumpTo(plan, 0).currentPage)
        assertEquals(12, jumpTo(plan, 99).currentPage)
    }

    @Test
    fun `跳页期间可见页回写被拒绝`() {
        val jumping = jumpTo(ReaderPagePlan.initial(12), 9)

        // 动画途经第 3、5 页：页码必须停在目标页，否则顶栏会一路往回跳
        assertEquals(9, visiblePage(jumping, 2).currentPage)
        assertEquals(9, visiblePage(jumping, 4).currentPage)
        assertTrue(visiblePage(jumping, 4).isJumping)
    }

    @Test
    fun `跳页落地后恢复可见页回写`() {
        val settled = settleJump(jumpTo(ReaderPagePlan.initial(12), 9), 9)

        assertEquals(9, settled.currentPage)
        assertFalse(settled.isJumping)

        assertEquals(3, visiblePage(settled, 2).currentPage)
        assertEquals(12, visiblePage(settled, 11).currentPage)
    }

    @Test
    fun `旧的跳页动画落地不会清掉新目标`() {
        val superseded = jumpTo(jumpTo(ReaderPagePlan.initial(12), 4), 8)

        // 第一次动画结束：目标已被第二次跳页取代，冻结与页码都不能动
        val afterOldAnimation = settleJump(superseded, 4)
        assertEquals(8, afterOldAnimation.currentPage)
        assertEquals(8, afterOldAnimation.pendingTarget)
        assertTrue(afterOldAnimation.isJumping)

        val afterNewAnimation = settleJump(afterOldAnimation, 8)
        assertEquals(8, afterNewAnimation.currentPage)
        assertFalse(afterNewAnimation.isJumping)
    }

    @Test
    fun `可见页回写按页数收敛工具栏留白项`() {
        val plan = ReaderPagePlan.initial(5)

        assertEquals(1, visiblePage(plan, 0).currentPage)
        assertEquals(5, visiblePage(plan, 4).currentPage)
        // 列表末尾补的工具栏留白项让下标等于图片数，不能显示 6/5
        assertEquals(5, visiblePage(plan, 5).currentPage)
    }

    @Test
    fun `同页回写返回原计划避免多余重组`() {
        val plan = ReaderPagePlan.initial(5)

        assertSame(plan, visiblePage(plan, 0))
    }

    @Test
    fun `音量键翻页在首末页夹住`() {
        val plan = ReaderPagePlan.initial(5)

        assertEquals(1, stepPages(plan, -1))
        assertEquals(2, stepPages(plan, 1))
        assertEquals(5, stepPages(visiblePage(plan, 4), 1))
        assertEquals(4, stepPages(visiblePage(plan, 4), -1))
    }

    @Test
    fun `预取当前页之后的三个下标`() {
        val plan = visiblePage(ReaderPagePlan.initial(12), 4)

        assertEquals(5, plan.currentPage)
        assertEquals(listOf(5, 6, 7), prefetchAhead(plan))
    }

    @Test
    fun `预取在末页截断且不越界`() {
        val secondToLast = visiblePage(ReaderPagePlan.initial(12), 10)
        assertEquals(listOf(11), prefetchAhead(secondToLast))

        val last = visiblePage(ReaderPagePlan.initial(12), 11)
        assertEquals(12, last.currentPage)
        assertEquals(emptyList(), prefetchAhead(last))
    }

    @Test
    fun `预取数量可调且非正数给空`() {
        val plan = ReaderPagePlan.initial(12)

        assertEquals(listOf(1, 2), prefetchAhead(plan, ahead = 2))
        assertEquals(emptyList(), prefetchAhead(plan, ahead = 0))
        assertEquals(emptyList(), prefetchAhead(plan, ahead = -1))
    }
}
