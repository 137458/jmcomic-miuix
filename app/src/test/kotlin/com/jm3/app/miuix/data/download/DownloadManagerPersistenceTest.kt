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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `启动恢复期间新入队任务不会被回写成PAUSED`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val recoveryGate = CompletableDeferred<Unit>()
        val new1Statuses = mutableListOf<DownloadStatus>()
        val store = object : DownloadLedgerStore {
            val entries = mutableMapOf<String, DownloadLedgerEntry>()

            init {
                entries["old"] = DownloadLedgerEntry(
                    record = DownloadRecord("old", "a0", "Old", 2, 1, DownloadStatus.RUNNING, 10L),
                    pagesJson = null,
                )
            }

            override suspend fun put(write: DownloadLedgerWrite) {
                val r = write.record
                if (r.photoId == "new-1") new1Statuses += r.status
                val pages = when (write) {
                    is DownloadLedgerWrite.Progress -> entries[r.photoId]?.pagesJson
                    is DownloadLedgerWrite.Completed -> write.pagesJson
                }
                entries[r.photoId] = DownloadLedgerEntry(r, pages)
            }

            override suspend fun all(): List<DownloadRecord> {
                recoveryGate.await()
                return entries.values.map { it.record }
            }

            override suspend fun find(photoId: String): DownloadLedgerEntry? = entries[photoId]

            override suspend fun delete(photoId: String) {
                entries.remove(photoId)
            }
        }
        val fileStore = FakeDownloadStore()
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ -> ByteArray(1) },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "New", 2) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            ledger = DownloadLedger(store = store, pagesJsonOf = { "PAGES-$it" }),
            appScope = testScope,
        )

        manager.enqueueChapter("new-1", "a1", "New")
        runCurrent()
        // 放行恢复：此前若 ledger.start 早于 recoverInterruptedRecords，new-1 会被旧记录恢复覆盖成 PAUSED
        recoveryGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DownloadStatus.PAUSED, store.entries["old"]?.record?.status)
        assertEquals(DownloadStatus.DONE, store.entries["new-1"]?.record?.status)
        assertTrue(DownloadStatus.PAUSED !in new1Statuses, "新入队任务在恢复阶段不得被写为 PAUSED，实际写入=$new1Statuses")

        queue.shutdown()
        testScope.cancel()
    }

    @Test
    fun `删除进行中任务后在途文件写盘不会留下残留目录`() = runTest {
        val root = java.nio.file.Files.createTempDirectory("jm3-dm-guard").toFile()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val realStore = FileDownloadStore(root)
        lateinit var queue: DownloadQueue
        val guardedStore = QueueGuardedDownloadStore(
            delegate = realStore,
            isActiveInQueue = { id -> id in queue.updates.value },
        )
        val ledgerStore = FakeLedgerStore()
        queue = DownloadQueue(
            fetcher = PageFetcher { _, _ ->
                fetchStarted.complete(Unit)
                releaseFetch.await()
                byteArrayOf(1, 2, 3)
            },
            store = guardedStore,
            resolver = { com.jm3.core.download.ResolvedChapter("a1", "Chap 1", 1) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = realStore,
            ledger = DownloadLedger(store = ledgerStore),
            appScope = testScope,
        )

        try {
            manager.ensureStarted()
            advanceUntilIdle()
            manager.enqueueChapter("p1", "a1", "Chap 1")
            runCurrent()
            fetchStarted.await()

            manager.remove("p1")
            releaseFetch.complete(Unit)
            advanceUntilIdle()

            assertEquals(emptyList(), realStore.files("p1"))
            assertTrue(!java.io.File(root, "p1").exists(), "已删除任务在途取数返回后不得在磁盘重建目录")
            assertNull(ledgerStore.entries["p1"])
        } finally {
            queue.shutdown()
            testScope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun `删除进行中任务后在途台账写入不会复活已删记录`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val putStarted = CompletableDeferred<Unit>()
        val releasePut = CompletableDeferred<Unit>()
        var blockFirstPut = true
        val store = object : DownloadLedgerStore {
            val entries = mutableMapOf<String, DownloadLedgerEntry>()

            override suspend fun put(write: DownloadLedgerWrite) {
                if (blockFirstPut && write.record.photoId == "p1") {
                    blockFirstPut = false
                    putStarted.complete(Unit)
                    releasePut.await()
                }
                val r = write.record
                entries[r.photoId] = DownloadLedgerEntry(r, null)
            }

            override suspend fun all(): List<DownloadRecord> = entries.values.map { it.record }
            override suspend fun find(photoId: String): DownloadLedgerEntry? = entries[photoId]
            override suspend fun delete(photoId: String) {
                entries.remove(photoId)
            }
        }
        val fileStore = FakeDownloadStore()
        val fetchGate = CompletableDeferred<Unit>()
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ ->
                fetchGate.await()
                ByteArray(1)
            },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap 1", 2) },
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
        manager.enqueueChapter("p1", "a1", "Chap 1")
        runCurrent()
        putStarted.await()

        val removeJob = launch { manager.remove("p1") }
        runCurrent()
        releasePut.complete(Unit)
        fetchGate.complete(Unit)
        removeJob.join()
        advanceUntilIdle()

        assertNull(store.entries["p1"], "删除完成后不得被在途台账写入复活")
        assertTrue("p1" !in queue.updates.value)

        queue.shutdown()
        testScope.cancel()
    }

    @Test
    fun `clearAll取消尚未落盘的内存排队任务`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val store = FakeLedgerStore()
        val fileStore = FakeDownloadStore()
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ -> ByteArray(1) },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap 1", 2) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            ledger = DownloadLedger(store = store),
            appScope = testScope,
        )

        // 先入队但尚未 advanceUntilIdle：此时台账为空，任务只在 queue.updates 里
        manager.enqueueChapter("unpersisted", "a1", "Chap 1")
        assertTrue("unpersisted" in queue.updates.value)

        manager.clearAll()
        advanceUntilIdle()

        assertTrue(queue.updates.value.isEmpty(), "clearAll 必须取消尚未落盘的内存任务")
        assertNull(store.entries["unpersisted"])

        queue.shutdown()
        testScope.cancel()
    }

    @Test
    fun `collect在锁外拿到旧快照后遇到remove和clearAll不会复活已删记录且会驱逐页缓存`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val store = FakeLedgerStore()
        val fileStore = FakeDownloadStore()
        val evicted = mutableListOf<String>()
        var clearedCount = 0
        var blockCollect = false
        val collectPaused = CompletableDeferred<Unit>()
        val releaseCollect = CompletableDeferred<Unit>()
        val fetchGate = CompletableDeferred<Unit>()

        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ ->
                fetchGate.await()
                ByteArray(1)
            },
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap", 2) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            ledger = DownloadLedger(store = store),
            appScope = testScope,
            onEvictPages = { evicted += it },
            onClearPages = { clearedCount++ },
            beforeRecordLock = {
                if (blockCollect) {
                    blockCollect = false
                    collectPaused.complete(Unit)
                    releaseCollect.await()
                }
            },
        )

        manager.ensureStarted()
        advanceUntilIdle()

        // 下一轮 collect 在拿到快照后、进入 ledgerLock 前被暂停
        blockCollect = true
        manager.enqueueChapter("p1", "a1", "Chap 1")
        runCurrent()
        collectPaused.await()

        // 趁 collect 带着含 p1 的旧快照等待锁时，完成 remove("p1")
        manager.remove("p1")
        assertEquals(listOf("p1"), evicted)
        assertNull(store.entries["p1"])

        // 放行 collect：锁内重读 queue.updates.value 后不得把已删的 p1 写回台账
        releaseCollect.complete(Unit)
        advanceUntilIdle()
        assertNull(store.entries["p1"], "collect 锁内重读快照后不得复活已删除任务")

        // clearAll 同样触发清空页缓存
        manager.enqueueChapter("p2", "a1", "Chap 2")
        runCurrent()
        manager.clearAll()
        fetchGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, clearedCount)
        assertNull(store.entries["p2"])

        queue.shutdown()
        testScope.cancel()
    }
}
