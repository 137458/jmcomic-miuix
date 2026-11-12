// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import com.jm3.core.paging.PagedFeed

/**
 * 触底预取的统一采集：把「最后可见项的下标、列表总项数、按实测行高折出的提前行数」交给调用方判定。
 *
 * 首页、浏览页与详情页评论区原来各抄一份 snapshotFlow 与行高换算，三处的空列表兜底、密度取法
 * 稍有出入就会让同样的滚动速度在不同页面触发得早晚不一；采集只留这一份，判定仍留在各页
 * （行起点或 feed 级开关只有页面知道）。
 *
 * [enabled] 为 false 时完全不收集（点击翻页不需要触底预取）；[restartKey] 变化会重开快照，
 * 新列表挂载后立刻拿到首帧判定，不必等用户先滑一下。
 */
@Composable
internal fun CouiPrefetchEffect(
    listState: LazyListState,
    enabled: Boolean = true,
    restartKey: Any? = Unit,
    onReachEdge: (lastVisibleIndex: Int, totalItemsCount: Int, prefetchRows: Int) -> Unit,
) {
    val density = LocalDensity.current
    val latestOnReachEdge by rememberUpdatedState(onReachEdge)
    LaunchedEffect(listState, enabled, restartKey) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val rowHeightDp = if (visible.isEmpty()) {
                0f
            } else {
                visible.sumOf { it.size }.toFloat() / visible.size / density.density
            }
            Triple(
                visible.lastOrNull()?.index ?: -1,
                listState.layoutInfo.totalItemsCount,
                prefetchItemCount(COUI_LOAD_MORE_PREFETCH_DP.toFloat(), rowHeightDp),
            )
        }.collect { (lastIndex, totalCount, distance) ->
            latestOnReachEdge(lastIndex, totalCount, distance)
        }
    }
}

/**
 * 结果列表版触底预取：调用方交整份 [feed]、列表里结果行的起点与取下一页的动作。
 *
 * feed 级开关（还能不能加载、上次是否失败、空列表）由 [shouldPrefetchRow] 一并处理，
 * 页面只在真正需要取下一页时收到回调。
 */
@Composable
internal fun CouiFeedPrefetchEffect(
    feed: PagedFeed<*>?,
    rowStart: Int,
    listState: LazyListState,
    columns: Int = 1,
    enabled: Boolean = true,
    restartKey: Any? = Unit,
    onPrefetch: () -> Unit,
) {
    val latestFeed by rememberUpdatedState(feed)
    val latestRowStart by rememberUpdatedState(rowStart)
    val latestColumns by rememberUpdatedState(columns)
    val latestOnPrefetch by rememberUpdatedState(onPrefetch)
    CouiPrefetchEffect(listState = listState, enabled = enabled, restartKey = restartKey) { lastIndex, _, distance ->
        val current = latestFeed ?: return@CouiPrefetchEffect
        if (current.shouldPrefetchRow(lastIndex, latestRowStart, distance, latestColumns)) latestOnPrefetch()
    }
}
