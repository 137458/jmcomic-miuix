package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage

/** 浏览页列表状态。 */
internal sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Loaded(val page: SearchPage) : BrowseUiState
    data class Failed(val message: String) : BrowseUiState
}

/**
 * 浏览页的筛选口径：缓存靠它判断「这次回来的是不是同一份结果」。
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
 * 浏览页会话级状态缓存。
 *
 * 切 tab 时 Compose 会销毁整棵页面，rememberSaveable 能把滚动位置还回来，但列表数据是 remember 的：
 * 回来只剩一个 Loading 项，LazyList 会把恢复的索引钳到 0，用户就回到了列表顶部。
 * 这里按模式留一份已加载的结果，回来首帧就能铺满列表，位置才落得回去。
 */
internal object BrowseSessionCache {

    private class Entry(val key: BrowsePageKey, val state: BrowseUiState)

    private val entries = mutableMapOf<BrowseMode, Entry>()

    /** 同一口径下已加载的结果；没有就返回 Loading，页面照常去请求。 */
    fun stateFor(key: BrowsePageKey): BrowseUiState =
        entries[key.mode]?.takeIf { it.key == key }?.state ?: BrowseUiState.Loading

    /** 同一口径下是否已有可用结果。 */
    fun hasPage(key: BrowsePageKey): Boolean = entries[key.mode]?.key == key

    /**
     * 只留加载成功的结果：Loading 与 Failed 落进来，只会让下次回来先闪一下旧错误，不如让页面重新请求。
     */
    fun remember(key: BrowsePageKey, state: BrowseUiState) {
        if (state is BrowseUiState.Loaded) {
            entries[key.mode] = Entry(key, state)
        } else {
            entries.remove(key.mode)
        }
    }

    /** 单测清场用。 */
    fun reset() {
        entries.clear()
    }
}
