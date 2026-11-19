package com.jm3.app.miuix.ui.album

import com.jm3.app.miuix.ui.AlbumDetailUiSnapshot
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

/**
 * 相册详情页共享骨架的派生状态与动作反馈。
 *
 * 页面级状态机（加载四态、收藏/喜欢/追踪、评论分页）仍由 AlbumDetailStateHolder 持有并由
 * AlbumDetailStateHolderTest 覆盖；这里测的是骨架自己判断的那部分：页首标题与续读位置怎么选、
 * 预览块什么时候取首章、取得什么、批量下载怎么汇总入队结果、收藏夹面板什么时候重开。
 */
class AlbumDetailScaffoldTest {

    @Test
    fun 初始态_未登录且详情未就绪时不取预览也不给续读() {
        assertNull(albumPreviewChapterId(loggedIn = false, detail = null))
        assertNull(albumResumePhotoId(latestPhotoId = null, detail = null))
        assertEquals(ALBUM_DETAIL_TITLE_FALLBACK, albumDetailTitle(detailTitle = null, fallbackTitle = null))
        assertEquals("详情", ALBUM_DETAIL_TITLE_FALLBACK)
    }

    @Test
    fun 成功装载_页首标题优先用详情标题再回落转场标题() {
        assertEquals("作品标题", albumDetailTitle(detailTitle = "作品标题", fallbackTitle = "转场标题"))
        assertEquals("转场标题", albumDetailTitle(detailTitle = null, fallbackTitle = "转场标题"))
        assertEquals("转场标题", albumDetailTitle(detailTitle = "  ", fallbackTitle = "转场标题"))
        assertEquals(ALBUM_DETAIL_TITLE_FALLBACK, albumDetailTitle(detailTitle = "", fallbackTitle = " "))
    }

    @Test
    fun 成功装载_续读位置只认当前章节树里的那一话() {
        val detail = albumDetail(chapters = listOf(chapter("c1"), chapter("c2")))

        assertEquals("c2", albumResumePhotoId(latestPhotoId = "c2", detail = detail))
        assertNull(albumResumePhotoId(latestPhotoId = "c9", detail = detail))
        assertEquals("c1", albumPreviewChapterId(loggedIn = true, detail = detail))
    }

    @Test
    fun 未登录或无章节时预览块整块隐藏() {
        val detail = albumDetail(chapters = listOf(chapter("c1")))

        assertNull(albumPreviewChapterId(loggedIn = false, detail = detail))
        assertNull(albumPreviewChapterId(loggedIn = true, detail = albumDetail(chapters = emptyList())))
        assertNull(albumPreviewChapterId(loggedIn = true, detail = null))
    }

    @Test
    fun 预览取图成功时给出首章页图() {
        val pages = listOf(
            PageImage(photoId = "c1", scrambleId = "s1", index = 1, filename = "1.jpg", url = "u1"),
            PageImage(photoId = "c1", scrambleId = "s1", index = 2, filename = "2.jpg", url = "u2"),
        )
        val outcome = SessionOutcome.Ok(PhotoPages(photoId = "c1", albumId = "a1", title = "作品标题", images = pages))

        assertEquals(pages, albumPreviewPages(outcome))
        assertEquals(listOf("u1", "u2"), albumPreviewPages(outcome).map { it.url })
    }

    @Test
    fun 失败态_预览取图失败与会话失效都当空() {
        assertEquals(emptyList(), albumPreviewPages(SessionOutcome.Failed("读取失败")))
        assertEquals(emptyList(), albumPreviewPages(SessionOutcome.Expired))
    }

    @Test
    fun 分享_域名就绪时标题后附作品链接() {
        assertEquals("https://jm.example/album/a1", albumShareLink(host = "jm.example", albumId = "a1"))
        assertEquals(
            "作品标题\nhttps://jm.example/album/a1",
            albumShareText(title = "作品标题", host = "jm.example", albumId = "a1"),
        )
    }

    @Test
    fun 分享_域名未就绪时只分享标题() {
        assertNull(albumShareLink(host = null, albumId = "a1"))
        assertNull(albumShareLink(host = "   ", albumId = "a1"))
        assertEquals("作品标题", albumShareText(title = "作品标题", host = null, albumId = "a1"))
        assertEquals("作品标题", albumShareText(title = "作品标题", host = " ", albumId = "a1"))
    }

    @Test
    fun 批量下载_空章节列表不产生提示() {
        val calls = mutableListOf<String>()

        val outcome = albumDownloadAll(albumId = "a1", chapters = emptyList()) { photoId, _, _ ->
            calls += photoId
            true
        }

        assertNull(outcome)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun 批量下载_全部入队时报告新增章节数() {
        val calls = mutableListOf<Triple<String, String, String>>()

        val outcome = albumDownloadAll(
            albumId = "a1",
            chapters = listOf(chapter("c1"), chapter("c2", title = "第二话"), chapter("c3", title = " ")),
        ) { photoId, albumId, title ->
            calls += Triple(photoId, albumId, title)
            true
        }

        assertEquals(AlbumDownloadOutcome(accepted = 3, message = "已加入下载队列（3 个章节）"), outcome)
        assertEquals(
            listOf(
                Triple("c1", "a1", "第1话"),
                Triple("c2", "a1", "第二话"),
                Triple("c3", "a1", "第3话"),
            ),
            calls,
        )
    }

    @Test
    fun 批量下载_全部已在队列时如实提示无新增() {
        val calls = mutableListOf<String>()

        val outcome = albumDownloadAll(albumId = "a1", chapters = listOf(chapter("c1"), chapter("c2"))) { photoId, _, _ ->
            calls += photoId
            false
        }

        assertEquals(AlbumDownloadOutcome(accepted = 0, message = "这些章节已在下载队列或已下载"), outcome)
        assertEquals(listOf("c1", "c2"), calls)
    }

    @Test
    fun 批量下载_部分入队只报实际新增数() {
        val outcome = albumDownloadAll(albumId = "a1", chapters = listOf(chapter("c1"), chapter("c2"))) { photoId, _, _ ->
            photoId == "c1"
        }

        assertEquals(AlbumDownloadOutcome(accepted = 1, message = "已加入下载队列（1 个章节）"), outcome)
    }

    @Test
    fun 单话下载_入队成功与已存在两种反馈() {
        assertEquals("已加入下载队列", albumChapterDownloadMessage(accepted = true))
        assertEquals("该章节已在下载队列或已下载", albumChapterDownloadMessage(accepted = false))
    }

    @Test
    fun 收藏夹恢复_快照开着面板且收藏夹未加载时重开() {
        assertTrue(shouldRestoreFolderSheet(AlbumDetailUiSnapshot(showFolderSheet = true), folders = emptyMap()))
    }

    @Test
    fun 收藏夹恢复_未开面板或收藏夹已加载时不重开() {
        assertFalse(shouldRestoreFolderSheet(AlbumDetailUiSnapshot(), folders = emptyMap()))
        assertFalse(
            shouldRestoreFolderSheet(
                AlbumDetailUiSnapshot(showFolderSheet = true),
                folders = mapOf("f1" to "默认收藏夹"),
            ),
        )
    }
}

/** 构造详情：只有标题与章节树是被测逻辑关心的字段。 */
private fun albumDetail(
    title: String = "作品标题",
    chapters: List<Chapter> = listOf(chapter("c1"), chapter("c2")),
): AlbumDetail = AlbumDetail(
    id = "a1",
    title = title,
    description = "",
    authors = emptyList(),
    tags = emptyList(),
    works = emptyList(),
    actors = emptyList(),
    likes = null,
    views = null,
    coverUrl = null,
    chapters = chapters,
    favorited = false,
)

private fun chapter(id: String, title: String = ""): Chapter = Chapter(id = id, title = title, sortOrder = 0)
