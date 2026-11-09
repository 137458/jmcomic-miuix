package com.jm3.core

import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage

/** 详情页预览最多展示的页数。 */
const val ALBUM_PREVIEW_PAGE_LIMIT = 6

/** 详情页预览：首章 id 与该章要展示的前几页。 */
data class AlbumPreviewPlan(
    val chapterId: String,
    val pages: List<PageImage>,
)

/**
 * 按 sortOrder 取首章并截取前 [ALBUM_PREVIEW_PAGE_LIMIT] 页；缺章节、没有图片、
 * 或页不属于首章（章节列表先到、图片还是上一章的旧页）时返回 null。
 *
 * 页的归属只能靠 photoId 判断：PageImage 没有章节字段，但 chapter.id 与 photoId 同一命名空间
 * ——详情页就是拿首章 id 调 gateway.photo 取这一章的页。
 */
fun albumPreviewPlan(chapters: List<Chapter>, pages: List<PageImage>): AlbumPreviewPlan? {
    if (chapters.isEmpty() || pages.isEmpty()) return null
    // sortedBy 稳定：sortOrder 相同时保持原列表顺序
    val first = chapters.sortedBy { it.sortOrder }.first()
    // 旧页拼到首章下会把别的章当成首章预览，宁可不展示
    if (pages.any { it.photoId != first.id }) return null
    return AlbumPreviewPlan(
        chapterId = first.id,
        pages = pages.take(ALBUM_PREVIEW_PAGE_LIMIT),
    )
}
