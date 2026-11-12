package com.jm3.app.miuix.data.history

import com.jm3.core.download.DownloadLedgerWrite
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
