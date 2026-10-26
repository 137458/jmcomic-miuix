package com.jm3.core

import com.jm3.core.model.PageImage
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmCategoryMeta
import io.github.jukomu.jmcomic.api.model.JmImage
import io.github.jukomu.jmcomic.api.model.JmPhoto
import io.github.jukomu.jmcomic.api.model.JmPhotoMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 库为 Java record，构造器只支持位置实参。
class MappersTest {

    private val cat = JmCategoryMeta("0", "分类")
    private val subCat = JmCategoryMeta("1", "子分类")

    private fun albumMeta(
        id: String = "1",
        title: String? = "标题",
        authors: List<String>? = listOf("作者A"),
        tags: List<String>? = listOf("tag1"),
        description: String? = null,
        image: String? = "cover.jpg",
    ): JmAlbumMeta = JmAlbumMeta(id, title, authors, tags, description, image, cat, subCat)

    private fun album(
        id: String = "9",
        likes: String? = "1.2K",
        views: String? = "3.4K",
        photoMetas: List<JmPhotoMeta> = listOf(JmPhotoMeta("p1", "c1", 0), JmPhotoMeta("p2", "c2", 1)),
    ): JmAlbum = JmAlbum(
        id, "T", "D", "220980", "1700000000", 2, likes, views, 5, "cover.jpg",
        cat, subCat, listOf("A"), emptyList(), emptyList(), listOf("t"), emptyList(), photoMetas, "",
        false, false, false, emptyList(), "", "",
    )

    @Test
    fun 相册元数据_映射_无封面时为null() {
        val got: SearchResult = albumMeta().toSearchResult(coverUrl = null)
        assertEquals("1", got.id)
        assertEquals("标题", got.title)
        assertEquals(listOf("作者A"), got.authors)
        assertEquals(listOf("tag1"), got.tags)
        assertNull(got.coverUrl)
    }

    @Test
    fun 章节元数据_映射_保留顺序字段() {
        val chapter = JmPhotoMeta("77", "第1话", 2).toChapter()
        assertEquals("77", chapter.id)
        assertEquals("第1话", chapter.title)
        assertEquals(2, chapter.sortOrder)
    }

    @Test
    fun 相册详情_映射_含章节列表() {
        val got = album().toAlbumDetail(coverUrl = "http://x/c.jpg")
        assertEquals("9", got.id)
        assertEquals("1.2K", got.likes)
        assertEquals("3.4K", got.views)
        assertEquals("http://x/c.jpg", got.coverUrl)
        assertEquals(listOf("p1", "p2"), got.chapters.map { it.id })
        assertEquals(listOf("c1", "c2"), got.chapters.map { it.title })
    }

    @Test
    fun 章节_映射_图片清单带索引与分割号() {
        val photo = JmPhoto(
            "p1", "第一章", "9", "220980", 0, null, null,
            listOf(
                JmImage("p1", "220980", "00001.jpg", "http://x/1.jpg", "", 0),
                JmImage("p1", "220980", "00002.jpg", "http://x/2.jpg", "", 1),
            ),
            true,
        )
        val got = photo.toPhotoPages()
        assertEquals("p1", got.photoId)
        assertEquals(2, got.images.size)
        assertEquals(
            listOf(
                PageImage("p1", "220980", 0, "00001.jpg", "http://x/1.jpg"),
                PageImage("p1", "220980", 1, "00002.jpg", "http://x/2.jpg"),
            ),
            got.images,
        )
    }

    @Test
    fun 章节空图_映射_空列表不崩溃() {
        val photo = JmPhoto("p2", "t", "9", "1", 0, null, null, emptyList(), true)
        assertEquals(0, photo.toPhotoPages().images.size)
    }
}
