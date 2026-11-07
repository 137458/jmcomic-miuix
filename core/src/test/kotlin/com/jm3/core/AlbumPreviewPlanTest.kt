package com.jm3.core

import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 详情页预览的选页规则：首章按 sortOrder 取，页数截到上限，任何缺失都不产出预览。
 */
class AlbumPreviewPlanTest {

    private fun chapter(id: String, order: Int) = Chapter(id = id, title = id, sortOrder = order)

    private fun page(index: Int) = PageImage(
        photoId = "photo-1",
        scrambleId = "s",
        index = index,
        filename = "$index.jpg",
        url = "https://example.com/$index",
    )

    private fun pages(count: Int) = (0 until count).map(::page)

    @Test
    fun 无章节时没有预览() {
        assertNull(albumPreviewPlan(chapters = emptyList(), pages = pages(6)))
    }

    @Test
    fun 章节没有图片时没有预览() {
        assertNull(albumPreviewPlan(chapters = listOf(chapter("c1", 1)), pages = emptyList()))
    }

    @Test
    fun 按sortOrder取首章() {
        val chapters = listOf(chapter("late", 9), chapter("first", 1), chapter("mid", 5))
        assertEquals("first", albumPreviewPlan(chapters, pages(6))?.chapterId)
    }

    @Test
    fun sortOrder相同时保持章节原顺序() {
        val chapters = listOf(chapter("a", 1), chapter("b", 1))
        assertEquals("a", albumPreviewPlan(chapters, pages(1))?.chapterId)
    }

    @Test
    fun 最多只取前六页且顺序不变() {
        val plan = albumPreviewPlan(listOf(chapter("c1", 1)), pages(9))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), plan?.pages?.map { it.index })
    }

    @Test
    fun 页数不足六页时全部返回() {
        val plan = albumPreviewPlan(listOf(chapter("c1", 1)), pages(3))
        assertEquals(listOf(0, 1, 2), plan?.pages?.map { it.index })
    }
}
