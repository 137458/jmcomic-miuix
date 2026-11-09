package com.jm3.core

import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 详情页预览的选页规则：首章按 sortOrder 取，页数截到上限，
 * 页必须属于首章（photoId == chapter.id），任何缺失或错章都不产出预览。
 */
class AlbumPreviewPlanTest {

    private fun chapter(id: String, order: Int) = Chapter(id = id, title = id, sortOrder = order)

    private fun page(index: Int, photoId: String) = PageImage(
        photoId = photoId,
        scrambleId = "s",
        index = index,
        filename = "$index.jpg",
        url = "https://example.com/$index",
    )

    private fun pages(count: Int, photoId: String = "c1") = (0 until count).map { page(it, photoId) }

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
        assertEquals("first", albumPreviewPlan(chapters, pages(6, photoId = "first"))?.chapterId)
    }

    @Test
    fun sortOrder相同时保持章节原顺序() {
        val chapters = listOf(chapter("a", 1), chapter("b", 1))
        assertEquals("a", albumPreviewPlan(chapters, pages(1, photoId = "a"))?.chapterId)
    }

    @Test
    fun 页属于首章时产出预览() {
        val chapters = listOf(chapter("late", 9), chapter("c1", 1))
        val plan = albumPreviewPlan(chapters, pages(2, photoId = "c1"))
        assertEquals("c1", plan?.chapterId)
        assertEquals(listOf(0, 1), plan?.pages?.map { it.index })
    }

    @Test
    fun 页属于其它章节时没有预览() {
        val chapters = listOf(chapter("c1", 1), chapter("c2", 2))
        // 章节列表已切到新首章，图片还是上一章取回来的：宁可整块不展示
        assertNull(albumPreviewPlan(chapters, pages(3, photoId = "c2")))
        // 混进别的章的页同样不拼
        assertNull(
            albumPreviewPlan(
                listOf(chapter("c1", 1)),
                listOf(page(0, "c1"), page(1, "c2")),
            ),
        )
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
