// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 书库接口一页折算成 [com.jm3.core.paging.PageSlice]：接口只给总数，总页数由 30 条一页向上取整，
 * 末页判据用页码而不是条目数；总数为 0 或负数表示未提供，按 1 页处理，否则分页条没有页数可显示。
 */
class CouiLibraryScreenTest {

    @Test
    fun 总数折算总页数并判还有下一页() {
        val slice = couiLibrarySlice(items = List(30) { "creator-" + it }, total = 95, page = 1)
        assertEquals(4, slice.pageCount)
        assertTrue(slice.hasMore)
        assertEquals(95, slice.total)
        assertEquals(1, slice.page)
        assertEquals(30, slice.items.size)
    }

    @Test
    fun 最后一页不再续页() {
        val slice = couiLibrarySlice(items = List(5) { "creator-" + it }, total = 95, page = 4)
        assertEquals(4, slice.pageCount)
        assertFalse(slice.hasMore)
        assertEquals(4, slice.page)
    }

    @Test
    fun 总数正好整除时页数不偏多() {
        val first = couiLibrarySlice(items = List(30) { "work-" + it }, total = 60, page = 1)
        val second = couiLibrarySlice(items = List(30) { "work-" + it }, total = 60, page = 2)
        assertEquals(2, first.pageCount)
        assertTrue(first.hasMore)
        assertEquals(2, second.pageCount)
        assertFalse(second.hasMore)
    }

    @Test
    fun 总数未提供时按一页处理() {
        val slice = couiLibrarySlice(items = List(30) { "work-" + it }, total = 0, page = 1)
        assertEquals(1, slice.pageCount)
        assertFalse(slice.hasMore)
        assertEquals(0, slice.total)
    }

    @Test
    fun 负数总数同样按一页处理() {
        val slice = couiLibrarySlice(items = List(12) { "work-" + it }, total = -7, page = 1)
        assertEquals(1, slice.pageCount)
        assertFalse(slice.hasMore)
        assertEquals(-7, slice.total)
    }

    @Test
    fun 条目原样透传且保持顺序() {
        val slice = couiLibrarySlice(items = listOf("甲", "乙", "丙"), total = 3, page = 1)
        assertEquals(listOf("甲", "乙", "丙"), slice.items)
        assertEquals(1, slice.pageCount)
        assertFalse(slice.hasMore)
    }
}
