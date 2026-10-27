package com.jm3.core.download

import kotlin.test.Test
import kotlin.test.assertEquals

class PageNumbersTest {

    @Test
    fun `imageIndex converts 1-based page number to 0-based list index`() {
        assertEquals(0, PageNumbers.imageIndex(1)) // 边界：首页
        assertEquals(1, PageNumbers.imageIndex(2))
        assertEquals(6, PageNumbers.imageIndex(7))
        assertEquals(11, PageNumbers.imageIndex(12)) // 边界：末页（共 12 页）
    }

    @Test
    fun `pageNo converts 0-based list index to 1-based page number`() {
        assertEquals(1, PageNumbers.pageNo(0)) // 边界：首页
        assertEquals(2, PageNumbers.pageNo(1))
        assertEquals(7, PageNumbers.pageNo(6))
        assertEquals(12, PageNumbers.pageNo(11)) // 边界：末页（共 12 页）
    }

    @Test
    fun `conversion round trips for different page counts`() {
        // 两组差异化数据：3 页与 12 页，逐项双向回环。
        for (total in listOf(3, 12)) {
            for (imageIndex in 0 until total) {
                assertEquals(imageIndex, PageNumbers.imageIndex(PageNumbers.pageNo(imageIndex)), "total=$total")
            }
            for (pageNo in 1..total) {
                assertEquals(pageNo, PageNumbers.pageNo(PageNumbers.imageIndex(pageNo)), "total=$total")
            }
        }
    }
}
