package com.jm3.app.miuix.ui

import com.jm3.core.paging.PaginationModePolicy
import com.jm3.core.settings.PaginationMode

/**
 * 分页条的页码换算规则。
 *
 * [PagerBar] 只上报「当前页 ±1」，页面需要自己把它收敛成合法页码：
 * 末页不再递增、首页不再递减、页码始终落在 [1, pageCount] 内。
 */

/** 下一页页码：末页钳制到 [pageCount]，总页数未知或为 0 时回到第 1 页。 */
internal fun pagerNextPage(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 1
    return pagerClamp(page + 1, pageCount)
}

/** 上一页页码：第 1 页时保持第 1 页。 */
internal fun pagerPrevPage(page: Int): Int = if (page <= 1) 1 else page - 1

/** 页码收敛到 [1, pageCount]；总页数未知或为 0 时按第 1 页处理。 */
internal fun pagerClamp(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 1
    return page.coerceIn(1, pageCount)
}

/**
 * 把分页条上报的目标页换算成实际要加载的页码。
 *
 * 目标页是相对当前页的方向（[PagerBar] 只会给出当前页 ±1），因此按方向换算：
 * 更大的目标走下一页、更小的目标走上一页、相同则保持当前页。
 */
internal fun pagerResolvePage(current: Int, requested: Int, pageCount: Int): Int = when {
    requested > current -> pagerNextPage(current, pageCount)
    requested < current -> pagerPrevPage(current)
    else -> pagerClamp(current, pageCount)
}

/**
 * 把分页条上报的目标页收敛成真正要加载的页码。
 *
 * 总页数已知时按 [pagerClamp] 收进 [1, pageCount]；总页数未知（接口不返回总数）时按
 * 当前页偏移一格，并保证不落到第 1 页之前。
 */
internal fun pagerTargetPage(currentPage: Int, requestedPage: Int, pageCount: Int?): Int {
    val count = pageCount ?: 0
    if (count > 0) return pagerClamp(requestedPage, count)
    val shifted = when {
        requestedPage > currentPage -> currentPage + 1
        requestedPage < currentPage -> currentPage - 1
        else -> currentPage
    }
    return if (shifted < 1) 1 else shifted
}

/**
 * 分页模式变化时该回到哪一页重载；模式没变则返回 null（不重载）。
 *
 * 换模式等于换翻页语义：旧状态里的页码与「已追加」的内容不能继续用，必须回第 1 页
 * 按新语义整页重新加载。
 */
internal fun pagingModeReloadPage(previous: PaginationMode, next: PaginationMode): Int? =
    if (PaginationModePolicy.paginationModeChanged(previous, next)) 1 else null
