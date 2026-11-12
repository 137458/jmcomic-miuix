package com.jm3.core.paging

import com.jm3.core.appendedBy
import com.jm3.core.replacedBy
import com.jm3.core.toSlice
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagedFeedTest {

    private fun result(id: String) = SearchResult(
        id = id,
        title = "标题$id",
        authors = listOf("作者$id"),
        tags = listOf("标签$id"),
        coverUrl = "https://cdn.example/media/albums/${id}_3x4.jpg",
    )

    private fun searchPage(page: Int, totalPages: Int, vararg ids: String, totalItems: Int = 0) = SearchPage(
        currentPage = page,
        totalPages = totalPages,
        items = ids.map(::result),
        totalItems = totalItems,
    )

    private fun comment(id: String) = CommentItem(
        id = id,
        author = "作者",
        content = "内容",
        postDate = null,
        likes = 0,
        replies = emptyList(),
    )

    private val keyOfSearch: (SearchResult) -> Any = { it.id }

    @Test
    fun 首页落下来时记下页码与总页数() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 4, "1", "2", totalItems = 90).toSlice(), keyOfSearch)

        assertEquals(listOf("1", "2"), feed.items.map { it.id })
        assertEquals(1, feed.page)
        assertEquals(4, feed.pageCount)
        assertEquals(90, feed.total)
        assertTrue(feed.hasMore)
        assertTrue(feed.canLoadMore)
    }

    @Test
    fun 追加下一页时按id去重并推进游标() {
        val first = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1", "2").toSlice(), keyOfSearch)

        val appended = first.appendedBy(searchPage(2, 3, "2", "3"))

        assertEquals(listOf("1", "2", "3"), appended.items.map { it.id })
        assertEquals(2, appended.page)
        assertEquals(3, appended.pageCount)
    }

    @Test
    fun 末页落下来时不再允许加载更多() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 2, "1").toSlice(), keyOfSearch)
            .appendedBy(searchPage(2, 2, "2"))

        assertFalse(feed.hasMore)
        assertFalse(feed.canLoadMore)
    }

    @Test
    fun 已经在拉时再点加载更多不会重复发起() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1").toSlice(), keyOfSearch)
            .beginLoadMore()

        assertTrue(feed.loadingMore)
        assertTrue(feed.beginLoadMore().loadingMore)
        assertEquals(feed, feed.beginLoadMore())
    }

    @Test
    fun 加载更多失败时撤掉在途标记并留出重试入口() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1").toSlice(), keyOfSearch)
            .beginLoadMore()
            .failLoadMore()

        assertFalse(feed.loadingMore)
        assertTrue(feed.loadMoreFailed)
        assertEquals(listOf("1"), feed.items.map { it.id })
        assertEquals(1, feed.page)
        assertTrue(feed.canLoadMore)
    }

    @Test
    fun 重试成功后清掉失败标记() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1").toSlice(), keyOfSearch)
            .beginLoadMore()
            .failLoadMore()
            .beginLoadMore()
            .appendedBy(searchPage(2, 3, "2"))

        assertFalse(feed.loadMoreFailed)
        assertFalse(feed.loadingMore)
        assertEquals(listOf("1", "2"), feed.items.map { it.id })
    }

    @Test
    fun 服务端没报总数时沿用上一次的总数() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1", totalItems = 120).toSlice(), keyOfSearch)
            .appendedBy(searchPage(2, 3, "2"))

        assertEquals(120, feed.total)
    }

    @Test
    fun 末端空页也能收尾不把游标停在旧页() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 3, "1").toSlice(), keyOfSearch)
            .appendedBy(searchPage(2, 3))

        assertEquals(2, feed.page)
        assertEquals(listOf("1"), feed.items.map { it.id })
    }

    @Test
    fun 点击分页整页替换时游标跟着目标页走() {
        val feed = PagedFeed<SearchResult>()
            .replace(searchPage(1, 5, "1").toSlice(), keyOfSearch)
            .beginLoadMore()
            .failLoadMore()
            .replace(searchPage(3, 5, "7", "8").toSlice(), keyOfSearch)

        assertEquals(3, feed.page)
        assertEquals(5, feed.pageCount)
        assertEquals(listOf("7", "8"), feed.items.map { it.id })
        assertFalse(feed.loadingMore)
        assertFalse(feed.loadMoreFailed)
    }

    @Test
    fun 评论页不满总数时继续可取() {
        val feed = PagedFeed<CommentItem>()
            .replacedBy(CommentPage(total = 120, items = listOf(comment("1"), comment("2"))))

        assertEquals(2, feed.items.size)
        assertTrue(feed.canLoadMore)
        assertEquals(120, feed.total)

        val appended = feed.appendedBy(CommentPage(total = 120, items = listOf(comment("2"), comment("3"))))

        assertEquals(listOf("1", "2", "3"), appended.items.map { it.id })
        assertTrue(appended.canLoadMore)
    }

    @Test
    fun 评论页取满总数时收尾() {
        val feed = PagedFeed<CommentItem>()
            .replacedBy(CommentPage(total = 3, items = listOf(comment("1"), comment("2"))))
            .appendedBy(CommentPage(total = 3, items = listOf(comment("3"))))

        assertEquals(listOf("1", "2", "3"), feed.items.map { it.id })
        assertFalse(feed.hasMore)
        assertNull(feed.pageCount)
    }

    // 触底判据：屏幕只报「最后一个可见项下标」与「列表总项数」，其余判定全在 PagedFeed 里。

    @Test
    fun 距末尾三项内请求下一页() {
        val feed = PagedFeed<SearchResult>().replacedBy(searchPage(page = 1, totalPages = 4, "1", "2"))

        assertTrue(feed.shouldLoadMore(lastVisibleIndex = 2, totalItemCount = 5))
        assertTrue(feed.shouldLoadMore(lastVisibleIndex = 4, totalItemCount = 5))
    }

    @Test
    fun 离末尾还远时不请求下一页() {
        val feed = PagedFeed<SearchResult>().replacedBy(searchPage(page = 1, totalPages = 4, "1", "2"))

        assertFalse(feed.shouldLoadMore(lastVisibleIndex = 1, totalItemCount = 20))
    }

    @Test
    fun 没有下一页时不请求下一页() {
        val feed = PagedFeed<SearchResult>().replacedBy(searchPage(page = 4, totalPages = 4, "1", "2"))

        assertFalse(feed.hasMore)
        assertFalse(feed.shouldLoadMore(lastVisibleIndex = 4, totalItemCount = 5))
    }

    @Test
    fun 已经在拉时不请求下一页() {
        val feed = PagedFeed<SearchResult>()
            .replacedBy(searchPage(page = 1, totalPages = 4, "1", "2"))
            .beginLoadMore()

        assertFalse(feed.shouldLoadMore(lastVisibleIndex = 4, totalItemCount = 5))
    }

    @Test
    fun 加载更多失败后不自动重试下一页() {
        // 失败后停在原地等用户点页脚重试：滚动信息仍停在末尾，不能借它反复重发
        val feed = PagedFeed<SearchResult>()
            .replacedBy(searchPage(page = 1, totalPages = 4, "1", "2"))
            .beginLoadMore()
            .failLoadMore()

        assertTrue(feed.canLoadMore)
        assertFalse(feed.shouldLoadMore(lastVisibleIndex = 4, totalItemCount = 5))
    }

    @Test
    fun 列表还没铺满时不请求下一页() {
        val feed = PagedFeed<SearchResult>().replacedBy(searchPage(page = 1, totalPages = 4, "1", "2"))

        assertFalse(feed.shouldLoadMore(lastVisibleIndex = -1, totalItemCount = 0))
    }

    @Test
    fun 空列表不显示分页区() {
        assertFalse(PagedFeed<SearchResult>().showsPagedFooter())
    }

    @Test
    fun 只有一页且没有下一页时不显示分页区() {
        val feed = PagedFeed(items = listOf(result("1")), page = 1, pageCount = null, hasMore = false)

        assertFalse(feed.showsPagedFooter())
    }

    @Test
    fun 总页数超过一页时显示分页区() {
        val feed = PagedFeed(items = listOf(result("1")), page = 1, pageCount = 2, hasMore = true)

        assertTrue(feed.showsPagedFooter())
    }

    @Test
    fun 还有下一页时即使总页数未知也显示分页区() {
        val feed = PagedFeed(items = listOf(result("1")), page = 1, pageCount = null, hasMore = true)

        assertTrue(feed.showsPagedFooter())
    }

    @Test
    fun 已翻过第一页时显示分页区() {
        val feed = PagedFeed(items = listOf(result("1")), page = 2, pageCount = null, hasMore = false)

        assertTrue(feed.showsPagedFooter())
    }
}
