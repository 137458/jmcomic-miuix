package com.jm3.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReaderPrefetchTest {

    @Test
    fun `纵向首屏预取可见页之后的页`() {
        val plan = ReaderPagePlan.initial(12)

        val prefetch = advancePrefetch(
            plan = plan,
            firstVisibleIndex = 0,
            loaded = emptySet(),
        )

        assertEquals(1, prefetch.plan.currentPage)
        assertEquals(listOf(1, 2, 3), prefetch.pages)
    }

    @Test
    fun `横向翻页按pager当前页给出同一组下标`() {
        val plan = ReaderPagePlan.initial(12)

        val prefetch = advancePrefetch(
            plan = plan,
            firstVisibleIndex = 3,
            loaded = emptySet(),
        )

        assertEquals(4, prefetch.plan.currentPage)
        assertEquals(listOf(4, 5, 6), prefetch.pages)
    }

    @Test
    fun `已入队的页不再重复出现`() {
        val plan = ReaderPagePlan.initial(12)

        val prefetch = advancePrefetch(
            plan = plan,
            firstVisibleIndex = 0,
            loaded = setOf(1, 3),
        )

        assertEquals(listOf(2), prefetch.pages)
    }

    @Test
    fun `末尾不足ahead页时只到最后一页`() {
        val plan = ReaderPagePlan.initial(12)

        val prefetch = advancePrefetch(
            plan = plan,
            firstVisibleIndex = 9,
            loaded = emptySet(),
        )

        assertEquals(listOf(10, 11), prefetch.pages)
    }

    @Test
    fun `末页与超出图片数的下标都没有预取`() {
        val plan = ReaderPagePlan.initial(12)

        assertEquals(emptyList(), advancePrefetch(plan, firstVisibleIndex = 11, loaded = emptySet()).pages)
        // 长图列表末尾补了工具栏留白项，下标可能等于图片数
        val beyond = advancePrefetch(plan, firstVisibleIndex = 12, loaded = emptySet())
        assertEquals(12, beyond.plan.currentPage)
        assertEquals(emptyList(), beyond.pages)
    }

    @Test
    fun `空章没有预取`() {
        val prefetch = advancePrefetch(
            plan = ReaderPagePlan.initial(0),
            firstVisibleIndex = 0,
            loaded = emptySet(),
        )

        assertEquals(0, prefetch.plan.currentPage)
        assertEquals(emptyList(), prefetch.pages)
    }

    @Test
    fun `ahead为零时不预取`() {
        val prefetch = advancePrefetch(
            plan = ReaderPagePlan.initial(12),
            firstVisibleIndex = 0,
            loaded = emptySet(),
            ahead = 0,
        )

        assertEquals(emptyList(), prefetch.pages)
    }

    @Test
    fun `预取页数按设置档位生效`() {
        val plan = ReaderPagePlan.initial(12)

        assertEquals(listOf(1, 2), advancePrefetch(plan, firstVisibleIndex = 0, loaded = emptySet(), ahead = 2).pages)
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            advancePrefetch(plan, firstVisibleIndex = 0, loaded = emptySet(), ahead = 5).pages,
        )
    }

    @Test
    fun `跳页进行中沿用原计划且不跳过目标页`() {
        val plan = jumpTo(ReaderPagePlan.initial(12), 7)

        val prefetch = advancePrefetch(
            plan = plan,
            firstVisibleIndex = 2,
            loaded = emptySet(),
        )

        assertSame(plan, prefetch.plan)
        assertEquals(listOf(7, 8, 9), prefetch.pages)
    }
}
