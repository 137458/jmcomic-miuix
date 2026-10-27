package com.jm3.core

import com.jm3.core.model.FavoriteFolderAction
import io.github.jukomu.jmcomic.api.enums.FavoriteFolderType
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmCategoryMeta
import io.github.jukomu.jmcomic.api.model.JmFavoriteFolderResult
import io.github.jukomu.jmcomic.api.model.JmNotification
import io.github.jukomu.jmcomic.api.model.JmNotificationPage
import io.github.jukomu.jmcomic.api.model.JmTrackingItem
import io.github.jukomu.jmcomic.api.model.JmTrackingPage
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksCategory
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksDetail
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksList
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 库为 Java record，构造器只支持位置实参。
class ParityBatchTest {

    private val cat = JmCategoryMeta("0", "分类")
    private val subCat = JmCategoryMeta("1", "子分类")

    private fun albumMeta(id: String = "1", title: String = "标题"): JmAlbumMeta =
        JmAlbumMeta(id, title, listOf("作者A"), listOf("tag1"), null, "cover.jpg", cat, subCat)

    @Test
    fun 每周必看_期数映射_保留id标题与时间() {
        val list = JmWeeklyPicksList(
            listOf(
                JmWeeklyPicksCategory("51", "第51期", "2026-10-20 ~ 2026-10-26"),
                JmWeeklyPicksCategory("50", null, null),
            ),
            listOf(JmWeeklyPicksType("1", "日漫")),
        )
        val got = list.toWeeklyPicks()
        assertEquals(listOf("51", "50"), got.periods.map { it.id })
        assertEquals(listOf("第51期", ""), got.periods.map { it.title })
        assertEquals(listOf("2026-10-20 ~ 2026-10-26", ""), got.periods.map { it.time })
    }

    @Test
    fun 每周必看_期数为null时返回空表() {
        assertTrue(JmWeeklyPicksList(null, null).toWeeklyPicks().periods.isEmpty())
    }

    @Test
    fun 每周必看_详情映射_封面按解析器构造() {
        val detail = JmWeeklyPicksDetail(2, listOf(albumMeta(id = "9"), albumMeta(id = "10", title = "另一本")))
        val got = detail.toWeeklyPicksDetail { meta -> "https://img.example/${meta.id()}.jpg" }
        assertEquals(2, got.total)
        assertEquals(listOf("https://img.example/9.jpg", "https://img.example/10.jpg"), got.items.map { it.coverUrl })
        assertEquals(listOf("标题", "另一本"), got.items.map { it.title })
    }

    @Test
    fun 每周必看_详情列表为null时为空() {
        val got = JmWeeklyPicksDetail(0, null).toWeeklyPicksDetail()
        assertEquals(0, got.total)
        assertTrue(got.items.isEmpty())
    }

    @Test
    fun 通知_映射_透传已读态与类型() {
        val page = JmNotificationPage(
            3,
            listOf(
                JmNotification("1", "新章节", "《标题》更新了", "2026-10-27", true, "comic_follow"),
                JmNotification("2", null, null, null, false, null),
            ),
        )
        val got = page.toNotificationPage()
        assertEquals(3, got.total)
        assertEquals(listOf("1", "2"), got.items.map { it.id })
        assertEquals(listOf("新章节", ""), got.items.map { it.title })
        assertEquals(listOf("《标题》更新了", ""), got.items.map { it.content })
        assertEquals(listOf(true, false), got.items.map { it.read })
        assertEquals(listOf("comic_follow", null), got.items.map { it.type })
    }

    @Test
    fun 通知_列表为null时为空() {
        assertTrue(JmNotificationPage(0, null).toNotificationPage().items.isEmpty())
    }

    @Test
    fun 连载追踪_映射_解析总数并过滤空白封面() {
        val page = JmTrackingPage(
            "2",
            listOf(
                JmTrackingItem("1", "标题", "https://img.example/1.jpg", "1760000000"),
                JmTrackingItem("2", "", " ", ""),
            ),
        )
        val got = page.toTrackingPage()
        assertEquals(2, got.total)
        assertEquals("https://img.example/1.jpg", got.items[0].coverUrl)
        assertEquals("1760000000", got.items[0].updateAt)
        assertEquals("", got.items[1].name)
        assertNull(got.items[1].coverUrl)
        assertNull(got.items[1].updateAt)
    }

    @Test
    fun 连载追踪_总数不可解析时按当前页条数() {
        val page = JmTrackingPage("abc", listOf(JmTrackingItem("1", "标题", null, null)))
        assertEquals(1, page.toTrackingPage().total)
        assertEquals(0, JmTrackingPage(null, null).toTrackingPage().total)
    }

    @Test
    fun 收藏夹操作_ok与失败消息() {
        val ok = JmFavoriteFolderResult("ok", null).toFolderOperation()
        assertTrue(ok.ok)
        assertNull(ok.message)

        val failed = JmFavoriteFolderResult("error", "名称重复").toFolderOperation()
        assertFalse(failed.ok)
        assertEquals("名称重复", failed.message)

        assertNull(JmFavoriteFolderResult("ok", " ").toFolderOperation().message)
    }

    @Test
    fun 收藏夹动作_枚举值与库一致() {
        val expected = mapOf(
            FavoriteFolderAction.ADD to FavoriteFolderType.ADD,
            FavoriteFolderAction.EDIT to FavoriteFolderType.EDIT,
            FavoriteFolderAction.MOVE to FavoriteFolderType.MOVE,
            FavoriteFolderAction.DELETE to FavoriteFolderType.DELETE,
        )
        expected.forEach { (action, type) ->
            assertEquals(type, action.toFavoriteFolderType())
            assertEquals(type.value, action.apiValue)
        }
    }
}
