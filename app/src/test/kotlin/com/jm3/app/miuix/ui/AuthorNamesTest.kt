package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorNamesTest {
    @Test
    fun `多个作者用同一分隔符拼接`() {
        assertEquals("甲 / 乙", listOf("甲", "乙").authorNames("未知作者"))
    }

    @Test
    fun `空列表按调用方选择兜底`() {
        assertEquals("未知作者", emptyList<String>().authorNames("未知作者"))
        assertNull(emptyList<String>().authorNames())
    }

    @Test
    fun `全空白作者不拼出空串`() {
        assertEquals("未知作者", listOf(" ", "").authorNames("未知作者"))
    }
}
