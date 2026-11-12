package com.jm3.core

import io.github.jukomu.jmcomic.api.model.JmNovelChapter
import io.github.jukomu.jmcomic.api.model.JmNovelChapterMeta
import io.github.jukomu.jmcomic.api.model.JmNovelDetail
import io.github.jukomu.jmcomic.api.model.JmNovelMeta
import io.github.jukomu.jmcomic.api.model.JmNovelPage
import io.github.jukomu.jmcomic.api.model.JmRelatedNovel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelMappersTest {

    @Test
    fun `JmNovelMeta 映射解析封面与空白可选字段`() {
        val covers = CoverResolver.of("cdn.example")
        val raw = JmNovelMeta("n1", "小说甲", "作者甲", "novel/cover.jpg", true, true, 1700L, " ", "", "终章")
        val got = raw.toNovelMeta(covers)

        assertEquals("n1", got.id)
        assertEquals("小说甲", got.name)
        assertEquals("作者甲", got.author)
        assertEquals("https://cdn.example/novel/cover.jpg", got.coverUrl)
        assertTrue(got.liked)
        assertTrue(got.favorite)
        assertEquals(1700L, got.updateAt)
        assertNull(got.likes)
        assertNull(got.lastChapterIndex)
        assertEquals("终章", got.lastChapterTitle)
    }

    @Test
    fun `JmRelatedNovel 映射使用 chapterUpdateAt 且 liked 为 false`() {
        val raw = JmRelatedNovel("r1", "关联小说", "作者乙", "https://img.example/r1.jpg", 2500L, "2026-01-01", "5", "第五章", "99", null)
        val got = raw.toNovelMeta()

        assertEquals("r1", got.id)
        assertEquals("https://img.example/r1.jpg", got.coverUrl)
        assertFalse(got.liked)
        assertFalse(got.favorite)
        assertEquals(2500L, got.updateAt)
        assertEquals("99", got.likes)
        assertEquals("5", got.lastChapterIndex)
        assertEquals("第五章", got.lastChapterTitle)
    }

    @Test
    fun `JmNovelPage 映射列表为 null 时安全为空表`() {
        val emptyPage = JmNovelPage(10, null).toNovelPage()
        assertEquals(10, emptyPage.total)
        assertTrue(emptyPage.items.isEmpty())
    }

    @Test
    fun `JmNovelChapterMeta 映射标题回退与购买状态`() {
        val needBuy = JmNovelChapterMeta("c1", "  ", "1", null, null, null, null, "1", null, "1", "回退章名", true, false)
            .toNovelChapterMeta()
        assertEquals("c1", needBuy.ncid)
        assertEquals("回退章名", needBuy.title)
        assertEquals("1", needBuy.sort)
        assertTrue(needBuy.isNew)
        assertFalse(needBuy.purchased)
        assertTrue(needBuy.needBuy)

        val purchased = JmNovelChapterMeta("c2", "正式章名", " ", null, null, null, null, "1", null, "2", "忽略名", false, true)
            .toNovelChapterMeta()
        assertEquals("正式章名", purchased.title)
        assertNull(purchased.sort)
        assertFalse(purchased.needBuy)
    }

    @Test
    fun `JmNovelDetail 映射完整字段与空表兜底`() {
        val raw = JmNovelDetail(
            "n9",
            "s9",
            "长篇",
            "/media/n9.jpg",
            999L,
            "简介",
            "1000",
            "88",
            "1",
            "已完结",
            "作者丙",
            listOf("奇幻"),
            true,
            true,
            listOf(JmNovelChapterMeta("c1", "第一章", "1", null, null, null, null, "0", null, "1", "第一章", false, true)),
            listOf(JmRelatedNovel("r2", "外传", "作者丙", null, 100L, null, null, null, null, true)),
            null,
        )
        val got = raw.toNovelDetail(CoverResolver.of("cdn.example"))

        assertEquals("n9", got.id)
        assertEquals("https://cdn.example/media/n9.jpg", got.coverUrl)
        assertTrue(got.isEnd)
        assertEquals("已完结", got.serialStatus)
        assertEquals(listOf("奇幻"), got.tags)
        assertEquals(listOf("c1"), got.chapters.map { it.ncid })
        assertEquals(listOf("r2"), got.related.map { it.id })
    }

    @Test
    fun `JmNovelChapter 映射正文与标题兜底`() {
        val raw = JmNovelChapter(42L, "n9", "备用名", "", "原始内容", "  ", "前章", "12", " ", "5", "2026-01-02", null, true, false, null)
        val got = raw.toNovelChapter()

        assertEquals("42", got.ncid)
        assertEquals("n9", got.novelId)
        assertEquals("备用名", got.title)
        assertEquals("原始内容", got.contentText)
        assertEquals("前章", got.lastChapterTitle)
        assertEquals("12", got.totalFavorites)
        assertNull(got.totalViews)
        assertEquals("5", got.totalLikes)
        assertEquals("2026-01-02", got.addtime)
        assertTrue(got.favorite)
        assertFalse(got.liked)
    }
}
