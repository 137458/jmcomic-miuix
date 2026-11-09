package com.jm3.app.miuix.update

import com.jm3.core.update.ApkDigest
import com.jm3.core.update.ReleaseAsset
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** 状态等待上限：只作卡死兜底，正常路径由 StateFlow 的挂起等待立即唤醒。 */
private const val WAIT_TIMEOUT_MS = 10_000L

/**
 * 下载器并发收尾契约：旧任务的取消/失败分支不得碰新任务的落盘文件。
 *
 * 用可控连接把「旧任务还卡在阻塞读」的时序固定下来，再取消并重启；
 * 断言最终留下的是新任务写出的安装包，且目录里没有半成品。
 * 收尾一律"等到协程真的结束"再断言（StateFlow 挂起等待 + join 子 Job），不靠 sleep 猜时间。
 */
class UpdateDownloaderTest {

    private val dir: File = Files.createTempDirectory("jm3-update-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun asset(digest: String? = null) = ReleaseAsset(
        name = "jm3.apk",
        size = PAYLOAD.size.toLong(),
        downloadUrl = "https://example.com/jm3.apk",
        digest = digest,
    )

    @Test
    fun `取消后立即重启 旧任务不得删掉新任务写出的安装包`() {
        val firstRead = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        var opened = 0
        val downloader = UpdateDownloader(scope, dir) {
            opened++
            if (opened == 1) {
                FakeConnection(GatedInputStream(PAYLOAD, firstRead, releaseOld), PAYLOAD.size.toLong())
            } else {
                FakeConnection(PAYLOAD.inputStream(), PAYLOAD.size.toLong())
            }
        }

        downloader.start(asset())
        assertTrue(firstRead.await(5, TimeUnit.SECONDS), "第一次下载应进入读流阶段")
        downloader.cancel()
        downloader.start(asset())

        val done = awaitState<UpdateDownloadState.Done>(downloader)

        // 放行旧任务后等它真正跑完：它的 finally 会删自己的半成品，也可能回写状态，
        // 只有 join 过子 Job 才能确定这些收尾动作都已经发生，而不是靠 sleep 赌时间。
        releaseOld.countDown()
        awaitDownloadIdle()

        val finished = downloader.state.value
        assertTrue(finished is UpdateDownloadState.Done, "旧任务收尾不得覆盖新任务状态，实际=$finished")
        assertContentEquals(PAYLOAD, done.file.readBytes(), "Done 必须指向新任务写出的完整文件")
        assertTrue(done.file.isFile, "旧任务收尾不得删除新任务的安装包")
        assertNoPartials()
    }

    @Test
    fun `新任务失败时保留上一次已下载的安装包`() {
        val existing = File(dir, "jm3.apk").apply { writeText("old-apk") }
        val downloader = UpdateDownloader(scope, dir) { throw IOException("断网") }

        downloader.start(asset())

        awaitState<UpdateDownloadState.Failed>(downloader)
        awaitDownloadIdle()
        assertTrue(existing.isFile, "新下载失败不得删除上一次的安装包")
        assertEquals("old-apk", existing.readText())
        assertNoPartials()
    }

    @Test
    fun `摘要不一致时删除本次半成品但保留旧安装包`() {
        val existing = File(dir, "jm3.apk").apply { writeText("old-apk") }
        val downloader = UpdateDownloader(scope, dir) { FakeConnection(PAYLOAD.inputStream(), PAYLOAD.size.toLong()) }
        val wrongDigest = "sha256:" + "0".repeat(64)

        downloader.start(asset(digest = wrongDigest))

        awaitState<UpdateDownloadState.Failed>(downloader)
        awaitDownloadIdle()
        assertTrue(existing.isFile, "校验失败只应丢弃本次下载，不得删除旧安装包")
        assertEquals("old-apk", existing.readText())
        assertNoPartials()
    }

    @Test
    fun `摘要一致时落成正式文件名`() {
        // 摘要按真实文件路径算：生产侧的校验走的就是 sha256Hex(file)，这里顺带覆盖流式读取。
        val payloadFile = File(dir, "payload.apk").apply { writeBytes(PAYLOAD) }
        val digest = "sha256:" + ApkDigest.sha256Hex(payloadFile)
        val downloader = UpdateDownloader(scope, dir) { FakeConnection(PAYLOAD.inputStream(), PAYLOAD.size.toLong()) }

        downloader.start(asset(digest = digest))

        val done = awaitState<UpdateDownloadState.Done>(downloader)
        // Done 是在 finally 删半成品之前发布的，join 之后目录状态才是终态
        awaitDownloadIdle()
        assertEquals(File(dir, "jm3.apk"), done.file)
        assertContentEquals(PAYLOAD, done.file.readBytes())
        assertNoPartials()
    }

    @Test
    fun `注入的假时钟驱动进度节流 未到间隔不上报 到点按七比三平滑`() {
        val payload = ByteArray(256) { (it + 1).toByte() }
        val signals = List(5) { CountDownLatch(1) }
        val releases = List(5) { CountDownLatch(1) }
        var now = 0L
        val downloader = UpdateDownloader(scope, dir, clock = { now }) {
            FakeConnection(SteppedInputStream(payload, 64, signals, releases), payload.size.toLong())
        }

        try {
            downloader.start(asset(digest = null))

            // 放行第一次读后等它读到第二次：协程走到下一次读，说明上一次的 onBytes 已经跑完，
            // 此刻读到的是那一次的节流结论，不需要 sleep 去赌。
            releases[0].countDown()
            awaitStep(signals, 1)
            // 时钟没推进：节流不上报，状态还停在启动时的 0 字节
            assertEquals(0L, downloadingState(downloader).downloaded, "未到 400ms 不应上报进度")

            // 推进 400ms 放行第二次读：本段新增 128B / 0.4s = 320B/s，首次平滑取 3/10 得 96
            now = 400L
            releases[1].countDown()
            awaitStep(signals, 2)
            val second = downloadingState(downloader)
            assertEquals(128L, second.downloaded, "到点应按累计字节上报")
            assertEquals(96L, second.speedBytesPerSec, "首次上报的平滑速度是瞬时速度的 3/10")

            // 再推进 400ms：本段 64B / 0.4s = 160B/s，与 96 按 7:3 平滑得 115
            now = 800L
            releases[2].countDown()
            awaitStep(signals, 3)
            assertEquals(115L, downloadingState(downloader).speedBytesPerSec)

            // 同样 160B/s 的瞬时速度继续平滑到 128，读完收尾落盘
            now = 1_200L
            releases[3].countDown()
            awaitStep(signals, 4)
            assertEquals(128L, downloadingState(downloader).speedBytesPerSec)
            releases[4].countDown()

            val done = awaitState<UpdateDownloadState.Done>(downloader)
            awaitDownloadIdle()
            assertContentEquals(payload, done.file.readBytes())
        } finally {
            // 断言失败时也要放行，避免下载协程永远卡在读上
            releases.forEach { it.countDown() }
        }
    }

    /**
     * 等到本测试作用域里的下载协程全部收尾。
     *
     * 断言半成品是否清理干净必须在协程退出后做：Done/Failed 在 finally 之前就发布了，
     * 只看状态值会在删除动作还没跑完时断言，属于时序竞态。
     */
    private fun awaitDownloadIdle() = runBlocking {
        scope.coroutineContext.job.children.forEach { it.join() }
    }

    /**
     * 等到目标状态。
     *
     * 状态只由后台协程推进，因此挂起等 StateFlow 的下一帧（first）而不是轮询 + sleep：
     * 状态一变立刻唤醒，超时只用于兜底卡死。
     */
    private inline fun <reified T : UpdateDownloadState> awaitState(downloader: UpdateDownloader): T {
        val state = runBlocking {
            withTimeout(WAIT_TIMEOUT_MS) {
                downloader.state.first { it is T }
            }
        }
        return state as T
    }

    /** 等下载协程走到第 [index] 次读；协程卡死时最多等 5 秒。 */
    private fun awaitStep(signals: List<CountDownLatch>, index: Int) {
        assertTrue(signals[index].await(5, TimeUnit.SECONDS), "下载协程未走到第 $index 次读")
    }

    /** 取当前下载中状态；类型不对说明协程已提前收尾，直接让断言报错。 */
    private fun downloadingState(downloader: UpdateDownloader): UpdateDownloadState.Downloading =
        downloader.state.value as UpdateDownloadState.Downloading

    private fun assertNoPartials() {
        val leftovers = dir.listFiles().orEmpty().filter { it.name.endsWith(".part") }
        assertEquals(emptyList(), leftovers.map { it.name }, "下载结束不应留下半成品")
    }

    private companion object {
        val PAYLOAD = ByteArray(64) { (it + 1).toByte() }
    }
}

/**
 * 可控连接：不真的建连，只把内容长度与输入流交给被测逻辑。
 */
private class FakeConnection(
    private val stream: InputStream,
    private val contentLength: Long,
) : HttpURLConnection(URL("https://example.com/jm3.apk")) {

    override fun connect() {}

    override fun disconnect() {}

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = 200

    override fun getContentLengthLong(): Long = contentLength

    override fun getInputStream(): InputStream = stream
}

/**
 * 分步放行的输入流：每次 read 先发信号、等放行，再吐 [chunk] 字节。
 *
 * 用来把「协程正在第几次读」变成可等待的确定性事件：等到第 k+1 次读的信号，
 * 就能确认第 k 次读之后的节流判定已经发生。读到 EOF 时返回 -1。
 */
private class SteppedInputStream(
    private val bytes: ByteArray,
    private val chunk: Int,
    private val signals: List<CountDownLatch>,
    private val releases: List<CountDownLatch>,
) : InputStream() {

    private var pos = 0
    private var step = 0

    override fun read(): Int = throw UnsupportedOperationException("按块读取")

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val index = step++
        signals[index].countDown()
        releases[index].await()
        if (pos >= bytes.size) return -1
        val count = minOf(chunk, length, bytes.size - pos)
        System.arraycopy(bytes, pos, buffer, offset, count)
        pos += count
        return count
    }
}

/**
 * 首次 read 先发信号，然后卡在 [release] 上：用来把「旧任务正在阻塞读」的时序固定住。
 */
private class GatedInputStream(
    private val bytes: ByteArray,
    private val firstRead: CountDownLatch,
    private val release: CountDownLatch,
) : InputStream() {

    private var pos = 0
    private var signalled = false

    override fun read(): Int = throw UnsupportedOperationException("按块读取")

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!signalled) {
            signalled = true
            firstRead.countDown()
        }
        release.await()
        if (pos >= bytes.size) return -1
        val count = minOf(length, bytes.size - pos)
        System.arraycopy(bytes, pos, buffer, offset, count)
        pos += count
        return count
    }
}
