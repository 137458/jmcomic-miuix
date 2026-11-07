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

/** 按 sortOrder 取首章并截取前 [ALBUM_PREVIEW_PAGE_LIMIT] 页；缺章节或没有图片时返回 null。 */
fun albumPreviewPlan(chapters: List<Chapter>, pages: List<PageImage>): AlbumPreviewPlan? {
    if (chapters.isEmpty() || pages.isEmpty()) return null
    // sortedBy 稳定：sortOrder 相同时保持原列表顺序
    val first = chapters.sortedBy { it.sortOrder }.first()
    return AlbumPreviewPlan(
        chapterId = first.id,
        pages = pages.take(ALBUM_PREVIEW_PAGE_LIMIT),
    )
}
