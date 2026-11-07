package com.jm3.core.paging

import com.jm3.core.settings.PaginationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 分页条页码计算的纯函数行为，迁移自 :app，删去两个无生产调用者的入口用例。 */
class PagingBehaviorTest {
    @Test
    fun 下一页在中间页递增() {
        assertEquals(3, pagerNextPage(2, 5))
    }

    @Test
    fun 下一页在末页钳制到总页数() {
        assertEquals(5, pagerNextPage(5, 5))
    }

    @Test
    fun 下一页在只有一页时停在第一页() {
        assertEquals(1, pagerNextPage(1, 1))
    }

    @Test
    fun 下一页在总页数为零时回到第一页() {
        assertEquals(1, pagerNextPage(3, 0))
    }

    @Test
    fun 页码收敛把越界值拉回合法区间() {
        assertEquals(5, pagerClamp(9, 5))
        assertEquals(1, pagerClamp(0, 5))
        assertEquals(3, pagerClamp(3, 5))
    }

    @Test
    fun 总页数为零时页码收敛到第一页() {
        assertEquals(1, pagerClamp(4, 0))
    }

    @Test
    fun 翻页目标页在总页数已知时收敛到合法区间() {
        assertEquals(3, pagerTargetPage(currentPage = 2, requestedPage = 3, pageCount = 5))
        assertEquals(1, pagerTargetPage(currentPage = 2, requestedPage = 1, pageCount = 5))
        assertEquals(5, pagerTargetPage(currentPage = 5, requestedPage = 6, pageCount = 5))
    }

    @Test
    fun 翻页目标页在总页数未知时按当前页偏移() {
        assertEquals(3, pagerTargetPage(currentPage = 2, requestedPage = 3, pageCount = null))
        assertEquals(1, pagerTargetPage(currentPage = 2, requestedPage = 1, pageCount = null))
        assertEquals(3, pagerTargetPage(currentPage = 2, requestedPage = 3, pageCount = 0))
    }

    @Test
    fun 分页模式变化时回到第一页重载() {
        assertEquals(1, pagingModeReloadPage(PaginationMode.INFINITE, PaginationMode.CLICK))
        assertEquals(1, pagingModeReloadPage(PaginationMode.CLICK, PaginationMode.INFINITE))
    }

    @Test
    fun 分页模式没变时不重载() {
        assertNull(pagingModeReloadPage(PaginationMode.CLICK, PaginationMode.CLICK))
        assertNull(pagingModeReloadPage(PaginationMode.INFINITE, PaginationMode.INFINITE))
    }
}
