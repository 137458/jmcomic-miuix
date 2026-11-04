package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** 记录写入的内存台账：进度写照 Room 的 UPDATE 语义处理——只改元数据列，页列表原样留着。 */
private class RecordingLedgerStore(
    var failNextPut: Boolean = false,
) : DownloadLedgerStore {

    val writes = mutableListOf<DownloadLedgerWrite>()
    val entries = LinkedHashMap<String, DownloadLedgerEntry>()

    override suspend fun put(write: DownloadLedgerWrite) {
        if (failNextPut) {
            failNextPut = false
            throw IllegalStateException("磁盘写入失败")
        }
        writes += write
        val record = write.record
        when (write) {
            is DownloadLedgerWrite.Completed ->
                entries[record.photoId] = DownloadLedgerEntry(record, write.pagesJson)

            is DownloadLedgerWrite.Progress ->
                entries[record.photoId] = DownloadLedgerEntry(record, entries[record.photoId]?.pagesJson)
        }
    }

    override suspend fun all(): List<DownloadRecord> =
        entries.values.map { it.record }.sortedByDescending { it.updatedAt }

    override suspend fun find(photoId: String): DownloadLedgerEntry? = entries[photoId]

    override suspend fun delete(photoId: String) {
        entries.remove(photoId)
    }
}

private fun task(
    photoId: String = "p1",
    total: Int = 100,
    index: Int = 0,
    status: DownloadStatus = QUEUED,
) = DownloadTask(photoId = photoId, albumId = "a1", title = "t1", total = total, index = index, status = status)

private fun record(
    photoId: String = "p1",
    total: Int = 100,
    donePages: Int = 0,
    status: DownloadStatus = RUNNING,
    updatedAt: Long = 100L,
) = DownloadRecord(
    photoId = photoId,
    albumId = "a1",
    title = "t1",
    total = total,
    donePages = donePages,
    status = status,
    updatedAt = updatedAt,
)

class DownloadLedgerTest {

    private var now = 1000L

    private fun ledger(
        store: DownloadLedgerStore,
        pagesJsonOf: (String) -> String? = { "PAGES" },
    ) = DownloadLedger(store = store, pagesJsonOf = pagesJsonOf, clock = { now })

    @Test
    fun `首次出现的任务立即落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        val queued = task(status = QUEUED)

        val persisted = ledger.record(listOf(queued), nowMs = 1000L)

        assertEquals(listOf(queued), persisted)
        assertEquals(1, store.writes.size)
        val write = store.writes.single()
        assertTrue(write is DownloadLedgerWrite.Progress, "未完成的任务只能是进度写，实际 $write")
        assertEquals(0, write.record.donePages)
        assertEquals(1000L, write.record.updatedAt)
    }

    @Test
    fun `状态转移立即落盘且带上终态页码`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        ledger.record(listOf(task(index = 98, status = RUNNING)), nowMs = 1000L)
        now = 2000L

        // 距上次只过了 50ms、页码只推了 2 页，但状态转 DONE 必须立刻写
        val persisted = ledger.record(listOf(task(index = 100, status = DONE)), nowMs = 1050L)

        assertEquals(1, persisted.size)
        val write = store.writes.last()
        assertTrue(write is DownloadLedgerWrite.Completed, "完成写应带页列表，实际 $write")
        assertEquals(100, write.record.donePages)
        assertEquals(DONE, write.record.status)
        assertEquals(2000L, write.record.updatedAt)
    }

    @Test
    fun `阈值内的页码推进不落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        ledger.record(listOf(task(index = 0, status = RUNNING)), nowMs = 1000L)

        // 100ms、2 页：时间和步长都没到
        val persisted = ledger.record(listOf(task(index = 2, status = RUNNING)), nowMs = 1100L)

        assertTrue(persisted.isEmpty())
        assertEquals(1, store.writes.size)
    }

    @Test
    fun `页码推进达到步长阈值时落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        ledger.record(listOf(task(index = 0, status = RUNNING)), nowMs = 1000L)

        val persisted = ledger.record(listOf(task(index = 5, status = RUNNING)), nowMs = 1200L)

        assertEquals(1, persisted.size)
        assertEquals(5, store.writes.last().record.donePages)
    }

    @Test
    fun `页码推进达到时间窗口时落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        ledger.record(listOf(task(index = 0, status = RUNNING)), nowMs = 1000L)

        val persisted = ledger.record(listOf(task(index = 1, status = RUNNING)), nowMs = 2500L)

        assertEquals(1, persisted.size)
        assertEquals(1, store.writes.last().record.donePages)
    }

    @Test
    fun `无变化的静态任务不重复落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        val running = task(index = 10, status = RUNNING)
        ledger.record(listOf(running), nowMs = 1000L)

        val persisted = ledger.record(listOf(running), nowMs = 7000L)

        assertTrue(persisted.isEmpty())
        assertEquals(1, store.writes.size)
    }

    @Test
    fun `一批任务里只落盘实质变化的那个`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        val first = task(photoId = "p1", index = 0, status = RUNNING)
        val second = task(photoId = "p2", total = 80, status = QUEUED)
        ledger.record(listOf(first, second), nowMs = 1000L)

        val persisted = ledger.record(
            listOf(first.copy(index = 1), second.copy(status = RUNNING)),
            nowMs = 1100L,
        )

        assertEquals(listOf(second.copy(status = RUNNING)), persisted)
        assertEquals(3, store.writes.size)
    }

    @Test
    fun `完成写带离线页列表`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store, pagesJsonOf = { "PAGES-of-$it" })

        ledger.record(listOf(task(index = 3, total = 3, status = DONE)), nowMs = 1000L)

        val write = store.writes.single()
        assertEquals(DownloadLedgerWrite.Completed(write.record, "PAGES-of-p1"), write)
        assertEquals("PAGES-of-p1", store.entries.getValue("p1").pagesJson)
    }

    @Test
    fun `已完成但取不到页列表时降级为进度写且不抹掉已存页列表`() = runTest {
        val store = RecordingLedgerStore()
        // 上次进程留下的记录：页列表已缓存
        store.entries["p1"] = DownloadLedgerEntry(record(donePages = 98, status = PAUSED), "PAGES")
        val ledger = ledger(store, pagesJsonOf = { null })

        ledger.record(listOf(task(index = 98, status = PAUSED)), nowMs = 1000L)
        ledger.record(listOf(task(index = 100, status = DONE)), nowMs = 1050L)

        assertTrue(store.writes.all { it is DownloadLedgerWrite.Progress }, "取不到页列表时只能是进度写")
        assertEquals("PAGES", store.entries.getValue("p1").pagesJson)
        assertEquals(DONE, store.entries.getValue("p1").record.status)
        assertEquals(100, store.entries.getValue("p1").record.donePages)
    }

    @Test
    fun `任务消失后快照被清理，同 photoId 再入队按新任务落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store)
        val queued = task(status = QUEUED)
        ledger.record(listOf(queued), nowMs = 1000L)

        ledger.record(emptyList(), nowMs = 1050L)
        val persisted = ledger.record(listOf(queued), nowMs = 1100L)

        assertEquals(1, persisted.size)
        assertEquals(2, store.writes.size)
    }

    @Test
    fun `单条落库失败不影响同批其它任务与后续重试`() = runTest {
        val store = RecordingLedgerStore(failNextPut = true)
        val ledger = ledger(store)

        val persisted = ledger.record(
            listOf(task(photoId = "p1", status = QUEUED), task(photoId = "p2", status = QUEUED)),
            nowMs = 1000L,
        )

        assertEquals(listOf(task(photoId = "p2", status = QUEUED)), persisted)
        assertEquals(listOf("p2"), store.entries.keys.toList())
        // 失败那条不记快照：下一次更新立刻重试，而不是等到时间窗口
        val retried = ledger.record(listOf(task(photoId = "p1", status = QUEUED)), nowMs = 1000L)
        assertEquals(1, retried.size)
        assertEquals(listOf("p2", "p1"), store.entries.keys.toList())
    }

    @Test
    fun `启动恢复把中断记录收敛为暂停并保留页列表`() = runTest {
        val store = RecordingLedgerStore()
        store.entries["running"] = DownloadLedgerEntry(record("running", donePages = 2, status = RUNNING), "PAGES-running")
        store.entries["done"] = DownloadLedgerEntry(record("done", donePages = 5, status = DONE), "PAGES-done")
        val ledger = ledger(store, pagesJsonOf = { null })

        val converged = ledger.recoverInterruptedRecords()

        assertEquals(1, converged)
        assertEquals(PAUSED, store.entries.getValue("running").record.status)
        assertEquals("PAGES-running", store.entries.getValue("running").pagesJson)
        assertEquals(DONE, store.entries.getValue("done").record.status)
        // 已经收敛过再跑一次不写库（可安全重入）
        assertEquals(0, ledger.recoverInterruptedRecords())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `订阅队列更新后终态记录连同页列表一起落盘`() = runTest {
        val store = RecordingLedgerStore()
        val ledger = ledger(store, pagesJsonOf = { "PAGES-of-$it" })
        val storePages = object : DownloadStore {
            override fun exists(photoId: String, index: Int) = false
            override fun write(photoId: String, index: Int, bytes: ByteArray) {}
            override fun delete(photoId: String) {}
            override fun files(photoId: String) = emptyList<Int>()
            override fun read(photoId: String, index: Int): ByteArray? = null
        }
        val queue = DownloadQueue(
            fetcher = PageFetcher { _, _ -> ByteArray(0) },
            store = storePages,
        )
        // 单独一个 Job 承载订阅与 worker：test 调度器照常推进，测试结束前显式取消，
        // 既不泄漏给 runTest 收尾检查，也不依赖 backgroundScope 的推进时机。
        val scope = CoroutineScope(coroutineContext + Job())
        ledger.start(scope, queue.updates)
        queue.start(scope)

        queue.enqueue(DownloadTask(photoId = "p1", albumId = "a1", title = "t1", total = 3))
        advanceUntilIdle()

        val entry = store.entries.getValue("p1")
        assertEquals(DONE, entry.record.status)
        assertEquals(3, entry.record.donePages)
        assertEquals("PAGES-of-p1", entry.pagesJson)
        queue.shutdown()
        scope.cancel()
        advanceUntilIdle()
    }
}
