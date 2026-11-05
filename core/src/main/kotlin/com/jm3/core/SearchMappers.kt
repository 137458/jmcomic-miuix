package com.jm3.core

import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchScope
import io.github.jukomu.jmcomic.api.enums.Category
import io.github.jukomu.jmcomic.api.enums.OrderBy
import io.github.jukomu.jmcomic.api.enums.SearchMainTag
import io.github.jukomu.jmcomic.api.enums.TimeOption
import io.github.jukomu.jmcomic.api.model.SearchQuery

fun BrowseFilter.toSearchQuery(page: Int): SearchQuery {
    val category = Category.entries.firstOrNull { it.value == category.apiValue } ?: Category.ALL
    val orderBy = OrderBy.entries.firstOrNull { it.value == order.apiValue } ?: OrderBy.LATEST
    val time = TimeOption.entries.firstOrNull { it.value == time.apiValue } ?: TimeOption.ALL
    return SearchQuery.Builder()
        .text("")
        .category(category)
        .orderBy(orderBy)
        .time(time)
        .page(page)
        .build()
}

/** 排序值映射：core 枚举值即库 OrderBy 的 API 值。 */
fun BrowseOrder.toOrderBy(): OrderBy =
    OrderBy.entries.firstOrNull { it.value == apiValue } ?: OrderBy.LATEST

/** 搜索范围映射到库的主标签。 */
fun SearchScope.toMainTag(): SearchMainTag = when (this) {
    SearchScope.SITE -> SearchMainTag.SITE_SEARCH
    SearchScope.WORK -> SearchMainTag.WORK
    SearchScope.AUTHOR -> SearchMainTag.AUTHOR
    SearchScope.TAG -> SearchMainTag.TAG
    SearchScope.ACTOR -> SearchMainTag.ACTOR
}

/** 时间范围映射：core 枚举值即库 TimeOption 的 API 值。 */
fun BrowseTime.toTimeOption(): TimeOption =
    TimeOption.entries.firstOrNull { it.value == apiValue } ?: TimeOption.ALL

/** 关键词/标签搜索查询；mainTag 决定站内搜索或标签检索，time 由服务端过滤时间范围。 */
fun searchQuery(
    text: String,
    page: Int,
    mainTag: SearchMainTag,
    order: BrowseOrder,
    time: BrowseTime = BrowseTime.ALL,
): SearchQuery = SearchQuery.Builder()
    .mainTag(mainTag)
    .text(text)
    .orderBy(order.toOrderBy())
    .time(time.toTimeOption())
    .page(page)
    .build()
