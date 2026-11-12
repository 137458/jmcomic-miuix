package com.jm3.core

import com.jm3.core.model.CommentPage
import com.jm3.core.model.CommentItem
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed

/** 追加下一页结果，按 id 去重并更新分页信息。 */
fun SearchPage.mergedWith(next: SearchPage): SearchPage {
    val seen = items.mapTo(HashSet()) { it.id }
    val appended = next.items.filterNot { it.id in seen }
    return copy(
        currentPage = next.currentPage,
        totalPages = next.totalPages,
        totalItems = if (next.totalItems > 0) next.totalItems else totalItems,
        items = items + appended,
    )
}

/** 追加下一页评论，按 id 去重并更新总数。 */
fun CommentPage.mergedWith(next: CommentPage): CommentPage {
    val seen = items.mapTo(HashSet()) { it.id }
    val appended = next.items.filterNot { it.id in seen }
    return CommentPage(total = next.total, items = items + appended)
}
/** 搜索结果页的游标口径：还有下一页当且仅当当前页小于总页数。 */
fun SearchPage.toSlice(): PageSlice<SearchResult> = PageSlice(
    items = items,
    hasMore = currentPage < totalPages,
    total = totalItems.takeIf { it > 0 },
    page = currentPage,
    pageCount = totalPages,
)

/** 用一页搜索结果整页替换（首页、下拉刷新、点击分页）。 */
fun PagedFeed<SearchResult>.replacedBy(page: SearchPage): PagedFeed<SearchResult> = replace(page.toSlice()) { it.id }

/**
 * 一页结果直接装成只含这一页的 [PagedFeed]。
 *
 * 首屏、下拉刷新、点击分页都从这里起手：调用方不必知道要先造一个空 feed 再 replace。
 */
fun SearchPage.toFeed(): PagedFeed<SearchResult> = PagedFeed<SearchResult>().replacedBy(this)

/** 把下一页搜索结果追加进来。 */
fun PagedFeed<SearchResult>.appendedBy(page: SearchPage): PagedFeed<SearchResult> = append(page.toSlice()) { it.id }

/**
 * 评论页没有页码字段，只有本页条目与总数：不满总数就还有下一页。
 *
 * 追加后按去重后的实际条数再算一次，避免服务端一页给满时多放一次空请求。
 */
fun CommentPage.toSlice(): PageSlice<CommentItem> = PageSlice(
    items = items,
    hasMore = items.size < total,
    total = total.takeIf { it > 0 },
)

/** 用一页评论整页替换。 */
fun PagedFeed<CommentItem>.replacedBy(page: CommentPage): PagedFeed<CommentItem> = replace(page.toSlice()) { it.id }

/** 追加下一页评论：本页非空、确实有新增条目且仍未达总数时才继续加载，防止空页或全重复页死循环。 */
fun PagedFeed<CommentItem>.appendedBy(page: CommentPage): PagedFeed<CommentItem> =
    append(page.toSlice()) { it.id }.let { next ->
        val grew = next.items.size > items.size
        next.copy(hasMore = page.items.isNotEmpty() && grew && next.items.size < page.total)
    }
