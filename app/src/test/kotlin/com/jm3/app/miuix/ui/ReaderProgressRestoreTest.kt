package com.jm3.app.miuix.ui

import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.resolveRestoredPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 恢复页码的判定：只有存在历史进度且页码落在当前总页数内才恢复，
 * 越界则夹到最后一页，总页数为 0（章节无图）时不恢复。
 */
class ReaderProgressRestoreTest {

    @Test
    fun 没有历史进度时不恢复() {
        assertNull(resolveRestoredPage(saved = null, totalPages = 20))
    }

    @Test
    fun 历史页码在范围内时原样恢复() {
        assertEquals(5, resolveRestoredPage(ReaderPageProgress(page = 5, total = 20), totalPages = 20))
    }

    @Test
    fun 历史页码超出总页数时夹到最后一页() {
        assertEquals(20, resolveRestoredPage(ReaderPageProgress(page = 33, total = 20), totalPages = 20))
    }

    @Test
    fun 历史页码超出总页数且总页数更小时夹到实际最后一页() {
        assertEquals(8, resolveRestoredPage(ReaderPageProgress(page = 33, total = 20), totalPages = 8))
    }

    @Test
    fun 总页数为零时不恢复() {
        assertNull(resolveRestoredPage(ReaderPageProgress(page = 5, total = 20), totalPages = 0))
    }

    @Test
    fun 历史页码非法时不恢复() {
        assertNull(resolveRestoredPage(ReaderPageProgress(page = 0, total = 20), totalPages = 20))
    }
}
