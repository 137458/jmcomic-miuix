package com.jm3.app.miuix.ui.reader

import com.jm3.app.miuix.data.download.encodePhotoPages
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.session.SessionOutcome
import java.net.ConnectException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 阅读器入口骨架的状态机测试：全部在纯 kotlin.test 上跑。
 *
 * 假端口替掉账本、网关与历史库，断言落在「页面看得见的结果」上：内容态里的页列表与章节、
 * 失败文案与是否可重试、历史条目字段、代际是否真的作废旧响应。
 */

private const val PHOTO_ID = "photo-2"
private const val ALBUM_ID = "album-42"
private const val COVER_URL = "https://img.jm3.test/album-42.jpg"

private fun page(photoId: String = PHOTO_ID): PageImage = PageImage(
    photoId = photoId,
    scrambleId = "scramble-1",
    index = 1,
    filename = "001.jpg",
    url = "https://img.jm3.test/001.webp",
)

private fun photo(
    photoId: String = PHOTO_ID,
    albumId: String = ALBUM_ID,
    title: String = "第 2 话",
): PhotoPages = PhotoPages(
    photoId = photoId,
    albumId = albumId,
    title = title,
    images = listOf(page(photoId)),
)

private fun detail(albumId: String = ALBUM_ID): AlbumDetail = AlbumDetail(
    id = albumId,
    title = "合集",
    description = "描述",
    authors = listOf("作者"),
    tags = listOf("同人"),
    works = emptyList(),
    actors = emptyList(),
    likes = null,
    views = null,
    coverUrl = COVER_URL,
    chapters = listOf(
        Chapter(id = "photo-3", title = "第 3 话", sortOrder = 3),
        Chapter(id = PHOTO_ID, title = "第 2 话", sortOrder = 2),
        Chapter(id = "photo-1", title = "第 1 话", sortOrder = 1),
    ),
    favorited = true,
    liked = true,
)

private fun record(
    status: DownloadStatus = DownloadStatus.DONE,
    albumId: String = ALBUM_ID,
    title: String = "第 2 话",
): DownloadRecord = DownloadRecord(
    photoId = PHOTO_ID,
    albumId = albumId,
    title = title,
    total = 1,
    donePages = 1,
    status = status,
    updatedAt = 0L,
)

/** 可编程端口：记录每次调用的入参，并按需卡住、失败或排队返回。 */
private class FakeReaderEntryPort : ReaderEntryPort {

    var saved: DownloadLedgerEntry? = null
    var savedFailure: Throwable? = null
    var photoOutcome: SessionOutcome<PhotoPages> = SessionOutcome.Ok(photo())
    var albumOutcome: SessionOutcome<AlbumDetail> = SessionOutcome.Ok(detail())
    var coverOutcome: SessionOutcome<String?> = SessionOutcome.Ok(COVER_URL)
    var historyFailure: Throwable? = null

    val photoCalls = mutableListOf<String>()
    val albumCalls = mutableListOf<String>()
    val coverCalls = mutableListOf<String>()
    val history = mutableListOf<HistoryEntry>()

    private val queuedPhotos = ArrayDeque<SessionOutcome<PhotoPages>>()
    private val photoGates = ArrayDeque<CompletableDeferred<Unit>>()

    fun queuePhoto(outcome: SessionOutcome<PhotoPages>) {
        queuedPhotos.addLast(outcome)
    }

    /** 让下一次 photo 请求停在闸门上，用来制造「旧响应迟到」。 */
    fun holdNextPhotoRequest(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        photoGates.addLast(gate)
        return gate
    }

    override suspend fun savedDownload(photoId: String): DownloadLedgerEntry? {
        savedFailure?.let { throw it }
        return saved
    }

    override suspend fun photo(photoId: String): SessionOutcome<PhotoPages> {
        photoCalls += photoId
        val outcome = queuedPhotos.removeFirstOrNull() ?: photoOutcome
        photoGates.removeFirstOrNull()?.await()
        return outcome
    }

    override suspend fun album(albumId: String): SessionOutcome<AlbumDetail> {
        albumCalls += albumId
        return albumOutcome
    }

    override suspend fun coverUrl(albumId: String): SessionOutcome<String?> {
        coverCalls += albumId
        return coverOutcome
    }

    override suspend fun upsertHistory(entry: HistoryEntry) {
        historyFailure?.let { throw it }
        history += entry
    }
}

private fun state(
    scope: CoroutineScope,
    port: FakeReaderEntryPort,
    photoId: String = PHOTO_ID,
): ReaderEntryScaffoldState = ReaderEntryScaffoldState(scope = scope, port = port, photoId = photoId)

/**
 * 独立测试作用域：与 runTest 共用同一个调度器，因此 advanceUntilIdle() 能推进它。
 *
 * TestScope.scope 上的任务在当前 kotlinx-coroutines-test 版本里不会被 advanceUntilIdle() 推进，
 * 用它会导致「协程体一次都没跑」的假红。
 */
private fun TestScope.entryScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

class ReaderEntryScaffoldTest {

    @Test
    fun `初始状态为加载中`() = runTest {
        val scope = entryScope()
        val subject = state(scope, FakeReaderEntryPort())
        assertEquals(ReaderUiState.Loading, subject.uiState)
    }

    @Test
    fun `首载成功进入内容态并带排序后的章节与收藏状态`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        val loaded = subject.uiState as ReaderUiState.Loaded
        assertEquals(PHOTO_ID, loaded.photo.photoId)
        assertEquals(ALBUM_ID, loaded.photo.albumId)
        assertEquals(listOf("photo-1", PHOTO_ID, "photo-3"), loaded.chapters.map { it.id })
        assertEquals(1, loaded.chapterIndex)
        assertTrue(loaded.favorited)
        assertTrue(loaded.liked)
        assertEquals(listOf(PHOTO_ID), port.photoCalls)
        assertEquals(listOf(ALBUM_ID), port.albumCalls)
    }

    @Test
    fun `首载网络失败给出网络文案并保留重试`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.photoOutcome = SessionOutcome.Failed(message = null, cause = ConnectException("拒绝连接"))
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        val failed = subject.uiState as ReaderUiState.Failed
        assertEquals("网络连接失败，请检查网络后重试", failed.message)
        assertFalse(failed.sessionExpired)
    }

    @Test
    fun `首载未知失败回落到章节加载失败`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.photoOutcome = SessionOutcome.Failed(message = "服务器开小差", cause = null)
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        val failed = subject.uiState as ReaderUiState.Failed
        assertEquals("章节加载失败", failed.message)
        assertFalse(failed.sessionExpired)
    }

    @Test
    fun `首载会话失效进入登录引导`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.photoOutcome = SessionOutcome.Expired
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        val failed = subject.uiState as ReaderUiState.Failed
        assertTrue(failed.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, failed.message)
    }

    @Test
    fun `已完成的落盘页优先于网关请求并回填相册号与标题`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.saved = DownloadLedgerEntry(
            record = record(status = DownloadStatus.DONE),
            pagesJson = encodePhotoPages(PHOTO_ID, listOf(page())),
        )
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        val loaded = subject.uiState as ReaderUiState.Loaded
        assertEquals(ALBUM_ID, loaded.photo.albumId)
        assertEquals("第 2 话", loaded.photo.title)
        assertEquals(1, loaded.photo.images.size)
        assertTrue(port.photoCalls.isEmpty())
        assertEquals(listOf(ALBUM_ID), port.albumCalls)
    }

    @Test
    fun `落盘页损坏时回退网关`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.saved = DownloadLedgerEntry(record = record(status = DownloadStatus.DONE), pagesJson = "{ 损坏的页列表")
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        assertEquals(listOf(PHOTO_ID), port.photoCalls)
        val loaded = subject.uiState as ReaderUiState.Loaded
        assertEquals(PHOTO_ID, loaded.photo.photoId)
    }

    @Test
    fun `落盘未完成时回退网关`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.saved = DownloadLedgerEntry(
            record = record(status = DownloadStatus.RUNNING),
            pagesJson = encodePhotoPages(PHOTO_ID, listOf(page())),
        )
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        assertEquals(listOf(PHOTO_ID), port.photoCalls)
        assertTrue(subject.uiState is ReaderUiState.Loaded)
    }

    @Test
    fun `读账本抛错时按无落盘处理走网关`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.savedFailure = IllegalStateException("账本库损坏")
        val subject = state(scope, port)

        scope.launch { subject.start() }
        advanceUntilIdle()

        assertEquals(listOf(PHOTO_ID), port.photoCalls)
        assertTrue(subject.uiState is ReaderUiState.Loaded)
    }

    @Test
    fun `重试先退回加载态再重跑请求成功`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.photoOutcome = SessionOutcome.Failed(message = "服务器开小差", cause = null)
        val subject = state(scope, port)
        scope.launch { subject.start() }
        advanceUntilIdle()
        assertTrue(subject.uiState is ReaderUiState.Failed)

        port.photoOutcome = SessionOutcome.Ok(photo())
        subject.load()

        assertEquals(ReaderUiState.Loading, subject.uiState)
        advanceUntilIdle()
        assertTrue(subject.uiState is ReaderUiState.Loaded)
        assertEquals(listOf(PHOTO_ID, PHOTO_ID), port.photoCalls)
    }

    @Test
    fun `重试仍然失败保留可重试文案`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.photoOutcome = SessionOutcome.Failed(message = "服务器开小差", cause = null)
        val subject = state(scope, port)
        scope.launch { subject.start() }
        advanceUntilIdle()

        subject.load()
        advanceUntilIdle()

        val failed = subject.uiState as ReaderUiState.Failed
        assertEquals("章节加载失败", failed.message)
        assertFalse(failed.sessionExpired)
        assertEquals(2, port.photoCalls.size)
    }

    @Test
    fun `迟到的旧加载不覆盖新状态`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        val gate = port.holdNextPhotoRequest()
        port.queuePhoto(SessionOutcome.Ok(photo(photoId = "photo-stale")))
        port.queuePhoto(SessionOutcome.Ok(photo(photoId = "photo-fresh")))
        val subject = state(scope, port)

        scope.launch { subject.loadContent() }
        advanceUntilIdle()
        scope.launch { subject.loadContent() }
        advanceUntilIdle()
        assertEquals("photo-fresh", (subject.uiState as ReaderUiState.Loaded).photo.photoId)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("photo-fresh", (subject.uiState as ReaderUiState.Loaded).photo.photoId)
    }

    @Test
    fun `内容就绪后写阅读历史`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        val subject = state(scope, port)
        scope.launch { subject.start() }
        advanceUntilIdle()

        subject.recordLoadedHistory()

        assertEquals(listOf(ALBUM_ID), port.coverCalls)
        assertEquals(
            listOf(HistoryEntry(photoId = PHOTO_ID, albumId = ALBUM_ID, title = "第 2 话", coverUrl = COVER_URL, updatedAt = 0L)),
            port.history,
        )
    }

    @Test
    fun `封面取不到时历史照写但封面为空`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.coverOutcome = SessionOutcome.Ok(null)
        val subject = state(scope, port)
        scope.launch { subject.start() }
        advanceUntilIdle()

        subject.recordLoadedHistory()

        assertEquals(1, port.history.size)
        assertNull(port.history.first().coverUrl)
    }

    @Test
    fun `历史写入失败不影响已就绪内容`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        port.historyFailure = IllegalStateException("历史库损坏")
        val subject = state(scope, port)
        scope.launch { subject.start() }
        advanceUntilIdle()

        subject.recordLoadedHistory()

        assertTrue(subject.uiState is ReaderUiState.Loaded)
        assertTrue(port.history.isEmpty())
    }

    @Test
    fun `内容未就绪时不写历史`() = runTest {
        val scope = entryScope()
        val port = FakeReaderEntryPort()
        val subject = state(scope, port)

        subject.recordLoadedHistory()

        assertTrue(port.history.isEmpty())
        assertTrue(port.coverCalls.isEmpty())
    }
}