package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.shouldLoadMore
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表加载更多三态：加载中 / 出错重试 / 没有更多了；与 HyperOS 加载反馈一致。
 *
 * 三个开关都在 [PagedFeed] 里，调用方只交整份状态，不再各自摆三个布尔。
 */
@Composable
fun LoadMoreFooter(
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            feed.loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            feed.loadMoreFailed -> MinTouchTarget(onClick = onLoadMore) {
                TextButton(text = "加载失败，点击重试", onClick = onLoadMore, minWidth = 0.dp)
            }
            !feed.hasMore -> Text(
                text = "没有更多了",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            else -> TextButton(text = "加载更多", onClick = onLoadMore)
        }
    }
}

/**
 * 触底加载下一页：列表只交出滚动信息，判定（还有没有下一页、在不在拉、距末尾几项、上次是否失败）
 * 全在 [PagedFeed.shouldLoadMore] 里。
 *
 * 只在还能自动加载时订阅滚动；一旦拿到下一页或到底，[PagedFeed.canLoadMore] 变为假，订阅随之解除。
 * 失败后会停在原地等用户点页脚重试，不会借「滚动信息还在末尾」反复重发。
 */
@Composable
fun LoadMoreOnScroll(
    listState: LazyListState,
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = feed.canLoadMore
    val latestFeed by rememberUpdatedState(feed)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.collect { info ->
            if (latestFeed.shouldLoadMore(info.visibleItemsInfo.lastOrNull()?.index ?: -1, info.totalItemsCount)) {
                latestOnLoadMore()
            }
        }
    }
}

/** 两列瀑布流的触底加载：取所有可见列里的最大下标，与列表共用同一判据。 */
@Composable
fun LoadMoreOnScroll(
    gridState: LazyStaggeredGridState,
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = feed.canLoadMore
    val latestFeed by rememberUpdatedState(feed)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo }.collect { info ->
            val lastIndex = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            if (latestFeed.shouldLoadMore(lastIndex, info.totalItemsCount)) latestOnLoadMore()
        }
    }
}

/**
 * 列表滚动条：贴右缘铺满可用高度，轨道按 [trackPadding] 让出顶栏与底部安全区。
 *
 * 只由 [TrackedLazyColumn] 在同一层 [Box] 内调用，轨道内边距与列表内边距共用同一份参数；
 * 调用方不再手工让两者对齐，也不必自己决定要不要滚动条。
 */
// ScrollBar 目前在 Miuix 标记为实验 API（warning 级别），仅此处显式表态使用
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun BoxScope.ListScrollBar(
    listState: LazyListState,
    trackPadding: PaddingValues,
) {
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        trackPadding = trackPadding,
    )
}

/**
 * 带滚动条的列表容器：把 [Box]、[LazyColumn] 与 [ListScrollBar] 收进一处。
 *
 * 滚动条落点与列表首尾留白都取自同一个 [contentPadding]，新增列表不会再漏挂滚动条，
 * 也不会因为两处 padding 各写一遍而让轨道错位。所有调用方都是纵向列表，滚动条一律常开；
 * 横向翻页走 HorizontalPager，不经过这里。
 */
@Composable
fun TrackedLazyColumn(
    state: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            content = content,
        )
        ListScrollBar(listState = state, trackPadding = contentPadding)
    }
}

/**
 * 通用点击分页条；页码与总页数由调用方给出，避免各页自己拼一套翻页控件。
 *
 * [totalPages] 为 null 表示接口不返回总数：这时只显示当前页，下一页能不能点按 [hasMore] 判。
 * [enabled] 为 false 时两键都禁用，供翻页请求在途期间使用（屏幕仍保留当前列表）。
 */
@Composable
fun PagerBar(
    page: Int,
    totalPages: Int? = null,
    hasMore: Boolean = false,
    enabled: Boolean = true,
    onPageChange: (Int) -> Unit,
) {
    val current = page.coerceAtLeast(1)
    val total = totalPages?.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val prevEnabled = enabled && current > 1
            val nextEnabled = enabled && (total?.let { current < it } ?: hasMore)
            MinTouchTarget(
                onClick = { onPageChange(current - 1) },
                enabled = prevEnabled,
            ) {
                TextButton(
                    text = "上一页",
                    onClick = { onPageChange(current - 1) },
                    enabled = prevEnabled,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
            }
            Text(
                text = total?.let { "$current / $it" } ?: "第 $current 页",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            MinTouchTarget(
                onClick = { onPageChange(current + 1) },
                enabled = nextEnabled,
            ) {
                TextButton(
                    text = "下一页",
                    onClick = { onPageChange(current + 1) },
                    enabled = nextEnabled,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
            }
        }
    }
}
