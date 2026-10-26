package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    override fun exists(photoId: String, index: Int): Boolean = files[photoId]?.contains(index) == true

    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        files.getOrPut(photoId) { mutableSetOf() }.add(index)
        writeLog += photoId to index
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
}
