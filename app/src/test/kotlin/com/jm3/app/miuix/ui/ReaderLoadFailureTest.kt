package com.jm3.app.miuix.ui

import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderLoadFailureTest {

    @Test
    fun 成功结局不产生失败态() {
        // 成功分支由调用方继续走内容，不该在这里被翻译成失败
        assertNull(readerLoadFailure(SessionOutcome.Ok(Unit)))
    }

    @Test
    fun 失效结局进入登录引导且不可重试() {
        val failed = readerLoadFailure(SessionOutcome.Expired)

        assertTrue(failed!!.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, failed.message)
    }

    @Test
    fun 普通失败给出归类文案且保留重试() {
        val failed = readerLoadFailure(SessionOutcome.Failed("boom"))

        assertFalse(failed!!.sessionExpired)
        assertEquals("章节加载失败", failed.message)
    }

    @Test
    fun 普通失败没有原因时回落到章节加载失败() {
        val failed = readerLoadFailure(SessionOutcome.Failed(null))

        assertEquals("章节加载失败", failed!!.message)
    }

    @Test
    fun 离线章节回填相册号和标题并按排序号排列章节() {
        val offlinePhoto = PhotoPages(
            photoId = "p2",
            albumId = "",
            title = "",
            images = listOf(PageImage(photoId = "p2", scrambleId = "0", index = 0, filename = "00001.jpg", url = "")),
        )
        val record = DownloadRecord(
            photoId = "p2",
            albumId = "album-42",
            title = "第 2 话",
            total = 1,
            donePages = 1,
            status = DownloadStatus.DONE,
            updatedAt = 100L,
        )
        val detail = AlbumDetail(
            id = "album-42",
            title = "作品主标题",
            description = "",
            authors = emptyList(),
            tags = emptyList(),
            works = emptyList(),
            actors = emptyList(),
            likes = null,
            views = null,
            coverUrl = null,
            chapters = listOf(
                Chapter(id = "p3", title = "第 3 话", sortOrder = 3),
                Chapter(id = "p1", title = "第 1 话", sortOrder = 1),
                Chapter(id = "p2", title = "第 2 话", sortOrder = 2),
            ),
            favorited = true,
            liked = true,
        )

        val loaded = resolveReaderLoadedState(offlinePhoto, record, detail)
        assertEquals("album-42", loaded.photo.albumId)
        assertEquals("第 2 话", loaded.photo.title)
        assertEquals(listOf("p1", "p2", "p3"), loaded.chapters.map { it.id })
        assertEquals(1, loaded.chapterIndex)

        val offlineNoRecordTitle = resolveReaderLoadedState(
            offlinePhoto,
            record.copy(title = ""),
            detail,
        )
        assertEquals("album-42", offlineNoRecordTitle.photo.albumId)
        assertEquals("作品主标题", offlineNoRecordTitle.photo.title)
    }
}
