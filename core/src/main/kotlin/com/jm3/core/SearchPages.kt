package com.jm3.core

import com.jm3.core.model.SearchPage

/** 追加下一页结果，按 id 去重并更新分页信息。 */
fun SearchPage.mergedWith(next: SearchPage): SearchPage {
    val seen = items.mapTo(HashSet()) { it.id }
    val appended = next.items.filterNot { it.id in seen }
    return copy(
        currentPage = next.currentPage,
        totalPages = next.totalPages,
        items = items + appended,
    )
}
