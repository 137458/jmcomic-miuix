package com.jm3.core

import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskType
import com.jm3.core.settings.InMemorySettingsRepository
import com.jm3.core.settings.PaginationMode
import io.github.jukomu.jmcomic.api.model.JmCategoryBlock
import io.github.jukomu.jmcomic.api.model.JmCategoryList
import io.github.jukomu.jmcomic.api.model.JmCategoryListItem
import io.github.jukomu.jmcomic.api.model.JmSubCategoryItem
import io.github.jukomu.jmcomic.api.model.JmTagFavorite
import io.github.jukomu.jmcomic.api.model.JmTaskItem
import io.github.jukomu.jmcomic.api.model.JmTaskList
import io.github.jukomu.jmcomic.api.model.JmUserProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** M01 基础设施：分类树/任务/资料/标签映射与搜索历史契约。 */
class LoopCoreTest {

    @Test
    fun 分类树_映射_子分类与标签区块() {
        val item = JmCategoryListItem(
            "1", "同人", "doujin", "slug", "120",
            listOf(JmSubCategoryItem("11", "Cosplay", "cosplay")),
        )
        val tree = JmCategoryList(listOf(item), listOf(JmCategoryBlock("主题A漫", listOf("全彩", "汉化"))))
            .toCategoryTree()

        assertEquals(1, tree.categories.size)
        assertEquals("同人", tree.categories.first().name)
        assertEquals(120, tree.categories.first().totalAlbums)
        assertEquals(listOf("Cosplay"), tree.categories.first().children.map { it.name })
        assertEquals(listOf("全彩", "汉化"), tree.blocks.first().tags)
    }

    @Test
    fun 分类树_总数非数字_按零() {
        val item = JmCategoryListItem("1", "分类", "slug", null, "N/A", null)
        assertEquals(0, JmCategoryList(listOf(item), null).toCategoryTree().categories.first().totalAlbums)
    }

    @Test
    fun 任务列表_映射_完成态与金币() {
        val done = JmTaskItem("1", "首次签到", "title", "签到一次", "10", "{}", null, null, null, null, true)
        val todo = JmTaskItem("2", "收藏三本", "badge", "收藏三本", "abc", null, null, null, null, null, false)
        val page = JmTaskList("ok", "ok", listOf(done, todo)).toTaskPage()

        assertEquals(2, page.items.size)
        assertEquals(10, page.items[0].coin)
        assertEquals(true, page.items[0].done)
        assertEquals(0, page.items[1].coin)
        assertNull(page.message)
    }

    @Test
    fun 任务列表_失败状态_透传消息() {
        val page = JmTaskList("登录已失效", "fail", null).toTaskPage()
        assertEquals("登录已失效", page.message)
        assertEquals(emptyList(), page.items)
    }

    @Test
    fun 任务枚举_值与原版一致() {
        assertEquals("title", TaskType.TITLE.apiValue)
        assertEquals("badge", TaskType.BADGE.apiValue)
        assertEquals("my", TaskFilter.MY.apiValue)
        assertEquals("all", TaskFilter.ALL.apiValue)
    }

    @Test
    fun 个人资料_映射_可编辑字段覆盖昵称与关于我() {
        val profile = JmUserProfile(
            "u1", "a@b.c", "鲸娘", "禁", "漫", "2000-01-01", "Single", "Guys",
            "https://jm.example", "台北", "台北", "TW", "画师", "JM", "某大学",
            "你好", "info", "全彩", "温柔", "耳", "猫", "吵",
        ).toUserProfile()

        assertEquals("鲸娘", profile.nickname)
        assertEquals("2000-01-01", profile.birthday)
        assertEquals("你好", profile.editableFields()["about_me"])
        assertEquals("鲸娘", profile.editableFields()["nickname"])
    }

    @Test
    fun 收藏标签_映射_空时间降级() {
        assertEquals("全彩", JmTagFavorite("全彩", "33 秒 ago").toTagFavorite().tag)
        assertNull(JmTagFavorite("汉化", "").toTagFavorite().updatedAt)
    }

    @Test
    fun 搜索历史_去重前移并截断() = runTest {
        val repo = InMemorySettingsRepository()
        repeat(12) { repo.addSearchHistory("词$it") }
        assertEquals(10, repo.searchHistory().size)
        assertEquals("词11", repo.searchHistory().first())

        repo.addSearchHistory("词5")
        assertEquals("词5", repo.searchHistory().first())
        assertEquals(10, repo.searchHistory().size)

        repo.removeSearchHistory("词5")
        assertEquals(9, repo.searchHistory().size)

        repo.clearSearchHistory()
        assertEquals(emptyList(), repo.searchHistory())
    }

    @Test
    fun 分页模式_默认无限滚动且可切换() = runTest {
        val repo = InMemorySettingsRepository()
        assertEquals(PaginationMode.INFINITE, repo.paginationMode())
        repo.setPaginationMode(PaginationMode.CLICK)
        assertEquals(PaginationMode.CLICK, repo.paginationMode())
    }

    @Test
    fun 阅读器偏好_默认直向且提示与追更静默可持久() = runTest {
        val repo = InMemorySettingsRepository()
        assertEquals(false, repo.readerHorizontal())
        assertEquals(false, repo.volumeKeyHintShown())
        assertEquals(0L, repo.trackPromptMuteUntil())

        repo.setReaderHorizontal(true)
        repo.markVolumeKeyHintShown()
        repo.setTrackPromptMuteUntil(123L)

        assertEquals(true, repo.readerHorizontal())
        assertEquals(true, repo.volumeKeyHintShown())
        assertEquals(123L, repo.trackPromptMuteUntil())
    }
}
