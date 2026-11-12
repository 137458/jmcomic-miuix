package com.jm3.core.paging

/**
 * 服务端返回的一页结果：本页条目 + 后面还有没有。
 *
 * @param page 服务端报的页码；0 表示这一页没有页码概念，游标按调用顺序推进
 * @param pageCount 服务端报的总页数；null 表示未提供
 * @param total 结果总数；null 表示未提供
 */
data class PageSlice<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val total: Int? = null,
    val page: Int = 0,
    val pageCount: Int? = null,
)

/**
 * 分页列表的纯状态机：条目、游标、总数与加载更多开关都在这里。
 *
 * 屏幕只做三件事：把服务端返回的一页交给 [replace] 或 [append]、按 [canLoadMore] 决定要不要发请求、
 * 把 [loadingMore] 与 [loadMoreFailed] 交给 LoadMoreFooter。三种互不兼容的游标写法
 * （页码对总页数、已取条数对总数、单页条数推还有没有）都收敛成 [PageSlice]。
 */
data class PagedFeed<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val pageCount: Int? = null,
    val total: Int? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
) {
    /** 还有下一页且没有在途请求时才允许继续拉。 */
    val canLoadMore: Boolean get() = hasMore && !loadingMore

    /** 标记在途；已经在拉或没有下一页时原样返回，避免重复请求。 */
    fun beginLoadMore(): PagedFeed<T> = if (canLoadMore) copy(loadingMore = true, loadMoreFailed = false) else this

    /** 这一页失败：撤掉在途标记、留出底部重试入口，游标与已加载条目不动。 */
    fun failLoadMore(): PagedFeed<T> = copy(loadingMore = false, loadMoreFailed = true)

    /** 整页替换（首页、下拉刷新、点击分页）：条目按 key 去重，游标取这一页的页码。 */
    fun replace(slice: PageSlice<T>, keyOf: (T) -> Any): PagedFeed<T> = PagedFeed(
        items = slice.items.distinctBy(keyOf),
        page = slice.page.takeIf { it > 0 } ?: 1,
        pageCount = slice.pageCount,
        total = slice.total,
        hasMore = slice.hasMore,
    )

    /** 追加下一页：按 key 去重后接在末尾，游标推进一格（服务端报了页码就按它）。 */
    fun append(slice: PageSlice<T>, keyOf: (T) -> Any): PagedFeed<T> {
        val seen = items.mapTo(HashSet()) { keyOf(it) }
        val appended = slice.items.filter { seen.add(keyOf(it)) }
        return copy(
            items = items + appended,
            page = slice.page.takeIf { it > 0 } ?: page + 1,
            pageCount = slice.pageCount ?: pageCount,
            total = slice.total ?: total,
            hasMore = slice.hasMore,
            loadingMore = false,
            loadMoreFailed = false,
        )
    }
}
/** 分页/加载区该不该显示：有条目，且（总页数超过 1、仍有下一页、已翻过第 1 页）三者之一。 */
fun PagedFeed<*>.showsPagedFooter(): Boolean =
    items.isNotEmpty() && ((pageCount ?: 1) > 1 || hasMore || page > 1)

/** 触底判据的默认提前量：最后一个可见项距末尾这么多项时开始取下一页。 */
const val PREFETCH_DISTANCE: Int = 3

/**
 * 触底判据：列表滚到接近末尾、还有下一页、且没有在途请求时返回 true。
 *
 * 屏幕只提供「最后一个可见项下标」与「列表总项数」，不再各自写一遍比较与两个布尔的与运算。
 */
fun PagedFeed<*>.shouldLoadMore(
    lastVisibleIndex: Int,
    totalItemCount: Int,
    distance: Int = PREFETCH_DISTANCE,
): Boolean = canLoadMore &&
    !loadMoreFailed &&
    lastVisibleIndex >= 0 &&
    totalItemCount > 0 &&
    lastVisibleIndex >= totalItemCount - distance
