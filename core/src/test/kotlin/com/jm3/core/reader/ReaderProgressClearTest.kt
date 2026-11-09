package com.jm3.core.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 进度键清理判定：读到末页才清键，未读完或总页数无效时必须保留进度。
 */
class ReaderProgressClearTest {

    @Test
    fun 读到末页时清键() {
        assertTrue(shouldClearReaderProgress(ReaderPageProgress(page = 20, total = 20)))
    }

    @Test
    fun 单页章节读到唯一一页也清键() {
        assertTrue(shouldClearReaderProgress(ReaderPageProgress(page = 1, total = 1)))
    }

    @Test
    fun 未读到末页时保留进度() {
        assertFalse(shouldClearReaderProgress(ReaderPageProgress(page = 5, total = 20)))
        assertFalse(shouldClearReaderProgress(ReaderPageProgress(page = 19, total = 20)))
    }

    @Test
    fun 总页数为零时没有末页按未读完处理() {
        assertFalse(shouldClearReaderProgress(ReaderPageProgress(page = 0, total = 0)))
        assertFalse(shouldClearReaderProgress(ReaderPageProgress(page = 1, total = 0)))
    }

    @Test
    fun 页码越过总页数时按末页处理() {
        assertTrue(shouldClearReaderProgress(ReaderPageProgress(page = 25, total = 20)))
    }

    @Test
    fun 退出补记的末页进度触发清键() {
        val progress = readerProgressOnExit(restored = true, page = 33, total = 20)
        assertTrue(progress != null && shouldClearReaderProgress(progress))
    }

    @Test
    fun 退出补记的中间页进度保留写键() {
        val progress = readerProgressOnExit(restored = true, page = 5, total = 20)
        assertFalse(progress != null && shouldClearReaderProgress(progress))
    }
}
