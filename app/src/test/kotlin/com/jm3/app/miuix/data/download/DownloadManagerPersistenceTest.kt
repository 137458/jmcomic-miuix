package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.ResolvedChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerPersistenceTest {

    private class FakeRecordStore : DownloadRecordStore {
        val upsertCalls = mutableListOf<DownloadRecord>()
        val records = mutableMapOf<String, DownloadRecord>()

        override suspend fun upsert(record: DownloadRecord, pagesJson: String?) {
            upsertCalls.add(record)
            records[record.photoId] = record
        }

        override suspend fun updateStatus(photoId: String, status: DownloadStatus) {
            records[photoId]?.let { records[photoId] = it.copy(status = status) }
        }

        override suspend fun all(): List<DownloadRecord> = records.values.toList()

        override suspend fun find(photoId: String): DownloadRecordWithPages? =
            records[photoId]?.let { DownloadRecordWithPages(it, null) }

        override suspend fun delete(photoId: String) {
            records.remove(photoId)
        }
    }

    private class FakeDownloadStore : DownloadStore {
        override fun exists(photoId: String, index: Int): Boolean = false
        override fun write(photoId: String, index: Int, bytes: ByteArray) {}
        override fun delete(photoId: String) {}
        override fun files(photoId: String): List<Int> = emptyList()
        override fun read(photoId: String, index: Int): ByteArray? = null
    }

    @Test
    fun 下载管理器持久化只在关键状态和步长满足时触发写库() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val recordStore = FakeRecordStore()
        val fileStore = FakeDownloadStore()

        val queue = DownloadQueue(
            fetcher = { _, _ -> ByteArray(0) },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap 1", 10) },
            maxConcurrent = 1,
        )
        val deltaTracker = DownloadRecordDeltaTracker(progressIntervalMs = 60_000L, progressStep = 5)
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            recordStore = recordStore,
            pageSource = GatewayPageFetcher { error("not used") },
            appScope = testScope,
            deltaTracker = deltaTracker,
        )

        manager.ensureStarted()
        advanceUntilIdle()

        manager.enqueueChapter("p1", "a1", "Chap 1")
        advanceUntilIdle()

        queue.shutdown()
        advanceUntilIdle()

        // 验证关键落盘触发且终态完整
        assertTrue(recordStore.upsertCalls.isNotEmpty(), "任务入队并执行完毕应产生关键落盘调用")
        val finalCall = recordStore.upsertCalls.last()
        assertEquals("p1", finalCall.photoId)
        assertEquals(DownloadStatus.DONE, finalCall.status)
        assertEquals(10, finalCall.total)
        assertEquals(10, finalCall.donePages)

        // 验证写入次数被节流控制（10页下载仅产生 2..5 次写库，大幅低于逐页落盘的 12+ 次）
        assertTrue(recordStore.upsertCalls.size in 2..5, "实际持久化次数应被防抖收敛至 2..5 次，实际: ${recordStore.upsertCalls.size}")

        testScope.cancel()
    }
}
