package com.jm3.core.paging

import com.jm3.core.settings.PaginationMode

/**
 * 分页条的页码换算规则：末页不再递增、首页不再递减、页码始终落在 [1, pageCount] 内。
 *
 * 无平台依赖的纯函数，放在 :core 供各列表屏复用，避免同一套换算在 :app 里各写一份。
 */

/** 下一页页码：末页钳制到 [pageCount]，总页数未知或为 0 时回到第 1 页。 */
fun pagerNextPage(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 1
    return pagerClamp(page + 1, pageCount)
}

/** 页码收敛到 [1, pageCount]；总页数未知或为 0 时按第 1 页处理。 */
fun pagerClamp(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 1
    return page.coerceIn(1, pageCount)
}

/**
 * 把分页条上报的目标页收敛成真正要加载的页码。
 *
 * 总页数已知时按 [pagerClamp] 收进 [1, pageCount]；总页数未知（接口不返回总数）时按
 * 当前页偏移一格，并保证不落到第 1 页之前。
 */
fun pagerTargetPage(currentPage: Int, requestedPage: Int, pageCount: Int?): Int {
    val count = pageCount ?: 0
    if (count > 0) return pagerClamp(requestedPage, count)
    val shifted = when {
        requestedPage > currentPage -> currentPage + 1
        requestedPage < currentPage -> currentPage - 1
        else -> currentPage
    }
    return if (shifted < 1) 1 else shifted
}

/** 分页模式变化时该回到第 1 页重载；模式没变则返回 null（不重载）。 */
fun pagingModeReloadPage(previous: PaginationMode, next: PaginationMode): Int? =
    if (PaginationModePolicy.paginationModeChanged(previous, next)) 1 else null

/**
 * 服务端总数折算总页数；总数为 0 或负数表示未提供，返回 null。
 *
 * 评论、书库这类接口只给 total，页码与末页判据都得由总数推出来。
 */
fun pageCountOf(total: Int, pageSize: Int): Int? {
    require(pageSize > 0) { "pageSize 必须为正数，实际为 $pageSize" }
    if (total <= 0) return null
    return (total + pageSize - 1) / pageSize
}

/** 同上，但总页数未知时按 1 页处理：界面上的分页条总要有个页数。 */
fun pageCountAtLeastOne(total: Int, pageSize: Int): Int = pageCountOf(total, pageSize) ?: 1

/** 页码判末页：请求页之后还有页即返回 true。 */
fun hasMorePages(page: Int, pageCount: Int): Boolean = page < pageCount

/** 一页是否装满：服务端不报总数的列表靠「这页装满了」推断后面可能还有。 */
fun pageLooksFull(itemCount: Int, pageSize: Int): Boolean = itemCount >= pageSize

/**
 * 已加载到第几条：按每页条数估算，页内条数不足时以实际条数补。
 *
 * @param page 从 1 起的页码
 * @param pageSizeHint 每页条数提示
 * @param pageItemCount 这一页实际返回的条数
 */
fun loadedThrough(page: Int, pageSizeHint: Int, pageItemCount: Int): Int {
    val size = pageSizeHint.coerceAtLeast(pageItemCount)
    return (page - 1).coerceAtLeast(0) * size + pageItemCount
}

/** 按已加载条数判末页：本页为空视为到头，否则与总数比较。 */
fun hasMoreByLoaded(pageItemCount: Int, loaded: Int, total: Int): Boolean =
    pageItemCount > 0 && loaded < total
