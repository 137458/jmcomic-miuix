package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 搜索框输入到车号的判定：只有纯半角数字才直达作品，其它一律回落到关键词搜索。 */
class AlbumCodeTest {

    @Test
    fun `纯数字直接作为车号`() {
        assertEquals("123456", albumCodeOf("123456"))
    }

    @Test
    fun `首尾空白不影响车号判定`() {
        assertEquals("123456", albumCodeOf("  123456 \n"))
    }

    @Test
    fun `空输入不算车号`() {
        assertNull(albumCodeOf(""))
        assertNull(albumCodeOf("   "))
    }

    @Test
    fun `章节后缀与内部空格不算车号`() {
        assertNull(albumCodeOf("123456-1"))
        assertNull(albumCodeOf("123 456"))
    }

    @Test
    fun `混入字母或全角数字不算车号`() {
        assertNull(albumCodeOf("12a34"))
        assertNull(albumCodeOf("１２３"))
    }
}
