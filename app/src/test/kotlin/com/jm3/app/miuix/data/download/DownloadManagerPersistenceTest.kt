package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PageFetcher
import com.jm3.core.download.ResolvedChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerPersistenceTest {

    private class FakeRecordStore(
        private var failNextUpsert: Boolean = false,
    ) : DownloadRecordStore {
        val upsertCalls = mutableListOf<DownloadRecord>()
        val records = mutableMapOf<String, DownloadRecord>()

        override suspend fun upsert(record: DownloadRecord, pagesJson: String?) {
            if (failNextUpsert) {
                failNextUpsert = false
                throw IllegalStateException("磁盘写入失败")
            }
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

    /**
     * 逐页放行的取数门控。
     *
     * 队列每下完一页就更新一次进度，但 StateFlow 会把这些连发的值合并成一个「最新值」，
     * 测试里一口气跑完就只能看到终态，防抖逻辑等于没被测。让取数停在每页边界上，
     * 测试放行一页再 drain 一次调度，进度事件才是逐个可观测的。
     */
    private class PageGate {
        private val permits = Channel<Unit>(Channel.UNLIMITED)

        val fetcher = PageFetcher { _, _ ->
            permits.receive()
            ByteArray(0)
        }

        suspend fun releaseOnePage() = permits.send(Unit)
    }

    @Test
    fun 下载管理器持久化只在关键状态和步长满足时触发写库() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val recordStore = FakeRecordStore()
        val fileStore = FakeDownloadStore()
        val pageGate = PageGate()

        val queue = DownloadQueue(
            fetcher = pageGate.fetcher,
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

        // 逐页放行：放一页、drain 一次，保证每页的进度事件都被订阅看到，而不是被合并掉
        repeat(10) {
            pageGate.releaseOnePage()
            advanceUntilIdle()
        }

        queue.shutdown()
        advanceUntilIdle()

        // 验证关键落盘触发且终态完整
        assertTrue(recordStore.upsertCalls.isNotEmpty(), "任务入队并执行完毕应产生关键落盘调用")
        val finalCall = recordStore.upsertCalls.last()
        assertEquals("p1", finalCall.photoId)
        assertEquals(DownloadStatus.DONE, finalCall.status)
        assertEquals(10, finalCall.total)
        assertEquals(10, finalCall.donePages)

        val persistedPages = recordStore.upsertCalls.map { it.donePages }
        // 步长触发：页码推进满 5 页必须落一次盘，否则续传只能退回更早的落盘点
        assertTrue(5 in persistedPages, "页码推进到第 5 页应落盘一次，实际落盘页码: $persistedPages")
        // 时间窗口（60s）在这条用例里永远不满足，所以页码推进只允许落在步长刻度上：
        // 逐页落盘正是这次要消掉的写放大
        val middlePages = persistedPages.filter { it in 1..9 && it != 5 }
        assertTrue(middlePages.isEmpty(), "步长与时间窗口都不满足的中间页不应落盘，实际多写了: $middlePages")

        testScope.cancel()
    }

    @Test
    fun `单次落库失败不会打死进度订阅，后续进度照常持久化`() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val recordStore = FakeRecordStore(failNextUpsert = true)
        val fileStore = FakeDownloadStore()
        val pageGate = PageGate()

        val queue = DownloadQueue(
            fetcher = pageGate.fetcher,
            store = fileStore,
            resolver = { ResolvedChapter("a1", "Chap 1", 10) },
            maxConcurrent = 1,
        )
        val manager = DownloadManager(
            queue = queue,
            fileStore = fileStore,
            recordStore = recordStore,
            pageSource = GatewayPageFetcher { error("not used") },
            appScope = testScope,
        )

        manager.ensureStarted()
        advanceUntilIdle()
        manager.enqueueChapter("p1", "a1", "Chap 1")
        advanceUntilIdle()
        repeat(10) {
            pageGate.releaseOnePage()
            advanceUntilIdle()
        }
        queue.shutdown()
        advanceUntilIdle()

        // 第一次落库抛错被吞掉之后，订阅还得活着：终态必须落进去，否则用户看到的是永远下不完的任务
        assertEquals(DownloadStatus.DONE, recordStore.records["p1"]?.status)
        assertTrue(recordStore.upsertCalls.size >= 2, "失败后仍应继续落盘，实际: ${recordStore.upsertCalls.size}")

        testScope.cancel()
    }
}
