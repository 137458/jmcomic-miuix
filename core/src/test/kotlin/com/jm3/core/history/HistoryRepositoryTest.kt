package com.jm3.core.history

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryRepositoryTest {

    private var now = 1000L

    private fun repo(): HistoryRepository = InMemoryHistoryRepository(clock = { now })

    private fun entry(id: String, albumId: String = "a$id") =
        HistoryEntry(photoId = id, albumId = albumId, title = "T$id", coverUrl = null, updatedAt = now)

    @Test
    fun 插入_按更新时间倒序返回() = runTest {
        val r = repo()
        now = 1L; r.upsert(entry("p1"))
        now = 2L; r.upsert(entry("p2"))
        now = 3L; r.upsert(entry("p3"))
        assertEquals(listOf("p3", "p2", "p1"), r.page(1, 10).map { it.photoId })
    }

    @Test
    fun 重复插入_同章节去重并置顶() = runTest {
        val r = repo()
        now = 1L; r.upsert(entry("p1"))
        now = 2L; r.upsert(entry("p2"))
        now = 3L; r.upsert(entry("p1", albumId = "a1"))
        val page = r.page(1, 10)
        assertEquals(listOf("p1", "p2"), page.map { it.photoId })
        assertEquals(3L, page[0].updatedAt)
    }

    @Test
    fun 分页_按页切割_越界返回空() = runTest {
        val r = repo()
        for (i in 1..5) {
            now = i.toLong()
            r.upsert(entry("p$i"))
        }
        assertEquals(listOf("p5", "p4", "p3"), r.page(1, 3).map { it.photoId })
        assertEquals(listOf("p2", "p1"), r.page(2, 3).map { it.photoId })
        assertTrue(r.page(3, 3).isEmpty())
        assertTrue(r.page(0, 3).isEmpty())
    }
}
