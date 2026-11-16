package com.jm3.app.miuix.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 一屏之内的页框排布：两页及以上并排，单页（首页与末尾补空）居中半宽。
 */
class ReaderSpreadArrangeTest {

    @Test
    fun 两页及以上并排显示() {
        assertEquals(ReaderSpreadArrange.SideBySide, spreadArrangeFor(2))
        assertEquals(ReaderSpreadArrange.SideBySide, spreadArrangeFor(3))
    }

    @Test
    fun 首页与末尾补空单页居中() {
        assertEquals(ReaderSpreadArrange.SingleCentered, spreadArrangeFor(1))
        assertEquals(ReaderSpreadArrange.SingleCentered, spreadArrangeFor(0))
    }
}
