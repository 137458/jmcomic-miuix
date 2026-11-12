package com.jm3.core

import io.github.jukomu.jmcomic.api.model.JmCreatorAuthorWorksPage
import io.github.jukomu.jmcomic.api.model.JmCreatorMeta
import io.github.jukomu.jmcomic.api.model.JmCreatorPage
import io.github.jukomu.jmcomic.api.model.JmCreatorRelatedWork
import io.github.jukomu.jmcomic.api.model.JmCreatorSponsor
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkDetail
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkInfo
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkMeta
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreatorMappersTest {

    private val covers = CoverResolver.of("cdn.example")

    @Test
    fun `creatorFilters 对空输入与重复空白项做去重过滤`() {
        assertTrue(creatorFilters(null).isEmpty)

        val filters = creatorFilters(
            mapOf(
                "language" to listOf("中文", "", "  ", "中文", "日文"),
                "source" to listOf("Pixiv", null, "Patreon", "Pixiv"),
            ),
        )
        assertEquals(listOf("中文", "日文"), filters.languages)
        assertEquals(listOf("Pixiv", "Patreon"), filters.sources)
    }

    @Test
    fun `JmCreatorMeta 与 JmCreatorPage 映射头像背景及空列表兜底`() {
        val page = JmCreatorPage(
            5,
            listOf(JmCreatorMeta("cr1", "画师A", "/bg/1.jpg", " ", "/avatar/1.png")),
        ).toCreatorPage(covers)

        assertEquals(5, page.total)
        val item = page.items.single()
        assertEquals("cr1", item.id)
        assertEquals("画师A", item.name)
        assertEquals("https://cdn.example/avatar/1.png", item.avatarUrl)
        assertEquals("https://cdn.example/bg/1.jpg", item.backgroundUrl)
        assertNull(item.updateText)

        assertTrue(JmCreatorPage(0, null).toCreatorPage().items.isEmpty())
    }

    @Test
    fun `JmCreatorWorkMeta 与 JmCreatorWorkPage 映射字段及筛选项`() {
        val work = JmCreatorWorkMeta("w1", "作品1", "works/1.jpg", "Fanbox", "2026-01-01", "画师A", "cr1")
        val page = JmCreatorWorkPage(
            12,
            listOf(work),
            mapOf("language" to listOf("中文"), "source" to listOf("Fanbox")),
        ).toCreatorWorkPage(covers)

        assertEquals(12, page.total)
        assertEquals(listOf("中文"), page.filters.languages)
        assertEquals(listOf("Fanbox"), page.filters.sources)
        val mapped = page.items.single()
        assertEquals("https://cdn.example/works/1.jpg", mapped.coverUrl)
        assertEquals("Fanbox", mapped.platform)
        assertEquals("cr1", mapped.authorId)
    }

    @Test
    fun `JmCreatorRelatedWork 与 JmCreatorSponsor 与 JmCreatorAuthorWorksPage 映射`() {
        val related = JmCreatorRelatedWork("rw1", "r/1.jpg", "关联作品", "2026-02-01", "Patreon")
        val sponsor = JmCreatorSponsor("https://patreon.com/a", "Patreon")
        val authorWorks = JmCreatorAuthorWorksPage(
            "标题",
            "画师B",
            "2026-02-01",
            "/a/b.png",
            "/bg/b.jpg",
            listOf(sponsor),
            listOf(related),
            mapOf("language" to listOf("日文")),
        ).toCreatorAuthorWorks(covers)

        assertEquals("画师B", authorWorks.authorName)
        assertEquals("https://cdn.example/a/b.png", authorWorks.avatarUrl)
        assertEquals("https://cdn.example/bg/b.jpg", authorWorks.backgroundUrl)
        assertEquals("Patreon", authorWorks.sponsors.single().name)
        assertEquals("https://patreon.com/a", authorWorks.sponsors.single().url)
        assertEquals("https://cdn.example/r/1.jpg", authorWorks.works.single().coverUrl)
        assertEquals(listOf("日文"), authorWorks.filters.languages)
    }

    @Test
    fun `toCreatorWork 合并 info 与 detail 并按页码排序图片`() {
        val info = JmCreatorWorkInfo(
            null,
            "画师C",
            "",
            listOf(JmCreatorRelatedWork("rw2", "r2.jpg", "推荐", null, null)),
        )
        val detail = JmCreatorWorkDetail(
            "w9",
            "详情标题",
            2,
            listOf(
                JmCreatorWorkDetail.Image(2, "/p/2.jpg"),
                JmCreatorWorkDetail.Image(1, "/p/1.jpg"),
                JmCreatorWorkDetail.Image(3, "   "),
            ),
            "<p>正文描述</p>",
            100L,
            "2026-03-01",
        )
        val got = toCreatorWork(info, detail, covers)

        assertEquals("w9", got.id)
        assertEquals("详情标题", got.title)
        assertEquals("画师C", got.authorName)
        assertEquals("2026-03-01", got.workDate)
        assertEquals("正文描述", got.contentText)
        assertEquals(
            listOf("https://cdn.example/p/1.jpg", "https://cdn.example/p/2.jpg"),
            got.images,
        )
        assertEquals(listOf("rw2"), got.related.map { it.id })
    }
}
