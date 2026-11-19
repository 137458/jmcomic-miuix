package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.showsPagedFooter
import com.jm3.core.model.SearchResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 榜单名次：前三名用主色强调，其余用次级文字色；固定宽度保证纵向对齐成列。 */
@Composable
private fun RankIndexBadge(rank: Int) {
    val highlighted = rank <= 3
    Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
        Text(
            text = rank.toString(),
            style = MiuixTheme.textStyles.title4,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

/**
 * 逐行结果卡：封面 + 标题 + 作者标签摘要，榜单模式在封面前补名次。
 *
 * 外观走 [WorkRowCard] 的统一契约；摘要只保留一行，超长日文标题限两行，
 * 否则整行会被撑到四行、高矮不一。
 */
@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    rank: Int? = null,
) {
    val authors = result.authors.joinToString(" / ").ifBlank { null }
    WorkRowCard(
        coverUrl = result.coverUrl,
        title = result.title,
        onClick = onClick,
        albumId = result.id,
        subtitle = authors,
        category = result.category,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 6.dp),
        leading = if (rank == null) {
            null
        } else {
            {
                Box(modifier = Modifier.fillMaxHeight().width(30.dp), contentAlignment = Alignment.Center) {
                    RankIndexBadge(rank = rank)
                }
            }
        },
    ) {
        WorkCardMetaRow(tags = result.tags, albumCode = result.id)
    }
}

/**
 * 搜索/浏览共用的结果列表与加载更多入口；是否还有下一页、在不在拉、上次是否失败都由 [PagedFeed] 回答。
 *
 * @param ranked 榜单模式：在封面前补名次序号（跨页累加，合并多页后依然连续）。
 * @param columns 大屏列数：由 [WideLayout.feedColumns] 给出，1 表示与手机一致的单列行卡。
 */
fun LazyListScope.searchResultItems(
    feed: PagedFeed<SearchResult>,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    ranked: Boolean = false,
    clickPaging: Boolean = false,
    onPageChange: ((Int) -> Unit)? = null,
    columns: Int = 1,
) {
    if (feed.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(title = "无结果", hint = "换个关键词再试试")
        }
    }
    // key 仍是作品 id（与单列时逐字一致），名次按切片前的顺序右移一位
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "",
        keyOf = { index -> feed.items[index].id },
    ) { index ->
        val result = feed.items[index]
        SearchResultCard(
            result = result,
            rank = if (ranked) index + 1 else null,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    // 单页结果（如每周必看）不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈
    if (feed.showsPagedFooter()) {
        if (clickPaging && onPageChange != null) {
            item(key = "pager") {
                PagerBar(
                    page = feed.page,
                    totalPages = feed.pageCount,
                    hasMore = feed.hasMore,
                    onPageChange = onPageChange,
                )
            }
        } else {
            item(key = "load-more") {
                LoadMoreFooter(feed = feed, onLoadMore = onLoadMore)
            }
        }
    }
}

fun LazyListScope.pagedSearchResultItems(
    feed: PagedFeed<SearchResult>,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    columns: Int = 1,
) {
    if (feed.items.isEmpty()) {
        item(key = "empty") { EmptyState(title = "无结果", hint = "换个关键词再试试") }
    }
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "",
        keyOf = { index -> feed.items[index].id },
    ) { index ->
        val result = feed.items[index]
        SearchResultCard(result = result, onClick = { onOpenAlbum(result.id) })
    }
    item(key = "pager") {
        PagerBar(
            page = feed.page,
            totalPages = feed.pageCount,
            hasMore = feed.hasMore,
            onPageChange = onPageChange,
        )
    }
}

/**
 * 一屏结果列表：按分页模式分发到「无限滚动」与「点击翻页」两条渲染路径。
 *
 * 各屏只交出当前这一页与两个动作，不必各自写一遍 clickPaging 的 when。
 */
fun LazyListScope.resultItems(
    feed: PagedFeed<SearchResult>,
    clickPaging: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    columns: Int = 1,
) {
    if (clickPaging) {
        pagedSearchResultItems(
            feed = feed,
            onPageChange = onPageChange,
            onOpenAlbum = onOpenAlbum,
            columns = columns,
        )
    } else {
        searchResultItems(
            feed = feed,
            onLoadMore = onLoadMore,
            onOpenAlbum = onOpenAlbum,
            columns = columns,
        )
    }
}

/**
 * 榜单瀑布流结果：两列封面卡，名次叠在封面左上角。
 *
 * 单页结果不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈。
 */
fun LazyStaggeredGridScope.rankResultGrid(
    feed: PagedFeed<SearchResult>,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    clickPaging: Boolean = false,
    onPageChange: ((Int) -> Unit)? = null,
) {
    if (feed.items.isEmpty()) {
        item(key = "rank-empty", span = StaggeredGridItemSpan.FullLine) {
            EmptyState(title = "无结果", hint = "换个筛选条件再试试")
        }
    }
    items(
        count = feed.items.size,
        key = { index -> feed.items[index].id },
        contentType = { "rank_card" },
    ) { index ->
        val result = feed.items[index]
        RankGridCard(
            result = result,
            rank = index + 1,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    // 单页结果（筛选后只有一页）不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈
    if (feed.showsPagedFooter()) {
        if (clickPaging && onPageChange != null) {
            item(key = "rank-pager", span = StaggeredGridItemSpan.FullLine) {
                PagerBar(
                    page = feed.page,
                    totalPages = feed.pageCount,
                    hasMore = feed.hasMore,
                    onPageChange = onPageChange,
                )
            }
        } else {
            item(key = "rank-load-more", span = StaggeredGridItemSpan.FullLine) {
                LoadMoreFooter(feed = feed, onLoadMore = onLoadMore)
            }
        }
    }
}

/** 榜单封面卡：封面 + 左上角名次角标 + 单行标题与作者，外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RankGridCard(result: SearchResult, rank: Int, onClick: () -> Unit) {
    WorkGridCard(
        title = result.title,
        coverUrl = result.coverUrl,
        onClick = onClick,
        albumId = result.id,
        coverContentDescription = result.title,
        subtitle = result.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = result.category,
        titleMaxLines = 1,
        modifier = Modifier.fillMaxWidth(),
        badge = {
            WorkCoverBadge(
                text = rank.toString(),
                highlighted = rank <= 3,
                modifier = Modifier.align(Alignment.TopStart).padding(WorkCardTokens.BadgeInset),
            )
            WorkAlbumCodeBadge(result.id)
        },
        footer = { WorkTagLine(result.tags) },
    )
}
