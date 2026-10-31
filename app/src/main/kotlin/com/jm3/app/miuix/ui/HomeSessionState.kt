package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult

/**
 * 首页列表加载状态。
 */
sealed interface HomeListState {
    data object Loading : HomeListState
    data class Loaded(val page: SearchPage) : HomeListState
    data class Failed(val message: String) : HomeListState
}

/**
 * 首页会话级状态缓存。
 *
 * 在 Activity / 单例生命周期内存活，跨路由跳转（如点击漫画进详情、阅读器后返回）保留发现流与搜索结果，
 * 避免用户在搜索结果点进详情返回时因组件重建而丢失搜索结果列表，或者重复向后端发送搜索请求。
 */
object HomeSessionCache {
    var feed: HomeListState = HomeListState.Loading
    var results: HomeListState? = null
    var hotTags: List<String> = emptyList()
    var serialUpdates: List<SearchResult> = emptyList()
    var randomPicks: List<SearchResult> = emptyList()

    /**
     * 当前会话缓存中是否存在已加载成功的搜索结果。
     */
    val hasSearchResults: Boolean
        get() = results is HomeListState.Loaded

    fun updateResults(state: HomeListState?) {
        results = state
    }

    fun clearSearch() {
        results = null
    }

    fun reset() {
        feed = HomeListState.Loading
        results = null
        hotTags = emptyList()
        serialUpdates = emptyList()
        randomPicks = emptyList()
    }
}

/**
 * 首页展示模式判定逻辑。
 *
 * @param activeQuery 当前已执行/生效的搜索词（关键词或标签名）
 * @param results 当前持有的搜索结果状态
 * @return 是否应当展示搜索内容列（而非主页发现流）
 */
fun isShowingSearchResults(activeQuery: String, results: HomeListState?): Boolean {
    // 只要有已生效的搜索词，或者已有明确的搜索结果，页面即处于搜索态
    return activeQuery.isNotBlank() || results != null
}
