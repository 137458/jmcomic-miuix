package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadLedger
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadLedgerStore
import com.jm3.core.download.DownloadLedgerWrite
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PageFetcher
import com.jm3.core.download.ResolvedChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 门面接线测试：队列 -> 台账 -> 存储这条线在真实 [DownloadManager] 上确实串起来了。
 *
 * 落盘时机（防抖、步长、降级写）在 :core 的 DownloadLedgerTest 里逐条覆盖；
 * 这里只验证接线没接反、恢复没被吞掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerPersistenceTest {

    /** 内存台账：进度写按 Room 的 UPDATE 语义保留既有页列表。 */
    private class FakeLedgerStore : DownloadLedgerStore {
        val entries = mutableMapOf<String, DownloadLedgerEntry>()

        override suspend fun put(write: DownloadLedgerWrite) {
            val record = write.record
            val pagesJson = when (write) {
                is DownloadLedgerWrite.Progress -> entries[record.photoId]?.pagesJson
                is DownloadLedgerWrite.Completed -> write.pagesJson
            }
            entries[record.photoId] = DownloadLedgerEntry(record = record, pagesJson = pagesJson)
        }

        override suspend fun all(): List<DownloadRecord> = entries.values.map { it.record }

        override suspend fun find(photoId: String): DownloadLedgerEntry? = entries[photoId]

        override suspend fun delete(photoId: String) {
            entries.remove(photoId)
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
    fun 队列跑完后终态记录连同页列表一起落台账() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = FakeLedgerStore()
        val fileStore = FakeDownloadStore()
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ -> ByteArray(0) },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap 1", 3) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            ledger = DownloadLedger(store = store, pagesJsonOf = { "PAGES-of-$it" }),
            appScope = testScope,
        )

        manager.ensureStarted()
        advanceUntilIdle()
        manager.enqueueChapter("p1", "a1", "Chap 1")
        advanceUntilIdle()

        val entry = store.entries.getValue("p1")
        assertEquals(DownloadStatus.DONE, entry.record.status)
        assertEquals(3, entry.record.total)
        assertEquals(3, entry.record.donePages)
        assertEquals("PAGES-of-p1", entry.pagesJson)
        assertEquals("Chap 1", manager.find("p1")?.record?.title)

        queue.shutdown()
        testScope.cancel()
    }

    @Test
    fun 启动恢复把遗留的运行中记录收敛为暂停且保留页列表() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = FakeLedgerStore()
        store.entries["p1"] = DownloadLedgerEntry(
            record = DownloadRecord(
                photoId = "p1",
                albumId = "a1",
                title = "Chap 1",
                total = 3,
                donePages = 2,
                status = DownloadStatus.RUNNING,
                updatedAt = 111L,
            ),
            pagesJson = "PAGES-old",
        )
        val fileStore = FakeDownloadStore()
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ -> ByteArray(0) },
            store = fileStore,
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            ledger = DownloadLedger(store = store),
            appScope = testScope,
        )

        manager.ensureStarted()
        advanceUntilIdle()

        val entry = manager.find("p1")
        assertEquals(DownloadStatus.PAUSED, entry?.record?.status)
        assertEquals(2, entry?.record?.donePages)
        assertEquals("PAGES-old", entry?.pagesJson)

        queue.shutdown()
        testScope.cancel()
    }
}
