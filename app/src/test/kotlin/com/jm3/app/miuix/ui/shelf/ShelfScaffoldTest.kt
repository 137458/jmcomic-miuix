package com.jm3.app.miuix.ui.shelf

import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadLabel
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadRow
import com.jm3.core.download.DownloadStatus
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 假的下载端口：账本由用例摆放，动作只记调用参数。 */
private class FakeShelfDownloads : ShelfDownloadPort {
    val ledger = mutableListOf<DownloadRecord>()
    val removed = mutableListOf<String>()
    val paused = mutableListOf<String>()
    val resumed = mutableListOf<String>()
    var ensureStartedCalls = 0
    private val progress = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override val updates: Flow<Any?> get() = progress

    override fun ensureStarted() {
        ensureStartedCalls++
    }

    override suspend fun records(): List<DownloadRecord> = ledger.toList()

    override fun pause(photoId: String) {
        paused += photoId
    }

    override fun resumeRecord(record: DownloadRecord): Boolean {
        resumed += record.photoId
        return true
    }

    override suspend fun remove(photoId: String) {
        removed += photoId
        ledger.removeAll { it.photoId == photoId }
    }

    /** 队列推进一步：书架应重读账本。 */
    fun pushProgress() {
        progress.tryEmit(Unit)
    }
}

/**
 * 状态机与承载它协程的作用域。
 *
 * 用 Unconfined 调度器是为了让动作里的副作用立即落定：
 * `enter()` 与 `confirmDelete()` 里的协程都在注入的 scope 上跑。
 */
private class ShelfHarness(
    val scope: CoroutineScope,
    val downloads: FakeShelfDownloads,
    val subject: ShelfScaffoldState,
)

private fun TestScope.harness(
    downloads: FakeShelfDownloads = FakeShelfDownloads(),
    imageHost: SessionOutcome<String> = SessionOutcome.Ok("https://img.jm3.test"),
): ShelfHarness {
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    return ShelfHarness(scope, downloads, ShelfScaffoldState(scope, downloads, { imageHost }))
}

private fun record(
    photoId: String = "p1",
    albumId: String = "123456",
    title: String = "海边的鲸娘",
    total: Int = 12,
    donePages: Int = 3,
    status: DownloadStatus = DownloadStatus.RUNNING,
): DownloadRecord = DownloadRecord(
    photoId = photoId,
    albumId = albumId,
    title = title,
    total = total,
    donePages = donePages,
    status = status,
    updatedAt = 1L,
)

private fun row(action: DownloadAction): DownloadRow = DownloadRow(
    label = DownloadLabel.Running,
    donePages = 3,
    total = 12,
    showsProgressBar = true,
    action = action,
)

class ShelfScaffoldTest {

    @Test
    fun `初始状态没有记录没有待删目标且未装载完`() = runTest {
        val shelf = harness()

        assertEquals(emptyList(), shelf.subject.records)
        assertNull(shelf.subject.pendingDeleteRecord)
        assertEquals("", shelf.subject.imageHost)
        assertFalse(shelf.subject.loaded)
    }

    @Test
    fun `首屏装载起队列读账本并标记已加载`() = runTest {
        val downloads = FakeShelfDownloads()
        downloads.ledger += record(photoId = "p1")
        downloads.ledger += record(photoId = "p2", status = DownloadStatus.DONE)
        val shelf = harness(downloads)

        shelf.scope.launch { shelf.subject.enter() }
        advanceUntilIdle()

        assertEquals(1, downloads.ensureStartedCalls)
        assertEquals(listOf("p1", "p2"), shelf.subject.records.map { it.photoId })
        assertTrue(shelf.subject.loaded)
    }

    @Test
    fun `队列进度推进后重读账本`() = runTest {
        val downloads = FakeShelfDownloads()
        downloads.ledger += record(photoId = "p1")
        val shelf = harness(downloads)

        shelf.scope.launch { shelf.subject.enter() }
        advanceUntilIdle()
        assertEquals(listOf("p1"), shelf.subject.records.map { it.photoId })

        downloads.ledger += record(photoId = "p2")
        downloads.pushProgress()
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), shelf.subject.records.map { it.photoId })
    }

    @Test
    fun `封面域名取到后落到状态里`() = runTest {
        val shelf = harness(imageHost = SessionOutcome.Ok("https://img.jm3.test"))

        shelf.subject.loadImageHost()

        assertEquals("https://img.jm3.test", shelf.subject.imageHost)
    }

    @Test
    fun `封面域名请求失败时退化为空串`() = runTest {
        val shelf = harness(imageHost = SessionOutcome.Failed("boom"))

        shelf.subject.loadImageHost()

        assertEquals("", shelf.subject.imageHost)
    }

    @Test
    fun `会话失效时封面域名同样退化为空串`() = runTest {
        val shelf = harness(imageHost = SessionOutcome.Expired)

        shelf.subject.loadImageHost()

        assertEquals("", shelf.subject.imageHost)
    }

    @Test
    fun `已完成行的主操作交给皮肤跳阅读器`() = runTest {
        val shelf = harness()

        val navigate = shelf.subject.performPrimaryAction(record(), row(DownloadAction.Open))

        assertTrue(navigate)
        assertEquals(emptyList(), shelf.downloads.paused)
        assertEquals(emptyList(), shelf.downloads.resumed)
    }

    @Test
    fun `进行中行的主操作就地暂停不跳转`() = runTest {
        val shelf = harness()

        val navigate = shelf.subject.performPrimaryAction(record(photoId = "p7"), row(DownloadAction.Pause))

        assertFalse(navigate)
        assertEquals(listOf("p7"), shelf.downloads.paused)
        assertEquals(emptyList(), shelf.downloads.resumed)
    }

    @Test
    fun `暂停行的主操作就地续传不跳转`() = runTest {
        val shelf = harness()

        val navigate = shelf.subject.performPrimaryAction(record(photoId = "p8"), row(DownloadAction.Resume))

        assertFalse(navigate)
        assertEquals(listOf("p8"), shelf.downloads.resumed)
        assertEquals(emptyList(), shelf.downloads.paused)
    }

    @Test
    fun `失败行的主操作就地重试不跳转`() = runTest {
        val shelf = harness()

        val navigate = shelf.subject.performPrimaryAction(record(photoId = "p9"), row(DownloadAction.Retry))

        assertFalse(navigate)
        assertEquals(listOf("p9"), shelf.downloads.resumed)
    }

    @Test
    fun `请求删除挂起目标取消后清空`() = runTest {
        val shelf = harness()
        val target = record(photoId = "p3")

        shelf.subject.requestDelete(target)
        assertEquals(target, shelf.subject.pendingDeleteRecord)

        shelf.subject.dismissDelete()
        assertNull(shelf.subject.pendingDeleteRecord)
    }

    @Test
    fun `确认删除落账并本地剔除该行`() = runTest {
        val downloads = FakeShelfDownloads()
        downloads.ledger += record(photoId = "p1")
        downloads.ledger += record(photoId = "p2")
        val shelf = harness(downloads)
        shelf.scope.launch { shelf.subject.enter() }
        advanceUntilIdle()

        shelf.subject.requestDelete(record(photoId = "p1"))
        shelf.subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("p1"), downloads.removed)
        assertEquals(listOf("p2"), shelf.subject.records.map { it.photoId })
        assertNull(shelf.subject.pendingDeleteRecord)
    }

    @Test
    fun `没有待删目标时确认删除不动账本`() = runTest {
        val shelf = harness()

        shelf.subject.confirmDelete()
        advanceUntilIdle()

        assertEquals(emptyList(), shelf.downloads.removed)
        assertEquals(emptyList(), shelf.subject.records)
    }

    @Test
    fun `书架摘要先车号后进度`() = runTest {
        val summary = shelfSummary(record(albumId = "123456"), row(DownloadAction.Pause), "下载中")

        assertEquals("#123456 · 下载中 3/12", summary)
    }

    @Test
    fun `没有车号时摘要只有进度`() {
        assertEquals("下载中 3/12", shelfSummary(record(albumId = ""), row(DownloadAction.Pause), "下载中"))
        assertEquals("下载中 3/12", shelfSummary(record(albumId = "-1"), row(DownloadAction.Pause), "下载中"))
        assertEquals("下载中 3/12", shelfSummary(record(albumId = "  "), row(DownloadAction.Pause), "下载中"))
    }

    @Test
    fun `删除文案带书名与本地文件提示`() {
        assertEquals(
            "确定要删除《海边的鲸娘》及已下载的本地文件吗？",
            shelfDeleteMessage(record(title = "海边的鲸娘")),
        )
    }

    @Test
    fun `删除文案书名空白时退到编号`() {
        assertEquals(
            "确定要删除《p5》及已下载的本地文件吗？",
            shelfDeleteMessage(record(photoId = "p5", title = "  ")),
        )
    }
}
