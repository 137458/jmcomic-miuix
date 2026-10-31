package com.jm3.core

import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.PageImage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import io.github.jukomu.jmcomic.api.enums.SearchMainTag
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmCategoryMeta
import io.github.jukomu.jmcomic.api.model.JmComment
import io.github.jukomu.jmcomic.api.model.JmCommentList
import io.github.jukomu.jmcomic.api.model.JmImage
import io.github.jukomu.jmcomic.api.model.JmPhoto
import io.github.jukomu.jmcomic.api.model.JmPhotoMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        favorite: Boolean = false,
        photoMetas: List<JmPhotoMeta> = listOf(JmPhotoMeta("p1", "c1", 0), JmPhotoMeta("p2", "c2", 1)),
        pageCount: Int = 24,
        commentCount: Int = 5,
        liked: Boolean = false,
        related: List<JmAlbumMeta> = emptyList(),
    ): JmAlbum = JmAlbum(
        id, "T", "D", "220980", "1700000000", pageCount, likes, views, commentCount, "cover.jpg",
        cat, subCat, listOf("A"), emptyList(), emptyList(), listOf("t"), related, photoMetas, "",
        favorite, liked, false, emptyList(), "", "",
    )

    /** JmComment 记录组件顺序：id,userId,username,nickname,content,contentHtml,postDate,photo,expinfo,expinfoData,aid,bid,nid,ncid,name,likes,gender,updateAt,parentCommentId,spoiler,replys,voteUp,voteDown */
    private fun comment(
        id: String,
        username: String? = null,
        nickname: String? = null,
        content: String? = null,
        postDate: String? = null,
        likes: Int = 0,
        name: String? = null,
        aid: String? = null,
        replys: List<JmComment> = emptyList(),
    ): JmComment = JmComment(
        id, "u", username, nickname, content, null, postDate, null, null, null,
        aid, null, null, null, name, likes, null, null, null, false, replys, 0, 0,
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
    fun 相册详情_映射_已收藏时favorited为true() {
        val got = album(favorite = true).toAlbumDetail()
        assertTrue(got.favorited)
    }

    @Test
    fun 相册详情_映射_未收藏时favorited为false() {
        val got = album(favorite = false).toAlbumDetail()
        assertFalse(got.favorited)
    }

    @Test
    fun 相册详情_映射_页数评论数点赞与相关作品() {
        val related = albumMeta(id = "77", title = "相关")
        val got = album(
            pageCount = 24,
            commentCount = 5,
            liked = true,
            related = listOf(related),
        ).toAlbumDetail(relatedCoverUrl = { meta -> "https://img.example/media/albums/${meta.id()}_3x4.jpg" })

        assertEquals(24, got.pageCount)
        assertEquals(5, got.commentCount)
        assertTrue(got.liked)
        assertEquals(listOf("77"), got.related.map { it.id })
        assertEquals("https://img.example/media/albums/77_3x4.jpg", got.related.single().coverUrl)
    }

    @Test
    fun 相册详情_映射_无相关作品时为空表() {
        assertEquals(emptyList(), album(related = emptyList()).toAlbumDetail().related)
    }

    @Test
    fun 相册详情_映射_过滤空白作者与标签与作品() {
        val raw = JmAlbum(
            "100", "  ", "D", "220980", "1700000000", 24, "1", "2", 3, "cover.jpg",
            cat, subCat, listOf("", "  ", "真实作者"), listOf("  ", "有效作品"), emptyList(), listOf(" ", "	", "有效标签"), emptyList(), emptyList(), "",
            false, false, false, emptyList(), "", "",
        )
        val got = raw.toAlbumDetail()
        assertEquals(listOf("真实作者"), got.authors)
        assertEquals(listOf("有效作品"), got.works)
        assertEquals(listOf("有效标签"), got.tags)
    }

    @Test
    fun 搜索查询_按主标签与排序构造() {
        val site = searchQuery("关键词", 2, SearchMainTag.SITE_SEARCH, BrowseOrder.MOST_LIKED)
        assertEquals(0, site.mainTag.value)
        assertEquals("关键词", site.searchQuery)
        assertEquals("tf", site.orderBy.value)
        assertEquals(2, site.page)

        val tag = searchQuery("全彩", 1, SearchMainTag.TAG, BrowseOrder.MOST_IMAGES)
        assertEquals(3, tag.mainTag.value)
        assertEquals("mp", tag.orderBy.value)
    }

    @Test
    fun 搜索查询_时间范围默认为全部并可按周过滤() {
        assertEquals("a", searchQuery("词", 1, SearchMainTag.SITE_SEARCH, BrowseOrder.LATEST).timeOption.value)
        assertEquals(
            "w",
            searchQuery("词", 1, SearchMainTag.SITE_SEARCH, BrowseOrder.LATEST, BrowseTime.WEEK).timeOption.value,
        )
    }

    @Test
    fun 搜索排序_映射到库排序值() {
        assertEquals("mr", BrowseOrder.LATEST.toOrderBy().value)
        assertEquals("mv", BrowseOrder.MOST_VIEWED.toOrderBy().value)
        assertEquals("mp", BrowseOrder.MOST_IMAGES.toOrderBy().value)
        assertEquals("tf", BrowseOrder.MOST_LIKED.toOrderBy().value)
    }

    @Test
    fun 搜索范围_映射到库主标签() {
        assertEquals(SearchMainTag.SITE_SEARCH, SearchScope.SITE.toMainTag())
        assertEquals(SearchMainTag.WORK, SearchScope.WORK.toMainTag())
        assertEquals(SearchMainTag.AUTHOR, SearchScope.AUTHOR.toMainTag())
        assertEquals(SearchMainTag.TAG, SearchScope.TAG.toMainTag())
        assertEquals(SearchMainTag.ACTOR, SearchScope.ACTOR.toMainTag())
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
        assertEquals("9", got.albumId)
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

    @Test
    fun 筛选条件_转库查询_映射分类排序时间与页码() {
        val q = BrowseFilter(
            category = BrowseCategory.KOREAN,
            order = BrowseOrder.MOST_LIKED,
            time = BrowseTime.WEEK,
        ).toSearchQuery(3)
        assertEquals("hanman", q.category.value)
        assertEquals("tf", q.orderBy.value)
        assertEquals("w", q.timeOption.value)
        assertEquals(3, q.page)
        assertEquals("", q.searchQuery)
    }

    @Test
    fun 筛选条件_默认值_全部分类最新排序全部时间() {
        val q = BrowseFilter().toSearchQuery(1)
        assertEquals("0", q.category.value)
        assertEquals("mr", q.orderBy.value)
        assertEquals("a", q.timeOption.value)
        assertEquals(1, q.page)
    }

    @Test
    fun 评论_映射_昵称优先且回复递归() {
        val reply = comment(id = "r1", nickname = "昵称二", content = "回复", postDate = "2026-01-01")
        val parent = comment(
            id = "c1",
            username = "user1",
            content = "内容",
            postDate = "2026-01-02",
            likes = 3,
            name = "JM123",
            replys = listOf(reply),
        )
        val got = parent.toCommentItem()
        assertEquals("c1", got.id)
        assertEquals("user1", got.author)
        assertEquals("内容", got.content)
        assertEquals("2026-01-02", got.postDate)
        assertEquals(3, got.likes)
        assertEquals(1, got.replies.size)
        assertEquals("昵称二", got.replies[0].author)
        assertEquals("回复", got.replies[0].content)
    }

    @Test
    fun 评论_映射_无昵称回退用户名再回退匿名() {
        assertEquals("userX", comment(id = "a", username = "userX").toCommentItem().author)
        assertEquals("JM9", comment(id = "b", name = "JM9").toCommentItem().author)
        assertEquals("匿名", comment(id = "c").toCommentItem().author)
    }

    @Test
    fun 评论空内容_映射_内容为空串() {
        val got = comment(id = "c2", content = null).toCommentItem()
        assertEquals("", got.content)
        assertNull(got.postDate)
    }

    @Test
    fun 评论_映射_关联作品封面按图片域名构造() {
        val got = comment(id = "c3", aid = "123456", name = "JM123456")
            .toCommentItem(imageHost = "18comic.vip")
        assertEquals("123456", got.albumId)
        assertEquals("https://18comic.vip/media/albums/123456_3x4.jpg", got.albumCoverUrl)
    }

    @Test
    fun 评论_映射_无关联作品或域名未知时封面为null() {
        assertNull(comment(id = "c4", aid = "0").toCommentItem(imageHost = "18comic.vip").albumCoverUrl)
        assertNull(comment(id = "c5", aid = "").toCommentItem(imageHost = "18comic.vip").albumCoverUrl)
        assertNull(comment(id = "c6", aid = "123456").toCommentItem().albumCoverUrl)
    }

    @Test
    fun 评论列表_映射_含总数() {
        val got = JmCommentList(5, listOf(comment(id = "c1"))).toCommentPage()
        assertEquals(5, got.total)
        assertEquals(listOf("c1"), got.items.map { it.id })
    }

    @Test
    fun 章节_映射_图片地址含下载查询参数() {
        val photo = JmPhoto(
            "p3", "t", "9", "220980", 0, null, null,
            listOf(JmImage("p3", "220980", "00001.jpg", "http://x/1.jpg", "file=b10c01.jpg", 0)),
            true,
        )
        val got = photo.toPhotoPages()
        assertEquals("http://x/1.jpg?file=b10c01.jpg", got.images[0].url)
    }

    @Test
    fun 页面图片_转库图片_序号从1起且下载地址透传() {
        val got = PageImage("p1", "220980", 0, "00001.jpg", "http://x/1.jpg?file=a.jpg").toJmImage()
        assertEquals(1, got.sortOrder())
        assertEquals("http://x/1.jpg?file=a.jpg", got.getDownloadUrl())
        assertEquals("p1", got.photoId())
        assertEquals("220980", got.scrambleId())
        assertEquals("00001.jpg", got.filename())
    }
}
