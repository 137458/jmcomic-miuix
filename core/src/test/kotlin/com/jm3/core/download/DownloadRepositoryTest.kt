package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DownloadRepositoryTest {

    @Test
    fun `upsert overwrites by photoId and sorts by updatedAt desc`() = runTest {
        var now = 1000L
        val repo = InMemoryDownloadRepository(clock = { now++ })
        repo.upsert(DownloadRecord("1", "a1", "t1", total = 10, donePages = 1, status = RUNNING, updatedAt = 0))
        repo.upsert(DownloadRecord("2", "a2", "t2", total = 5, donePages = 5, status = DONE, updatedAt = 0))
        // 同 photoId 覆盖：刷新 updatedAt 与状态
        repo.upsert(DownloadRecord("1", "a1", "t1", total = 10, donePages = 10, status = DONE, updatedAt = 0))
        val all = repo.all()
        assertEquals(listOf("1", "2"), all.map { it.photoId }) // 1 后写故时间更大
        assertEquals(DONE, all[0].status)
        assertEquals(10, all[0].donePages)
    }

    @Test
    fun `delete removes record`() = runTest {
        val repo = InMemoryDownloadRepository()
        repo.upsert(DownloadRecord("1", "a1", "t1", total = 1, donePages = 0, status = DONE, updatedAt = 0))
        repo.delete("1")
        assertTrue(repo.all().isEmpty())
        // 删除不存在的 photoId 不抛异常
        repo.delete("404")
    }
}
