package com.jm3.core

import com.jm3.core.model.CommentSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentBodyTest {

    @Test
    fun recommendLinkBecomesAlbumSegment() {
        val html = "<p>好看</p>[ 我推荐这本书 JM123456 ]"
        assertEquals(
            listOf(
                CommentSegment.Text("好看"),
                CommentSegment.Album(albumId = "123456", label = "[ 我推荐这本书 JM123456 ]"),
            ),
            commentSegments(content = null, contentHtml = html),
        )
    }

    @Test
    fun htmlBreaksBecomeNewlinesAndEntitiesDecode() {
        val html = "<div>第一行</div><div>第二行&nbsp;&amp;</div>"
        assertEquals(
            listOf(CommentSegment.Text("第一行\n第二行 &")),
            commentSegments(content = null, contentHtml = html),
        )
    }

    @Test
    fun traditionalRecommendLabelIsAlsoSplit() {
        val html = "甲[ 我推薦這本書 JM777 ]乙"
        assertEquals(
            listOf(
                CommentSegment.Text("甲"),
                CommentSegment.Album(albumId = "777", label = "[ 我推薦這本書 JM777 ]"),
                CommentSegment.Text("乙"),
            ),
            commentSegments(content = null, contentHtml = html),
        )
    }

    @Test
    fun multipleRecommendLinksKeepOrder() {
        val html = "[ 我推荐这本书 JM1 ]中间[ 我推荐这本书 JM2 ]"
        assertEquals(
            listOf(
                CommentSegment.Album(albumId = "1", label = "[ 我推荐这本书 JM1 ]"),
                CommentSegment.Text("中间"),
                CommentSegment.Album(albumId = "2", label = "[ 我推荐这本书 JM2 ]"),
            ),
            commentSegments(content = null, contentHtml = html),
        )
    }

    @Test
    fun htmlWinsOverPlainContent() {
        assertEquals(
            listOf(CommentSegment.Text("新的")),
            commentSegments(content = "旧的", contentHtml = "<p>新的</p>"),
        )
    }

    @Test
    fun plainContentIsUsedWhenHtmlMissing() {
        assertEquals(
            listOf(CommentSegment.Text("纯文本\n第二行")),
            commentSegments(content = "纯文本\n第二行", contentHtml = ""),
        )
    }

    @Test
    fun blankContentHasNoSegments() {
        assertEquals(emptyList(), commentSegments(content = "  ", contentHtml = null))
        assertEquals(emptyList(), commentSegments(content = null, contentHtml = null))
    }

    @Test
    fun badgeJsonObjectsYieldContentPaths() {
        val raw = listOf(
            """{"name":"元老","content":"/media/badge/1.png"}""",
            "/media/badge/2.png",
            "",
            """{"name":"没有图"}""",
        )
        assertEquals(listOf("/media/badge/1.png", "/media/badge/2.png"), badgeImagePaths(raw))
    }
}
