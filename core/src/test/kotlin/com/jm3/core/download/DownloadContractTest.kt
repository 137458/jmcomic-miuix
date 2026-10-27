package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.model.PageImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private const val CONTRACT_PHOTO_ID = "p1"

/** 固定 3 页清单：PageImage.index 0 起，与 Mappers.toPhotoPages 的 mapIndexed 一致。 */
private val contractPages = listOf(contractPage(0), contractPage(1), contractPage(2))

private fun contractPage(index: Int) = PageImage(
    photoId = CONTRACT_PHOTO_ID,
    scrambleId = "",
    index = index,
    filename = "%05d.jpg".format(index + 1),
    url = "",
)

/** 可预置已落盘页（1 起页号）的内存存储；记录写入的页号与字节。 */
private class ContractStore(vararg seeded: Int) : DownloadStore {
    private val pages = seeded.toMutableSet()

    val writeLog = mutableListOf<Pair<Int, ByteArray>>()

    override fun exists(photoId: String, index: Int): Boolean = pages.contains(index)

    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        pages += index
        writeLog += index to bytes
    }

    override fun delete(photoId: String) {
        pages.clear()
    }

    override fun files(photoId: String): List<Int> = pages.sorted()

    override fun read(photoId: String, index: Int): ByteArray? =
        if (pages.contains(index)) ByteArray(index) else null
}

/**
 * 假取数器：复刻真实取数链路 GatewayPageFetcher 的定位方式。
 * 队列传入 1 起页号，页列表 PageImage.index 0 起，两侧契约必须经共享换算对齐。
 */
private class ContractFetcher(private val pages: List<PageImage>) : PageFetcher {
    val requests = mutableListOf<Int>()

    override suspend fun fetch(photoId: String, index: Int): ByteArray {
        requests += index
        val image = pages.firstOrNull { it.index == PageNumbers.imageIndex(index) }
            ?: error("页号越界：photoId=$photoId 页号=$index")
        return byteArrayOf(image.index.toByte())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadContractTest {

    @Test
    fun `queue requests and stores pages 1 to 3 exactly once`() = runTest {
        val store = ContractStore()
        val fetcher = ContractFetcher(contractPages)
        val queue = DownloadQueue(fetcher, store)
        queue.start(this)
        queue.enqueue(DownloadTask(photoId = CONTRACT_PHOTO_ID, albumId = "a1", title = "t1", total = 3))
        advanceUntilIdle()

        // 队列页号 1 起：请求 1,2,3 各一次，落盘键与页号一致。
        assertEquals(listOf(1, 2, 3), fetcher.requests)
        assertEquals(listOf(1, 2, 3), store.writeLog.map { it.first })
        // 落盘字节 = 对应 PageImage.index：页 1 存 0 起下标 0 的图。
        assertEquals(listOf(0, 1, 2), store.writeLog.map { it.second.single().toInt() })
        val finished = queue.updates.value.getValue(CONTRACT_PHOTO_ID)
        assertEquals(DONE, finished.status)
        assertEquals(3, finished.index)

        queue.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `resume skips stored page and still finishes done`() = runTest {
        val store = ContractStore(2) // 预置页 2（1 起）已落盘
        val fetcher = ContractFetcher(contractPages)
        val queue = DownloadQueue(fetcher, store)
        queue.start(this)
        queue.enqueue(DownloadTask(photoId = CONTRACT_PHOTO_ID, albumId = "a1", title = "t1", total = 3))
        advanceUntilIdle()

        assertEquals(listOf(1, 3), fetcher.requests)
        assertEquals(listOf(0, 2), store.writeLog.map { it.second.single().toInt() })
        assertEquals(listOf(1, 2, 3), store.files(CONTRACT_PHOTO_ID))
        val finished = queue.updates.value.getValue(CONTRACT_PHOTO_ID)
        assertEquals(DONE, finished.status)
        assertEquals(3, finished.index)

        queue.shutdown()
        advanceUntilIdle()
    }
}
