package com.jm3.core.reader

import com.jm3.core.settings.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 进度落盘规则：读完末页清键，没读完写键，且只碰当前作品的那一个键。
 */
class ReaderProgressPersistTest {

    @Test
    fun 读到末页时清掉进度键() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setReaderLastPage("p1", ReaderPageProgress(page = 3, total = 6))
        persistReaderProgress(repo, "p1", ReaderPageProgress(page = 6, total = 6))
        assertNull(repo.readerLastPage("p1"))
    }

    @Test
    fun 未读完时写入当前进度() = runTest {
        val repo = InMemorySettingsRepository()
        persistReaderProgress(repo, "p1", ReaderPageProgress(page = 2, total = 9))
        assertEquals(ReaderPageProgress(page = 2, total = 9), repo.readerLastPage("p1"))
    }

    @Test
    fun 写入时覆盖旧进度() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setReaderLastPage("p1", ReaderPageProgress(page = 1, total = 9))
        persistReaderProgress(repo, "p1", ReaderPageProgress(page = 4, total = 9))
        assertEquals(ReaderPageProgress(page = 4, total = 9), repo.readerLastPage("p1"))
    }

    @Test
    fun 清键只影响当前作品() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setReaderLastPage("other", ReaderPageProgress(page = 1, total = 4))
        persistReaderProgress(repo, "p1", ReaderPageProgress(page = 4, total = 4))
        assertEquals(ReaderPageProgress(page = 1, total = 4), repo.readerLastPage("other"))
    }

    @Test
    fun 页码超过总页数同样按读完处理() = runTest {
        // 末页之后继续翻、或本地页数与实际不一致时，page 可能大于 total
        val repo = InMemorySettingsRepository()
        repo.setReaderLastPage("p1", ReaderPageProgress(page = 2, total = 5))
        persistReaderProgress(repo, "p1", ReaderPageProgress(page = 7, total = 5))
        assertNull(repo.readerLastPage("p1"))
    }
}
