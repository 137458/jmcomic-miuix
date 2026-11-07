package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed

/**
 * 一份列表的加载状态：首页发现流、首页搜索结果、浏览页结果共用同一套三态。
 *
 * 三处原本各写一份同形的 sealed 类型，判空分支也跟着各写一遍；合并成一个类型后，
 * 列表底部、错误页、加载态这些共用渲染只需要认这一种状态。
 */
internal sealed interface ListState {
    data object Loading : ListState
    data class Loaded(val feed: PagedFeed<SearchResult>) : ListState
    data class Failed(val message: String) : ListState
}

/**
 * 列表在会话里的身份：缓存靠它判断「这次回来的是不是同一份结果」。
 *
 * 首页发现流与搜索结果各占一格；浏览页按筛选口径（[BrowsePageKey]）认人，
 * 同一模式换了筛选条件就是另一份结果，回来必须重新请求。
 */
internal sealed interface SessionListKey {
    data object HomeFeed : SessionListKey
    data object HomeSearch : SessionListKey
    data class Browse(val pageKey: BrowsePageKey) : SessionListKey
}

/**
 * 浏览页的筛选口径。
 *
 * 四个值直接取筛选条持有的原始值，不做 filter() 那套归一化：切页回来时它们会被 rememberSaveable 原样还原，
 * 缓存才命中；归一化抹平了两种模式真正关心的字段差异，反而更容易认错。
 */
internal data class BrowsePageKey(
    val mode: BrowseMode,
    val category: String,
    val order: String,
    val time: String,
)

/**
 * 首页侧栏数据：连载更新、热门标签、随便看看。
 *
 * 三份数据一起到达、一起清空，合成一份既少三个字段，也让「侧栏是否为空」只判一次。
 */
internal data class HomeSideData(
    val hotTags: List<String> = emptyList(),
    val serialUpdates: List<SearchResult> = emptyList(),
    val randomPicks: List<SearchResult> = emptyList(),
) {
    /** 三份都空时首屏不需要再请求侧栏。 */
    val isEmpty: Boolean
        get() = hotTags.isEmpty() && serialUpdates.isEmpty() && randomPicks.isEmpty()
}

/**
 * 会话级列表缓存，全应用一个实例（由组装根持有，经屏幕依赖容器注入）。
 *
 * 页面数据是 remember 的：切 tab 或点进详情再回来，Compose 会销毁整棵页面，回来只剩一个 Loading 项，
 * LazyList 会把恢复的滚动索引钳到 0，用户就回到了列表顶部。这里按身份留一份已加载的结果，回来首帧就能铺满列表。
 *
 * 只留加载成功的结果：Loading 与 Failed 落进来，只会让下次回来先闪一下旧错误，不如让页面重新请求。
 * 页面自己的当前状态仍由 Composable 持有，失败态才显示得出来。
 */
internal class SessionCache {

    private val entries = mutableMapOf<SessionListKey, ListState>()

    /** 首页侧栏数据；与分页列表无关，整份替换。 */
    var homeSide: HomeSideData = HomeSideData()

    /** 该身份下已加载的结果；没有就返回 Loading，页面照常去请求。 */
    fun stateOf(key: SessionListKey): ListState = entries[key] ?: ListState.Loading

    /** 该身份下已加载的结果；没有返回 null（首页用它区分「没搜过」与「搜出来是空的」）。 */
    fun stateOrNull(key: SessionListKey): ListState? = entries[key]

    /** 该身份下是否已有可用结果。 */
    fun hasPage(key: SessionListKey): Boolean = entries[key] is ListState.Loaded

    /**
     * 只记加载成功的结果：其余状态落进来，下次回来只会先闪一下旧加载态或旧错误。
     *
     * 记的是稳定快照：在途/失败标记描述的是页面这一次请求，缓存下来只会在下次回来时永远转圈。
     */
    fun remember(key: SessionListKey, state: ListState) {
        if (state is ListState.Loaded) {
            entries[key] = ListState.Loaded(state.feed.copy(loadingMore = false, loadMoreFailed = false))
        } else {
            entries.remove(key)
        }
    }

    fun forget(key: SessionListKey) {
        entries.remove(key)
    }

    /** 分页模式变化后作废列表结果：旧模式下已追加的内容不能按新语义展示；侧栏数据与此无关，保留。 */
    fun forgetPages() {
        entries.clear()
    }

    /** 退出登录等场景整份清空。 */
    fun clear() {
        entries.clear()
        homeSide = HomeSideData()
    }
}

/**
 * 首页展示模式判定逻辑。
 *
 * @param activeQuery 当前已执行/生效的搜索词（关键词或标签名）
 * @param results 当前持有的搜索结果状态
 * @return 是否应当展示搜索内容列（而非主页发现流）
 */
internal fun isShowingSearchResults(activeQuery: String, results: ListState?): Boolean {
    // 只要有已生效的搜索词，或者已有明确的搜索结果，页面即处于搜索态
    return activeQuery.isNotBlank() || results != null
}
