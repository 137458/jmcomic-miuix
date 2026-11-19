package com.jm3.app.miuix.data.history

import com.jm3.core.download.DownloadLedgerWrite
import com.jm3.core.history.HistoryCursor
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class HistoryStoreTest {

    private class FakeDownloadDao : DownloadDao {
        val rows = linkedMapOf<String, DownloadEntity>()
        var upsertProgressCalls = 0
        var upsertCalls = 0

        override suspend fun upsert(entry: DownloadEntity) {
            upsertCalls++
            rows[entry.photoId] = entry
        }

        override suspend fun all(): List<DownloadEntity> =
            rows.values.sortedByDescending { it.updatedAt }

        override suspend fun find(photoId: String): DownloadEntity? = rows[photoId]

        override suspend fun updateProgress(
            photoId: String,
            albumId: String,
            title: String,
            total: Int,
            donePages: Int,
            status: String,
            updatedAt: Long,
        ): Int {
            val existing = rows[photoId] ?: return 0
            rows[photoId] = existing.copy(
                albumId = albumId,
                title = title,
                total = total,
                donePages = donePages,
                status = status,
                updatedAt = updatedAt,
            )
            return 1
        }

        override suspend fun insertIfAbsent(entry: DownloadEntity): Long {
            if (entry.photoId in rows) return -1L
            rows[entry.photoId] = entry
            return 1L
        }

        override suspend fun upsertProgress(entry: DownloadEntity) {
            upsertProgressCalls++
            super.upsertProgress(entry)
        }

        override suspend fun delete(photoId: String) {
            rows.remove(photoId)
        }
    }

    private class FakeHistoryDao : HistoryDao {
        val rows = linkedMapOf<String, HistoryEntity>()
        var lastOffset: Int? = null
        var lastAfter: Pair<Long, String>? = null

        override suspend fun upsert(entry: HistoryEntity) {
            rows[entry.photoId] = entry
        }

        override suspend fun page(pageSize: Int, offset: Int): List<HistoryEntity> {
            lastOffset = offset
            return ordered().drop(offset).take(pageSize)
        }

        override suspend fun firstPage(pageSize: Int): List<HistoryEntity> = ordered().take(pageSize)

        override suspend fun pageAfter(beforeUpdatedAt: Long, beforePhotoId: String, pageSize: Int): List<HistoryEntity> {
            lastAfter = beforeUpdatedAt to beforePhotoId
            return ordered()
                .filter { it.updatedAt < beforeUpdatedAt || (it.updatedAt == beforeUpdatedAt && it.photoId > beforePhotoId) }
                .take(pageSize)
        }

        override suspend fun latestForAlbum(albumId: String): HistoryEntity? =
            ordered().firstOrNull { it.albumId == albumId }

        override suspend fun photoIdsForAlbum(albumId: String): List<String> =
            ordered().filter { it.albumId == albumId }.map { it.photoId }

        override suspend fun delete(photoId: String) {
            rows.remove(photoId)
        }

        override suspend fun deleteAlbum(albumId: String) {
            rows.values.removeAll { it.albumId == albumId }
        }

        override suspend fun clear() {
            rows.clear()
        }

        private fun ordered(): List<HistoryEntity> =
            rows.values.sortedWith(compareByDescending<HistoryEntity> { it.updatedAt }.thenBy { it.photoId })
    }

    @Test
    fun `续页走游标查询而不是OFFSET`() = runTest {
        val dao = FakeHistoryDao()
        for (i in 1..5) {
            dao.upsert(HistoryEntity("p$i", "a1", "T$i", null, updatedAt = i.toLong()))
        }
        val repo = RoomHistoryRepository(dao)

        val firstPage = repo.pageAfter(cursor = null, pageSize = 2)
        assertEquals(listOf("p5", "p4"), firstPage.map { it.photoId })
        assertEquals(null, dao.lastOffset, "首页不该走 OFFSET 查询")

        // 列表上方新增一条：续页仍必须从游标之后开始
        dao.upsert(HistoryEntity("p6", "a1", "T6", null, updatedAt = 6L))
        val cursor = HistoryCursor(updatedAt = firstPage.last().updatedAt, photoId = firstPage.last().photoId)
        val secondPage = repo.pageAfter(cursor = cursor, pageSize = 2)

        assertEquals(listOf("p3", "p2"), secondPage.map { it.photoId })
        assertEquals(4L to "p4", dao.lastAfter)
        assertEquals(null, dao.lastOffset, "续页不得退化成 OFFSET 查询")
    }

    @Test
    fun `进度写走Dao原子upsertProgress且保留既有pagesJson`() = runTest {
        val dao = FakeDownloadDao()
        val store = RoomDownloadLedgerStore(dao)

        // 先完成写入带页列表的记录
        store.put(
            DownloadLedgerWrite.Completed(
                record = DownloadRecord("p1", "a1", "Chap 1", 5, 5, DownloadStatus.DONE, 100L),
                pagesJson = """["00001.jpg"]""",
            ),
        )
        assertEquals(1, dao.upsertCalls)

        // 再写进度（例如重下/状态更新）：必须走 upsertProgress 且不得抹掉 pagesJson
        store.put(
            DownloadLedgerWrite.Progress(
                record = DownloadRecord("p1", "a1", "Chap 1", 5, 2, DownloadStatus.RUNNING, 200L),
            ),
        )
        assertEquals(1, dao.upsertProgressCalls)
        assertEquals("""["00001.jpg"]""", store.find("p1")?.pagesJson)
        assertEquals(2, store.find("p1")?.record?.donePages)

        // 首见任务直接走 upsertProgress 补插
        store.put(
            DownloadLedgerWrite.Progress(
                record = DownloadRecord("p2", "a1", "Chap 2", 3, 0, DownloadStatus.QUEUED, 300L),
            ),
        )
        assertEquals(2, dao.upsertProgressCalls)
        assertEquals(DownloadStatus.QUEUED, store.find("p2")?.record?.status)
    }
}
