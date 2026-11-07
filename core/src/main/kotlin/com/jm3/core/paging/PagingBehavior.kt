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
