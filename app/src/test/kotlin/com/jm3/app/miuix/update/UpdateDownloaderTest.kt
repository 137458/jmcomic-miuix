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

/**
 * 下载器并发收尾契约：旧任务的取消/失败分支不得碰新任务的落盘文件。
 *
 * 用可控连接把「旧任务还卡在阻塞读」的时序固定下来，再取消并重启；
 * 断言最终留下的是新任务写出的安装包，且目录里没有半成品。
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

        val done = waitFor<UpdateDownloadState.Done>(downloader)
        assertContentEquals(PAYLOAD, done.file.readBytes(), "Done 必须指向新任务写出的完整文件")

        // 放行旧任务：它只能删自己的半成品，不得删新任务已经写完的安装包
        releaseOld.countDown()
        Thread.sleep(500)
        val finished = downloader.state.value
        assertTrue(finished is UpdateDownloadState.Done, "旧任务收尾不得覆盖新任务状态，实际=$finished")
        assertTrue(done.file.isFile, "旧任务收尾不得删除新任务的安装包")
        assertNoPartials()
    }

    @Test
    fun `新任务失败时保留上一次已下载的安装包`() {
        val existing = File(dir, "jm3.apk").apply { writeText("old-apk") }
        val downloader = UpdateDownloader(scope, dir) { throw IOException("断网") }

        downloader.start(asset())

        waitFor<UpdateDownloadState.Failed>(downloader)
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

        waitFor<UpdateDownloadState.Failed>(downloader)
        assertTrue(existing.isFile, "校验失败只应丢弃本次下载，不得删除旧安装包")
        assertEquals("old-apk", existing.readText())
        assertNoPartials()
    }

    @Test
    fun `摘要一致时落成正式文件名`() {
        val digest = "sha256:" + ApkDigest.sha256Hex(PAYLOAD)
        val downloader = UpdateDownloader(scope, dir) { FakeConnection(PAYLOAD.inputStream(), PAYLOAD.size.toLong()) }

        downloader.start(asset(digest = digest))

        val done = waitFor<UpdateDownloadState.Done>(downloader)
        assertEquals(File(dir, "jm3.apk"), done.file)
        assertContentEquals(PAYLOAD, done.file.readBytes())
        assertNoPartials()
    }

    private fun assertNoPartials() {
        val leftovers = dir.listFiles().orEmpty().filter { it.name.endsWith(".part") }
        assertEquals(emptyList(), leftovers.map { it.name }, "下载结束不应留下半成品")
    }

    /** 轮询到目标状态；状态只会由后台协程推进，因此按真实时间等待。 */
    private inline fun <reified T : UpdateDownloadState> waitFor(downloader: UpdateDownloader): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val state = downloader.state.value
            if (state is T) return state
            Thread.sleep(10)
        }
        throw AssertionError("等待下载状态超时，实际=${downloader.state.value}")
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
