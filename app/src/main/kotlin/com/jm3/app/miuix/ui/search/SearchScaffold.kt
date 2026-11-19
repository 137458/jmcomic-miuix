package com.jm3.app.miuix.ui.search

import com.jm3.app.miuix.ui.ListStateCache
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.coui.page.couiCanJumpFeedPage
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.Jm3Gateway
import com.jm3.core.albumCodeOf
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.toSlice
import kotlinx.coroutines.CoroutineScope

/**
 * 搜索状态机的单一真源：首页（两块皮肤）与搜索页（两块皮肤）共四处都消费这里。
 *
 * 本文件只放纯状态、纯判定与 loader 工厂：
 * - 纯转移一律 copy 返回新副本，不持有 Compose 状态，也不碰焦点与键盘；
 * - 输入法收起、焦点清除这类 Compose 副作用留在皮肤或 @Composable 入口；
 * - 不改任何一处皮肤文案：标题与摘要的措辞差异由调用方拼后缀，这里只提供共同前缀。
 */

/** 一次搜索的取数条件：查询词 + 是否标签检索 + 排序。 */
internal data class SearchCriteria(
    val query: String,
    val byTag: Boolean = false,
    val order: BrowseOrder = BrowseOrder.LATEST,
) {
    /** 标签检索走 TAG 域，普通关键词走 SITE 域。 */
    val scope: SearchScope get() = if (byTag) SearchScope.TAG else SearchScope.SITE
}

/**
 * 搜索输入面的纯状态：输入框、已执行条件、排序、展开态。
 *
 * [restoredResults] 是「手里还留着上次结果」的标记：只有接了 HomeSearch 缓存的皮肤才置真，
 * 它让从详情页返回时搜索结果先出现；不接缓存的皮肤恒为 false，[searching] 就只看 [activeQuery]。
 */
internal data class SearchInputState(
    val query: String = "",
    val activeQuery: String = "",
    val byTag: Boolean = false,
    val orderValue: String = BrowseOrder.LATEST.apiValue,
    val expanded: Boolean = false,
    val restoredResults: Boolean = false,
) {
    /** 排序解析：认不出的取值退回最新。 */
    val order: BrowseOrder get() = searchOrderOf(orderValue)

    /** 当前条件在 loader 眼里长什么样。 */
    val criteria: SearchCriteria get() = SearchCriteria(activeQuery, byTag, order)

    /** 搜索态 = 有活跃关键词 或 缓存里还留着上次结果。 */
    val searching: Boolean get() = activeQuery.isNotBlank() || restoredResults

    /** 输入框变化：只改输入，不动已执行条件。 */
    fun typed(value: String): SearchInputState = copy(query = value)

    /** 展开态变化。 */
    fun expandedTo(value: Boolean): SearchInputState = copy(expanded = value)

    /**
     * 执行一次搜索：条件落到已执行位，排序一并记住。
     *
     * [keepCachedResults] 对应「结果能靠缓存复原」的皮肤：它把标记一并置真，
     * 不清缓存的皮肤传 false，标记保持原样（恒为 false）。
     */
    fun started(criteria: SearchCriteria, keepCachedResults: Boolean = false): SearchInputState = copy(
        activeQuery = criteria.query,
        byTag = criteria.byTag,
        orderValue = criteria.order.apiValue,
        restoredResults = restoredResults || keepCachedResults,
    )

    /** 排序切换：只换排序，关键词与标签位不动。 */
    fun orderChanged(order: BrowseOrder): SearchInputState = copy(orderValue = order.apiValue)

    /** 清空搜索：回到发现流。 */
    fun cleared(): SearchInputState = copy(
        query = "",
        activeQuery = "",
        byTag = false,
        restoredResults = false,
    )

    /** 分页模式切换：搜索态标记复位，但输入框内容不动。 */
    fun searchDropped(): SearchInputState = copy(restoredResults = false)
}

/** 提交搜索的三种结局：空输入忽略、车号直达、正常检索。 */
internal sealed interface SearchSubmission {
    data object Ignored : SearchSubmission

    data class OpenAlbum(val albumId: String) : SearchSubmission

    data class Search(val criteria: SearchCriteria) : SearchSubmission
}

/**
 * 提交输入的判定：纯数字默认就是车号，与搜索栏既有的车号直达走同一套判定。
 *
 * 首页两块皮肤原本各写一遍同一段 when，这里只保留一份。
 */
internal fun resolveSearchSubmission(query: String, order: BrowseOrder): SearchSubmission {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return SearchSubmission.Ignored
    val albumCode = albumCodeOf(trimmed)
    return if (albumCode != null) {
        SearchSubmission.OpenAlbum(albumCode)
    } else {
        SearchSubmission.Search(SearchCriteria(query = trimmed, byTag = false, order = order))
    }
}

/** 排序取值解析：认不出的存档值退回最新。 */
internal fun searchOrderOf(orderValue: String): BrowseOrder =
    BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST

/** 时间取值解析：认不出的存档值退回全部。 */
internal fun searchTimeOf(timeValue: String): BrowseTime =
    BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

/** 搜索页标题：站内检索用「搜索：」，其余域用域名的中文标签。 */
internal fun searchPageTitle(query: String, scope: SearchScope): String =
    if (scope == SearchScope.SITE) "搜索：$query" else "${scope.label}：$query"

/** 结果摘要的共同前缀：标签检索写「标签：」，关键词检索用引号包住。 */
internal fun searchHeadline(query: String, byTag: Boolean): String =
    if (byTag) "标签：$query" else "“$query”"

/**
 * 检索结局到分页切片的翻译。
 *
 * 与 list/ListStateAdapters.kt 的 toCouiPageSlice 手写 when 等价；这里给搜索域两套皮肤共用，
 * 避免每处取数再各写一遍三分支。
 */
internal fun SessionOutcome<SearchPage>.toSearchPageSlice(): SessionOutcome<PageSlice<SearchResult>> = when (this) {
    is SessionOutcome.Ok -> SessionOutcome.Ok(value.toSlice())
    SessionOutcome.Expired -> SessionOutcome.Expired
    is SessionOutcome.Failed -> this
}

/**
 * 搜索 loader 工厂：四处搜索入口只在缓存接线与失败文案上不同，取数口径完全一致。
 *
 * @param criteria 取数时读当前条件；缓存型皮肤的 loader 只建一次，靠这个 lambda 逐次取最新条件
 * @param searchScope 检索域来源：搜索页用页面参数，首页按标签位推导（默认就是 [SearchCriteria.scope]）
 */
internal fun searchListLoader(
    scope: CoroutineScope,
    authCall: AuthCall,
    gateway: Jm3Gateway,
    criteria: () -> SearchCriteria,
    failureMessage: String,
    notice: (String) -> Unit,
    searchScope: (SearchCriteria) -> SearchScope = { it.scope },
    time: BrowseTime = BrowseTime.ALL,
    cache: ListStateCache<SearchResult>? = null,
    cacheKey: SessionListKey? = null,
): SessionListLoader<SearchResult> = SessionListLoader(
    scope = scope,
    idOf = { item: SearchResult -> item.id },
    failureMessage = failureMessage,
    fetchPage = { page ->
        val current = criteria()
        authCall.call {
            gateway.search(current.query, page, current.order, searchScope(current), time)
        }.toSearchPageSlice()
    },
    notice = notice,
    cache = cache,
    cacheKey = cacheKey,
)

/**
 * 夹住目标页后再决定跳不跳：null 表示不跳。
 *
 * [requireMoreWhenUnknown] 为真时多一道守卫：总页数未知、目标在往后、又没有下一页，就不发请求。
 * 搜索页那边的跳页没有这道守卫，所以做成参数而不是写死。
 */
internal fun clampedPageJumpTarget(
    feed: PagedFeed<*>,
    target: Int,
    requireMoreWhenUnknown: Boolean = true,
): Int? {
    val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
    if (resolved == feed.page) return null
    if (requireMoreWhenUnknown && feed.pageCount == null && resolved > feed.page && !feed.hasMore) return null
    return resolved
}

/** COUI 口径的跳页：只做合法性校验，通过就按原目标页跳，不做夹取。 */
internal fun couiPageJumpTarget(feed: PagedFeed<*>, target: Int): Int? =
    if (couiCanJumpFeedPage(feed, target)) target else null
