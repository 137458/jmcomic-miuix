package com.jm3.core.paging

import com.jm3.core.settings.PaginationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun 总数折算总页数向上取整() {
        assertEquals(10, pageCountOf(total = 91, pageSize = 10))
        assertEquals(10, pageCountOf(total = 100, pageSize = 10))
        assertEquals(1, pageCountOf(total = 1, pageSize = 30))
        assertEquals(3, pageCountOf(total = 61, pageSize = 30))
    }

    @Test
    fun 总数为零或负数时总页数未知() {
        assertNull(pageCountOf(total = 0, pageSize = 30))
        assertNull(pageCountOf(total = -5, pageSize = 30))
    }

    @Test
    fun 每页条数非法时立即失败() {
        assertFailsWith<IllegalArgumentException> { pageCountOf(total = 10, pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { pageCountAtLeastOne(total = 10, pageSize = -1) }
    }

    @Test
    fun 总页数至少为一() {
        assertEquals(1, pageCountAtLeastOne(total = 0, pageSize = 30))
        assertEquals(1, pageCountAtLeastOne(total = 1, pageSize = 30))
        assertEquals(3, pageCountAtLeastOne(total = 61, pageSize = 30))
    }

    @Test
    fun 页码判末页() {
        assertTrue(hasMorePages(page = 1, pageCount = 3))
        assertFalse(hasMorePages(page = 3, pageCount = 3))
        assertFalse(hasMorePages(page = 4, pageCount = 3))
    }

    @Test
    fun 装满一页才可能有下一页() {
        assertTrue(pageLooksFull(itemCount = 20, pageSize = 20))
        assertFalse(pageLooksFull(itemCount = 19, pageSize = 20))
        assertFalse(pageLooksFull(itemCount = 0, pageSize = 20))
    }

    @Test
    fun 已加载条数按每页条数累加() {
        assertEquals(40, loadedThrough(page = 2, pageSizeHint = 20, pageItemCount = 20))
        assertEquals(45, loadedThrough(page = 3, pageSizeHint = 20, pageItemCount = 5))
    }

    @Test
    fun 已加载条数在每页条数未知时按本页条数补() {
        assertEquals(7, loadedThrough(page = 1, pageSizeHint = 0, pageItemCount = 7))
        assertEquals(14, loadedThrough(page = 2, pageSizeHint = 0, pageItemCount = 7))
    }

    @Test
    fun 本页为空时已加载条数不倒退() {
        assertEquals(20, loadedThrough(page = 2, pageSizeHint = 20, pageItemCount = 0))
        assertEquals(0, loadedThrough(page = 0, pageSizeHint = 20, pageItemCount = 0))
    }

    @Test
    fun 已加载条数未达总数时还有下一页() {
        assertTrue(hasMoreByLoaded(pageItemCount = 20, loaded = 40, total = 100))
        assertFalse(hasMoreByLoaded(pageItemCount = 5, loaded = 45, total = 45))
    }

    @Test
    fun 本页为空时按到头处理() {
        assertFalse(hasMoreByLoaded(pageItemCount = 0, loaded = 20, total = 100))
    }
}
