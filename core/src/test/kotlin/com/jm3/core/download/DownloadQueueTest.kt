package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private fun task(photoId: String, total: Int = 1) =
    DownloadTask(photoId = photoId, albumId = "a$photoId", title = "t$photoId", total = total)

/** 可预置已下载页的内存存储；单线程测试调度器下无需加锁。 */
private class FakeStore(vararg seeded: Pair<String, List<Int>>) : DownloadStore {
    private val files = mutableMapOf<String, MutableSet<Int>>()

    init {
        seeded.forEach { (id, pages) -> files[id] = pages.toMutableSet() }
    }

    val writeLog = mutableListOf<Pair<String, Int>>()

    /** 剩余失败次数：>0 时 write 抛异常且不落盘，用于验证 worker 的任务级异常隔离。 */
    val writeFailures = mutableMapOf<Pair<String, Int>, Int>()

    override fun exists(photoId: String, index: Int): Boolean = files[photoId]?.contains(index) == true

    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        val key = photoId to index
        val left = writeFailures[key] ?: 0
        if (left > 0) {
            writeFailures[key] = left - 1
            throw IllegalStateException("mock 写盘失败：$key")
        }
        files.getOrPut(photoId) { mutableSetOf() }.add(index)
        writeLog += key
    }

    override fun delete(photoId: String) {
        files.remove(photoId)
    }

    override fun files(photoId: String): List<Int> = files[photoId]?.sorted().orEmpty()

    override fun read(photoId: String, index: Int): ByteArray? =
        if (exists(photoId, index)) ByteArray(index) else null
}

/** 记录取数调用；attempts 全量、calls 仅成功；failures 记录剩余失败次数；gates 按页号挂起。 */
private class FakeFetcher : PageFetcher {
    val attempts = mutableListOf<Pair<String, Int>>()
    val calls = mutableListOf<Pair<String, Int>>()
    val failures = mutableMapOf<Pair<String, Int>, Int>()
    val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    val running = AtomicInteger(0)
    val maxSeen = AtomicInteger(0)

    override suspend fun fetch(photoId: String, index: Int): ByteArray {
        attempts += photoId to index
        val now = running.incrementAndGet()
        maxSeen.updateAndGet { cur -> maxOf(cur, now) }
        gates[index]?.await()
        running.decrementAndGet()
        val key = photoId to index
        val left = failures[key] ?: 0
        if (left > 0) {
            failures[key] = left - 1
            throw IllegalStateException("mock 失败：$key")
        }
        calls += key
        return ByteArray(index)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {

    @Test
    fun `concurrency capped and queue auto continues`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[1] = CompletableDeferred() // 挂住前两个任务的第 1 页
        val queue = DownloadQueue(fetcher, FakeStore(), maxConcurrent = 2)
        queue.start(this)
        queue.enqueue(task("p1"))
        queue.enqueue(task("p2"))
        queue.enqueue(task("p3"))
        advanceUntilIdle()
        // 并发上限 2：p3 尚未被取数
        assertEquals(2, fetcher.maxSeen.get())
        assertFalse(fetcher.attempts.any { it.first == "p3" })
        // 放行后队列自动续跑，全部完成
        fetcher.gates.getValue(1).complete(Unit)
        advanceUntilIdle()
        assertEquals(2, fetcher.maxSeen.get())
        for (id in listOf("p1", "p2", "p3")) {
            assertEquals(DONE, queue.updates.value.getValue(id).status, id)
        }
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `enqueue dedupes by photoId`() = runTest {
        val queue = DownloadQueue(FakeFetcher(), FakeStore())
        queue.start(this)
        assertTrue(queue.enqueue(task("p1")))
        assertFalse(queue.enqueue(task("p1")))
        advanceUntilIdle()
        assertEquals(1, queue.updates.value.size)
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `resumes from existing files`() = runTest {
        val store = FakeStore("p1" to listOf(1, 2))
        val fetcher = FakeFetcher()
        val queue = DownloadQueue(fetcher, store)
        queue.start(this)
        queue.enqueue(task("p1", total = 5))
        advanceUntilIdle()
        assertEquals(listOf(3, 4, 5), fetcher.calls.filter { it.first == "p1" }.map { it.second })
        assertEquals(listOf(1, 2, 3, 4, 5), store.files("p1"))
        val done = queue.updates.value.getValue("p1")
        assertEquals(DONE, done.status)
        assertEquals(5, done.index)
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `pause stops fetching and resume continues`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[2] = CompletableDeferred() // 页 2 挂住
        val store = FakeStore()
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 4))
        runCurrent() // 页 1 完成、页 2 挂起
        queue.pause("p1")
        fetcher.gates.getValue(2).complete(Unit)
        advanceUntilIdle()
        assertEquals(PAUSED, queue.updates.value.getValue("p1").status)
        assertEquals(listOf(1, 2), store.files("p1"))
        // 恢复：只补剩余页，不重复取数
        queue.resume("p1")
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        assertEquals(listOf(1, 2, 3, 4), fetcher.calls.filter { it.first == "p1" }.map { it.second })
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `cancel removes task`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[1] = CompletableDeferred()
        val store = FakeStore()
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 2))
        runCurrent() // 页 1 取数挂起
        queue.cancel("p1")
        fetcher.gates.getValue(1).complete(Unit)
        advanceUntilIdle()
        assertNull(queue.updates.value["p1"])
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `page retry then success`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.failures["p1" to 3] = 2 // 页 3 前两次失败、第三次成功
        val queue = DownloadQueue(fetcher, FakeStore())
        queue.start(this)
        queue.enqueue(task("p1", total = 3))
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        assertEquals(3, fetcher.attempts.count { it == ("p1" to 3) })
        assertEquals(listOf(1, 2, 3), fetcher.calls.filter { it.first == "p1" }.map { it.second })
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `page failure beyond retries fails task without blocking queue`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.failures["p1" to 2] = Int.MAX_VALUE / 2 // 页 2 永远失败
        val store = FakeStore()
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 2))
        queue.enqueue(task("p2", total = 1))
        advanceUntilIdle()
        assertEquals(FAILED, queue.updates.value.getValue("p1").status)
        assertEquals(DONE, queue.updates.value.getValue("p2").status)
        assertEquals(listOf(1), store.files("p2"))
        // FAILED 允许重新入队
        assertTrue(queue.enqueue(task("p1", total = 2)))
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `resolver fills unknown metadata`() = runTest {
        val fetcher = FakeFetcher()
        val queue = DownloadQueue(
            fetcher = fetcher,
            store = FakeStore(),
            resolver = TaskResolver { ResolvedChapter(albumId = "a9", title = "第九话", total = 2) },
        )
        queue.start(this)
        queue.enqueue(DownloadTask(photoId = "p9", albumId = "", title = "", total = 0))
        advanceUntilIdle()
        val done = queue.updates.value.getValue("p9")
        assertEquals(DONE, done.status)
        assertEquals(2, done.total)
        assertEquals(2, done.index)
        assertEquals("a9", done.albumId)
        assertEquals("第九话", done.title)
 
        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `store write failure fails task without killing worker`() = runTest {
        val store = FakeStore()
        store.writeFailures["p1" to 1] = 1 // 页 1 写盘抛一次
        val queue = DownloadQueue(FakeFetcher(), store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1"))
        queue.enqueue(task("p2"))
        advanceUntilIdle()
        // 故障隔离：p1 置 FAILED，worker 继续完成 p2，队列未卡死
        assertEquals(FAILED, queue.updates.value.getValue("p1").status)
        assertEquals(DONE, queue.updates.value.getValue("p2").status)
        assertEquals(listOf(1), store.files("p2"))

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `resolver failure fails task without blocking queue`() = runTest {
        val fetcher = FakeFetcher()
        val queue = DownloadQueue(
            fetcher = fetcher,
            store = FakeStore(),
            resolver = TaskResolver { throw IllegalStateException("解析失败") },
            maxConcurrent = 1,
        )
        queue.start(this)
        queue.enqueue(DownloadTask(photoId = "p9", albumId = "", title = "", total = 0))
        queue.enqueue(task("p2"))
        advanceUntilIdle()
        assertEquals(FAILED, queue.updates.value.getValue("p9").status)
        assertEquals(DONE, queue.updates.value.getValue("p2").status)
        assertTrue(fetcher.calls.none { it.first == "p9" })

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `pause during last page stays paused then resume completes`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[2] = CompletableDeferred() // 末页取数挂住
        val store = FakeStore()
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 2))
        runCurrent()
        queue.pause("p1")
        fetcher.gates.getValue(2).complete(Unit)
        advanceUntilIdle()
        // 下载中暂停：末页已落盘也不能 RUNNING/PAUSED -> DONE，保持 PAUSED 不崩
        assertEquals(PAUSED, queue.updates.value.getValue("p1").status)
        assertEquals(listOf(1, 2), store.files("p1"))
        // 恢复后不重取已落盘页，正常完成
        queue.resume("p1")
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        assertEquals(1, fetcher.attempts.count { it == ("p1" to 2) })

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `write failure while paused keeps paused instead of crashing`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[2] = CompletableDeferred() // 末页取数挂住
        val store = FakeStore()
        store.writeFailures["p1" to 2] = 1 // 恢复后重取末页时写盘成功
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 2))
        runCurrent()
        queue.pause("p1")
        fetcher.gates.getValue(2).complete(Unit)
        advanceUntilIdle()
        // 暂停中写盘失败：不能走 PAUSED -> FAILED 非法转移，保持 PAUSED
        assertEquals(PAUSED, queue.updates.value.getValue("p1").status)
        assertEquals(listOf(1), store.files("p1"))
        queue.resume("p1")
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        assertEquals(listOf(1, 2), store.files("p1"))

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `cancellation is rethrown instead of consumed as retry`() = runTest {
        val attempts = AtomicInteger(0)
        val fetcher = PageFetcher { _, _ ->
            attempts.incrementAndGet()
            throw CancellationException("取数取消")
        }
        val queue = DownloadQueue(fetcher, FakeStore(), maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1"))
        advanceUntilIdle()
        // 取消必须向上传播：既不重试也不落 FAILED
        assertEquals(1, attempts.get())
        assertEquals(RUNNING, queue.updates.value.getValue("p1").status)

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `shutdown drains running task and ignores later enqueue`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[1] = CompletableDeferred()
        val store = FakeStore()
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1"))
        runCurrent()
        queue.shutdown()
        fetcher.gates.getValue(1).complete(Unit)
        advanceUntilIdle()
        // 停机排空当前任务，不产生新崩溃
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        queue.enqueue(task("p2"))
        advanceUntilIdle()
        // 停机后入队不再有 worker 消费：任务可解释地停在 QUEUED
        assertEquals(QUEUED, queue.updates.value.getValue("p2").status)
    }

    @Test
    fun `restore paused snapshot resumes from persisted cursor`() = runTest {
        val fetcher = FakeFetcher()
        val store = FakeStore("p1" to listOf(1, 2)) // 重启前已落盘页 1、2
        val queue = DownloadQueue(fetcher, store, maxConcurrent = 1)
        queue.start(this)
        val restored = DownloadTask(
            photoId = "p1",
            albumId = "a1",
            title = "t1",
            total = 5,
            index = 2,
            status = PAUSED,
        )
        assertTrue(queue.restore(restored))
        advanceUntilIdle()
        // 内存无任务时按快照重建：从游标续传，已落盘页不重取
        assertEquals(listOf(3, 4, 5), fetcher.calls.filter { it.first == "p1" }.map { it.second })
        val done = queue.updates.value.getValue("p1")
        assertEquals(DONE, done.status)
        assertEquals(5, done.index)

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `restore failed snapshot retries from donePages not zero`() = runTest {
        val fetcher = FakeFetcher()
        val queue = DownloadQueue(fetcher, FakeStore("p1" to listOf(1)), maxConcurrent = 1)
        queue.start(this)
        val retry = DownloadTask(
            photoId = "p1",
            albumId = "a1",
            title = "t1",
            total = 3,
            index = 1,
            status = FAILED,
        )
        assertTrue(queue.restore(retry))
        advanceUntilIdle()
        // 失败重试从持久化游标续传，不回到第 0 页
        assertEquals(listOf(2, 3), fetcher.calls.filter { it.first == "p1" }.map { it.second })
        assertEquals(DONE, queue.updates.value.getValue("p1").status)

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `restore resumes in-memory paused task without rewinding cursor`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[2] = CompletableDeferred()
        val queue = DownloadQueue(fetcher, FakeStore(), maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 3))
        runCurrent()
        queue.pause("p1")
        fetcher.gates.getValue(2).complete(Unit)
        advanceUntilIdle()
        assertEquals(PAUSED, queue.updates.value.getValue("p1").status)
        // 记录快照可能滞后（donePages=1），内存任务游标已到 2：恢复必须续用内存游标
        val stale = DownloadTask(
            photoId = "p1",
            albumId = "a1",
            title = "t1",
            total = 3,
            index = 1,
            status = PAUSED,
        )
        assertTrue(queue.restore(stale))
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)
        assertEquals(1, fetcher.attempts.count { it == ("p1" to 2) })
        assertEquals(listOf(1, 2, 3), fetcher.calls.filter { it.first == "p1" }.map { it.second })

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `restore rejects done and running snapshots`() = runTest {
        val fetcher = FakeFetcher()
        fetcher.gates[1] = CompletableDeferred()
        val queue = DownloadQueue(fetcher, FakeStore(), maxConcurrent = 1)
        queue.start(this)
        queue.enqueue(task("p1", total = 2))
        runCurrent()
        // 运行中不接受恢复快照，避免重复取数
        assertFalse(
            queue.restore(
                DownloadTask(photoId = "p1", albumId = "a1", title = "t1", total = 2, index = 1, status = PAUSED),
            ),
        )
        // 终态记录不允许复活
        assertFalse(
            queue.restore(
                DownloadTask(photoId = "p2", albumId = "a2", title = "t2", total = 1, index = 1, status = DONE),
            ),
        )
        assertNull(queue.updates.value["p2"])
        fetcher.gates.getValue(1).complete(Unit)
        advanceUntilIdle()
        assertEquals(DONE, queue.updates.value.getValue("p1").status)

        queue.shutdown()
        advanceUntilIdle()
    }
}
