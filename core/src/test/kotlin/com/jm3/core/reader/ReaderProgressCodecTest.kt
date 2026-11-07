package com.jm3.core.reader

import com.jm3.core.settings.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 阅读进度的编解码契约：键里存的是 "page:total"，读到非法内容必须当作没有进度，
 * 不能把损坏的数据还原成某一页。
 */
class ReaderProgressCodecTest {

    @Test
    fun 正常进度编码为页码冒号总页数() {
        assertEquals("3:20", encodeReaderProgress(ReaderPageProgress(page = 3, total = 20)))
    }

    @Test
    fun 编码解码往返保持页码与总页数() {
        val progress = ReaderPageProgress(page = 7, total = 42)
        assertEquals(progress, decodeReaderProgress(encodeReaderProgress(progress)))
    }

    @Test
    fun 第二组往返数据同样成立() {
        val progress = ReaderPageProgress(page = 1, total = 1)
        assertEquals(progress, decodeReaderProgress(encodeReaderProgress(progress)))
    }

    @Test
    fun 解码缺少分隔符返回空() {
        assertNull(decodeReaderProgress("320"))
    }

    @Test
    fun 解码非数字返回空() {
        assertNull(decodeReaderProgress("abc:20"))
    }

    @Test
    fun 解码多余分隔符返回空() {
        assertNull(decodeReaderProgress("3:20:1"))
    }

    @Test
    fun 解码空白返回空() {
        assertNull(decodeReaderProgress("   "))
    }

    @Test
    fun 解码空串返回空() {
        assertNull(decodeReaderProgress(""))
    }

    @Test
    fun 解码空值返回空() {
        assertNull(decodeReaderProgress(null))
    }

    @Test
    fun 解码零页返回空() {
        assertNull(decodeReaderProgress("0:20"))
    }

    @Test
    fun 解码零总页数返回空() {
        assertNull(decodeReaderProgress("3:0"))
    }

    @Test
    fun 解码负页返回空() {
        assertNull(decodeReaderProgress("-1:20"))
    }

    @Test
    fun 解码负总页数返回空() {
        assertNull(decodeReaderProgress("3:-20"))
    }

    /**
     * 设置层接缝：进度按漫画 id 各自成键，写进去再读出来必须是同一份进度，
     * 没写过的漫画读出来必须是空而不是别的漫画的进度。
     */
    @Test
    fun 设置层按漫画分别记住进度() = runTest {
        val s = InMemorySettingsRepository()
        s.setReaderLastPage("photo-1", ReaderPageProgress(page = 5, total = 20))
        s.setReaderLastPage("photo-2", ReaderPageProgress(page = 2, total = 10))
        assertEquals(ReaderPageProgress(page = 5, total = 20), s.readerLastPage("photo-1"))
        assertEquals(ReaderPageProgress(page = 2, total = 10), s.readerLastPage("photo-2"))
        assertNull(s.readerLastPage("photo-3"))
    }

    @Test
    fun 退出补记_恢复未完成时不写进度() {
        assertNull(readerProgressOnExit(restored = false, page = 1, total = 20))
    }

    @Test
    fun 退出补记_恢复完成后按当前页写进度() {
        assertEquals(ReaderPageProgress(page = 5, total = 20), readerProgressOnExit(true, page = 5, total = 20))
    }

    @Test
    fun 退出补记_页码越界时夹到总页数() {
        assertEquals(ReaderPageProgress(page = 20, total = 20), readerProgressOnExit(true, page = 33, total = 20))
    }

    @Test
    fun 退出补记_空章节不写进度() {
        assertNull(readerProgressOnExit(true, page = 1, total = 0))
    }

    @Test
    fun 退出补记_非法页码不写进度() {
        assertNull(readerProgressOnExit(true, page = 0, total = 20))
    }

    @Test
    fun 历史总页数为零时视为损坏数据不恢复() = runTest {
        assertNull(resolveRestoredPage(ReaderPageProgress(page = 5, total = 0), totalPages = 20))
    }

    @Test
    fun 历史总页数为负时视为损坏数据不恢复() = runTest {
        assertNull(resolveRestoredPage(ReaderPageProgress(page = 5, total = -1), totalPages = 20))
    }

    @Test
    fun 章节页数变化时按当前总页数夹取且仍保留进度() = runTest {
        assertEquals(8, resolveRestoredPage(ReaderPageProgress(page = 15, total = 20), totalPages = 8))
        assertEquals(15, resolveRestoredPage(ReaderPageProgress(page = 15, total = 15), totalPages = 30))
        assertEquals(5, resolveRestoredPage(ReaderPageProgress(page = 5, total = 20), totalPages = 20))
    }

    @Test
    fun 首载起始页_有效时优先于历史进度() {
        // 详情页预览点第 4 张：即使历史进度是第 9 页，也要先到第 4 页
        assertEquals(
            4,
            resolveInitialPage(
                startPage = 4,
                saved = ReaderPageProgress(page = 9, total = 20),
                totalPages = 20,
            ),
        )
    }

    @Test
    fun 首载起始页_越界时回退历史进度() {
        assertEquals(
            9,
            resolveInitialPage(
                startPage = 25,
                saved = ReaderPageProgress(page = 9, total = 20),
                totalPages = 20,
            ),
        )
        assertEquals(
            9,
            resolveInitialPage(
                startPage = 0,
                saved = ReaderPageProgress(page = 9, total = 20),
                totalPages = 20,
            ),
        )
    }

    @Test
    fun 首载起始页_无起始页时回退历史进度() {
        assertEquals(
            9,
            resolveInitialPage(
                startPage = null,
                saved = ReaderPageProgress(page = 9, total = 20),
                totalPages = 20,
            ),
        )
    }

    @Test
    fun 首载起始页_空章节没有可恢复页() {
        assertNull(resolveInitialPage(startPage = 3, saved = null, totalPages = 0))
        assertNull(resolveInitialPage(startPage = 1, saved = null, totalPages = 0))
    }
}
